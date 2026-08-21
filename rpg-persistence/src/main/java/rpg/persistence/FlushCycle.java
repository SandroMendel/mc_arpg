package rpg.persistence;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.sql.DataSource;

import rpg.core.persistence.AggregateType;
import rpg.core.persistence.BufferStatus;
import rpg.core.persistence.DirtyMark;
import rpg.core.persistence.FlushReason;
import rpg.core.persistence.FlushResult;
import rpg.core.persistence.PersistenceConfig;
import rpg.core.persistence.WriteBehindBuffer;
import rpg.core.persistence.WriteBehindCoordinator;
import rpg.core.scheduler.Scheduler;
import rpg.persistence.jdbc.BatchWriter;

/**
 * Runs the write-behind flushes.
 *
 * <p>The interval cycle re-arms itself through {@link Scheduler#runAsyncDelayed} after each run
 * rather than using a repeating task (ADR-010). One consequence is worth knowing: a cycle that
 * failed to reschedule stops rather than piling runs up, which is why rescheduling happens in a
 * {@code finally} and is logged when it cannot happen.
 *
 * <p>Each flush takes a <em>snapshot</em> of the pending marks, writes them, and removes only what
 * it wrote. An aggregate changed while the flush was running therefore keeps a newer mark that this
 * flush does not know about and cannot clear - so the change is not lost, it lands in the next
 * round.
 */
public final class FlushCycle implements WriteBehindCoordinator {

    /**
     * The order aggregates are written in.
     *
     * <p>Not arbitrary any more since B03: the foreign keys demand it. A character references an
     * account, and an item references a character (ADR-011), so a newly created character must be
     * written before the items pointing at it - otherwise the item insert fails against a row that
     * does not exist yet. B04's resource row references a character for the same reason and follows
     * it. Statistics and the audit log carry no such constraint and come last.
     *
     * <p><b>Every new aggregate type has to be listed here.</b> Adding a value to
     * {@link AggregateType} is not enough: a type missing from this list has its marks counted as
     * failed on every flush and never written, which looks exactly like a database problem and is
     * none. B06 learned that the hard way - see {@code ProgressSessionEndFlushTest}.
     */
    private static final List<AggregateType> WRITE_ORDER =
            List.of(
                    AggregateType.PLAYER_STATE,
                    AggregateType.CHARACTER,
                    AggregateType.CHARACTER_STATS,
                    // B06's progress row references a character, so it follows CHARACTER for the
                    // same reason CHARACTER_STATS does.
                    AggregateType.CHARACTER_PROGRESS,
                    AggregateType.ITEM_INSTANCE,
                    AggregateType.STATISTICS,
                    AggregateType.AUDIT_LOG);

    private final WriteBehindBuffer buffer;
    private final OutageState outageState;
    private final DataSource writePool;
    private final PersistenceConfig config;
    private final Scheduler scheduler;
    private final Logger logger;
    private final Clock clock;
    private final Map<AggregateType, BatchWriter> writers = new EnumMap<>(AggregateType.class);

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean stopped = new AtomicBoolean(false);
    private final AtomicReference<CompletableFuture<FlushResult>> inFlight = new AtomicReference<>();

    public FlushCycle(
            WriteBehindBuffer buffer,
            OutageState outageState,
            DataSource writePool,
            PersistenceConfig config,
            Scheduler scheduler,
            Logger logger,
            Clock clock) {
        this.buffer = Objects.requireNonNull(buffer, "buffer");
        this.outageState = Objects.requireNonNull(outageState, "outageState");
        this.writePool = Objects.requireNonNull(writePool, "writePool");
        this.config = Objects.requireNonNull(config, "config");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Registers the writer responsible for one aggregate type. */
    public void register(AggregateType type, BatchWriter writer) {
        writers.put(type, writer);
    }

    @Override
    public void markDirty(AggregateType type, String aggregateId) {
        buffer.mark(type, aggregateId);
        if (buffer.shouldWarnNow()) {
            BufferStatus status = buffer.status();
            logger.warning(
                    "[persistence] write buffer at "
                            + Math.round(status.fillRatio() * 100)
                            + "% ("
                            + status.pending()
                            + "/"
                            + status.capacity()
                            + ") - if it fills, every player is disconnected to protect their"
                            + " progress");
        }
    }

    @Override
    public CompletableFuture<FlushResult> flushNow(FlushReason reason) {
        CompletableFuture<FlushResult> current = inFlight.get();
        if (current != null && !current.isDone()) {
            // Never two flushes at once: the second would snapshot the same marks and write them
            // twice.
            return current;
        }
        if (!running.compareAndSet(false, true)) {
            CompletableFuture<FlushResult> concurrent = inFlight.get();
            return concurrent != null ? concurrent : CompletableFuture.completedFuture(FlushResult.empty(reason));
        }

        CompletableFuture<FlushResult> future = new CompletableFuture<>();
        inFlight.set(future);
        scheduler.runAsync(
                () -> {
                    try {
                        future.complete(performFlush(reason));
                    } catch (RuntimeException unexpected) {
                        // Still not a failed future: the contract says flushNow never fails
                        // outwards, or the interval cycle would die with it.
                        logger.log(Level.SEVERE, "[persistence] flush failed unexpectedly", unexpected);
                        future.complete(new FlushResult(reason, 0, buffer.pending(), Duration.ZERO));
                    } finally {
                        running.set(false);
                    }
                });
        return future;
    }

    @Override
    public BufferStatus bufferStatus() {
        return buffer.status();
    }

    /** Starts the self-rescheduling interval cycle (FR-003). */
    public void startIntervalCycle() {
        scheduleNextInterval(config.autosave());
    }

    /** Stops rescheduling; in-flight work still completes. */
    public void stopIntervalCycle() {
        stopped.set(true);
    }

    /**
     * The final flush (FR-011).
     *
     * <p>Allowed to block - it runs off the tick during module shutdown - but bounded by the
     * configured budget, which is itself capped so it stays inside B01's 10 second per-module
     * allowance (FR-011a). On timeout the outstanding count is logged rather than swallowed.
     */
    public void shutdownFlush() {
        stopIntervalCycle();
        Duration budget = config.shutdownFlush();
        long startedAt = System.nanoTime();
        try {
            FlushResult result =
                    flushNow(FlushReason.SHUTDOWN).get(budget.toMillis(), TimeUnit.MILLISECONDS);
            logger.info(
                    "[persistence] shutdown flush wrote "
                            + result.written()
                            + " aggregate(s) in "
                            + Duration.ofNanos(System.nanoTime() - startedAt).toMillis()
                            + "ms"
                            + (result.complete() ? "" : ", " + result.failed() + " failed"));
        } catch (TimeoutException timeout) {
            // Loud, with the count and the affected aggregates - never a silent loss.
            List<DirtyMark> outstanding = buffer.snapshot();
            logger.severe(
                    "[persistence] shutdown flush exceeded its "
                            + budget.toSeconds()
                            + "s budget - "
                            + outstanding.size()
                            + " change(s) were NOT written: "
                            + describe(outstanding));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            logger.warning("[persistence] shutdown flush was interrupted");
        } catch (java.util.concurrent.ExecutionException failure) {
            logger.log(Level.SEVERE, "[persistence] shutdown flush failed", failure.getCause());
        }
    }

    // --- internals ---

    private void scheduleNextInterval(Duration delay) {
        if (stopped.get()) {
            return;
        }
        scheduler.runAsyncDelayed(
                delay,
                () -> {
                    Duration next = config.autosave();
                    try {
                        if (buffer.pending() > 0) {
                            FlushResult result = performFlush(currentReason());
                            if (!result.complete()) {
                                next = outageState.nextRetryDelay();
                            }
                        }
                    } catch (RuntimeException failure) {
                        logger.log(Level.SEVERE, "[persistence] interval flush failed", failure);
                        next = outageState.nextRetryDelay();
                    } finally {
                        // In a finally on purpose: a cycle that fails to re-arm simply stops, and
                        // silence would be indistinguishable from "nothing to write".
                        scheduleNextInterval(next);
                    }
                });
    }

    /** After an outage the next successful run is a recovery, not a routine interval (FR-010). */
    private FlushReason currentReason() {
        return outageState.isReachable() ? FlushReason.INTERVAL : FlushReason.RECOVERY;
    }

    private FlushResult performFlush(FlushReason reason) {
        long startedAt = System.nanoTime();
        int written = 0;
        int failed = 0;

        for (AggregateType type : WRITE_ORDER) {
            List<DirtyMark> snapshot = buffer.snapshotOf(type);
            if (snapshot.isEmpty()) {
                continue;
            }
            BatchWriter writer = writers.get(type);
            if (writer == null) {
                // No writer registered for this type yet - keep the marks rather than dropping them.
                failed += snapshot.size();
                continue;
            }
            try {
                List<DirtyMark> persisted = writer.write(writePool, snapshot);
                buffer.removeWritten(persisted);
                written += persisted.size();
                failed += snapshot.size() - persisted.size();
                outageState.recordSuccess();
            } catch (RuntimeException failure) {
                // Every mark of this batch stays; nothing is discarded (FR-009).
                failed += snapshot.size();
                outageState.recordFailure(failure);
            }
        }

        return new FlushResult(
                reason, written, failed, Duration.ofNanos(System.nanoTime() - startedAt));
    }

    private static String describe(List<DirtyMark> marks) {
        List<String> shown = new ArrayList<>();
        for (int i = 0; i < Math.min(marks.size(), 10); i++) {
            DirtyMark mark = marks.get(i);
            shown.add(mark.aggregateType() + ":" + mark.aggregateId());
        }
        if (marks.size() > shown.size()) {
            shown.add("... and " + (marks.size() - shown.size()) + " more");
        }
        return String.join(", ", shown);
    }
}
