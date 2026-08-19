package rpg.core.persistence;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Holds the aggregates waiting to be written.
 *
 * <p>The one property everything else rests on: a mark is kept <strong>per aggregate</strong>, not
 * per change. Marking the same aggregate a thousand times leaves one entry, because the flush writes
 * the aggregate's current state anyway - the intermediate values were never going to be stored. Two
 * consequences follow, and both matter:
 *
 * <ul>
 *   <li>A game event costs one map write, no database access (FR-002, SC-005).
 *   <li>The buffer does <strong>not</strong> grow with the length of an outage, only with the number
 *       of <em>distinct</em> aggregates touched during it. A ten-hour outage with the same 200
 *       players holds the same ~200 player-state marks as a one-minute one. That is what makes the
 *       50 000 entry capacity a genuine emergency brake rather than something reached in normal
 *       operation - which is the right shape, since hitting it disconnects everyone (FR-009b).
 * </ul>
 *
 * <p>Thread-safe: {@code mark} is called from the tick, flushes run off it.
 */
public final class WriteBehindBuffer {

    private final Map<DirtyMark.Identity, DirtyMark> marks = new LinkedHashMap<>();
    private final int capacity;
    private final double warnThreshold;
    private final Clock clock;

    /** Warn once per crossing, not once per check - otherwise the log drowns exactly when it matters. */
    private final AtomicBoolean warned = new AtomicBoolean(false);

    public WriteBehindBuffer(int capacity, double warnThreshold, Clock clock) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be at least 1, but was " + capacity);
        }
        if (warnThreshold <= 0 || warnThreshold > 1) {
            throw new IllegalArgumentException(
                    "warnThreshold must be within (0, 1], but was " + warnThreshold);
        }
        this.capacity = capacity;
        this.warnThreshold = warnThreshold;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Buffer with the default warn threshold of 80% (FR-009c). */
    public WriteBehindBuffer(int capacity, Clock clock) {
        this(capacity, 0.8d, clock);
    }

    /**
     * Notes that an aggregate changed.
     *
     * <p>Cheap and allocation-light by design: this runs on the tick. Re-marking an already marked
     * aggregate keeps the <em>original</em> timestamp, so it stays measurable how long the oldest
     * unwritten change has been waiting.
     *
     * @return {@code true} if this created a new mark, {@code false} if it coalesced into an
     *     existing one
     */
    public synchronized boolean mark(AggregateType type, String aggregateId) {
        DirtyMark.Identity identity = new DirtyMark.Identity(type, aggregateId);
        if (marks.containsKey(identity)) {
            return false;
        }
        marks.put(identity, new DirtyMark(type, aggregateId, clock.instant()));
        return true;
    }

    /**
     * Takes a snapshot of what is currently pending, without removing anything.
     *
     * <p>Removal happens only in {@link #removeWritten}, after a write succeeded. Splitting the two
     * is what keeps a change made <em>during</em> a running flush from being lost: it creates a new
     * mark the snapshot does not contain, so the flush cannot clear it.
     */
    public synchronized List<DirtyMark> snapshot() {
        return List.copyOf(marks.values());
    }

    /** Pending marks of one aggregate type; used to batch per table. */
    public synchronized List<DirtyMark> snapshotOf(AggregateType type) {
        List<DirtyMark> result = new ArrayList<>();
        for (DirtyMark mark : marks.values()) {
            if (mark.aggregateType() == type) {
                result.add(mark);
            }
        }
        return List.copyOf(result);
    }

    /**
     * Removes exactly the marks that were written successfully.
     *
     * <p>Compares the full mark, not just the identity: if an aggregate was re-marked while the
     * flush ran, its mark carries a newer timestamp and therefore survives, and the change is picked
     * up next round.
     */
    public synchronized int removeWritten(Collection<DirtyMark> written) {
        int removed = 0;
        for (DirtyMark mark : written) {
            DirtyMark current = marks.get(mark.identity());
            if (current != null && current.equals(mark)) {
                marks.remove(mark.identity());
                removed++;
            }
        }
        if (!isWarning()) {
            warned.set(false); // dropped back below the threshold - allow warning again
        }
        return removed;
    }

    /** Current fill state (FR-009a to FR-009c). */
    public synchronized BufferStatus status() {
        int pending = marks.size();
        return new BufferStatus(pending, capacity, pending >= capacity, isWarningAt(pending));
    }

    /** Whether capacity is reached and players must be disconnected (FR-009b). */
    public synchronized boolean isOverCapacity() {
        return marks.size() >= capacity;
    }

    /**
     * Whether the warn threshold was crossed for the first time since dropping below it.
     *
     * <p>Returns {@code true} only once per crossing, so the caller can log without repeating the
     * warning on every cycle.
     */
    public synchronized boolean shouldWarnNow() {
        return isWarning() && warned.compareAndSet(false, true);
    }

    public synchronized int pending() {
        return marks.size();
    }

    public int capacity() {
        return capacity;
    }

    private boolean isWarning() {
        return isWarningAt(marks.size());
    }

    private boolean isWarningAt(int pending) {
        return pending >= (int) Math.ceil(capacity * warnThreshold) && pending < capacity;
    }
}
