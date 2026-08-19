package rpg.persistence;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

/**
 * Tracks whether durable storage is reachable, and how long it has not been.
 *
 * <p>The transition back to reachable happens only after a write actually <em>succeeded</em>, never
 * after merely opening a connection. A pool that hands out a connection to a database which then
 * rejects the statement would otherwise flip the state back and let logins through again, only for
 * them to fail - the check has to mean what it claims.
 *
 * <p>Read from the login path off the tick while the flush cycle writes it, hence the atomics.
 */
public final class OutageState {

    /** Retry backoff bounds; the cycle asks for the next delay after each failure. */
    private static final Duration MIN_RETRY = Duration.ofSeconds(1);
    private static final Duration MAX_RETRY = Duration.ofSeconds(30);

    private final AtomicReference<Instant> unreachableSince = new AtomicReference<>();
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private final Clock clock;
    private final Logger logger;

    public OutageState(Clock clock, Logger logger) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /** Whether storage was reachable at the last write attempt. */
    public boolean isReachable() {
        return unreachableSince.get() == null;
    }

    /** How long the current outage has lasted, or zero when there is none. */
    public Duration outageDuration() {
        Instant since = unreachableSince.get();
        return since == null ? Duration.ZERO : Duration.between(since, clock.instant());
    }

    /** Records a failed write; starts an outage if none was running. */
    public void recordFailure(Throwable cause) {
        int failures = consecutiveFailures.incrementAndGet();
        if (unreachableSince.compareAndSet(null, clock.instant())) {
            logger.warning(
                    "[persistence] storage became unreachable - buffering changes in memory,"
                            + " already-connected players keep playing: "
                            + cause);
        } else if (failures % 10 == 0) {
            // Periodic reminder rather than one line per attempt, so a long outage does not drown
            // everything else in the log.
            logger.warning(
                    "[persistence] storage still unreachable after "
                            + outageDuration().toSeconds()
                            + "s ("
                            + failures
                            + " failed attempts)");
        }
    }

    /** Records a successful write; ends an outage if one was running. */
    public void recordSuccess() {
        Instant since = unreachableSince.getAndSet(null);
        consecutiveFailures.set(0);
        if (since != null) {
            logger.info(
                    "[persistence] storage reachable again after "
                            + Duration.between(since, clock.instant()).toSeconds()
                            + "s - buffered changes were written");
        }
    }

    /** Delay before the next retry, growing with consecutive failures and capped. */
    public Duration nextRetryDelay() {
        int failures = Math.min(consecutiveFailures.get(), 8); // cap the exponent, not just the result
        Duration delay = MIN_RETRY.multipliedBy(1L << Math.max(0, failures - 1));
        return delay.compareTo(MAX_RETRY) > 0 ? MAX_RETRY : delay;
    }

    /** Consecutive failed write attempts; zero while healthy. */
    public int consecutiveFailures() {
        return consecutiveFailures.get();
    }
}
