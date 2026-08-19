package rpg.persistence.jdbc;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;

import rpg.core.persistence.PersistenceException;
import rpg.core.scheduler.Scheduler;

/**
 * Waits, off the tick, until a condition holds or a deadline passes.
 *
 * <p>Polls through {@link Scheduler#runAsyncDelayed} rather than blocking a thread: the wait can
 * last as long as a flush, and occupying a pool thread for it would eat exactly the capacity the
 * flush needs to finish.
 *
 * <p>On timeout the future fails, and the login is refused (FR-019c) rather than left hanging
 * forever.
 */
final class SessionHandoverSupport {

    private static final Duration POLL_INTERVAL = Duration.ofMillis(100);

    private SessionHandoverSupport() {}

    static CompletableFuture<Void> await(
            BooleanSupplier condition, Duration timeout, Scheduler scheduler, Clock clock) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        Instant deadline = clock.instant().plus(timeout);
        poll(condition, deadline, scheduler, clock, future);
        return future;
    }

    private static void poll(
            BooleanSupplier condition,
            Instant deadline,
            Scheduler scheduler,
            Clock clock,
            CompletableFuture<Void> future) {
        if (condition.getAsBoolean()) {
            future.complete(null);
            return;
        }
        if (!clock.instant().isBefore(deadline)) {
            future.completeExceptionally(
                    new PersistenceException(
                            "timed out waiting for the previous session's writes to finish -"
                                    + " refusing the login rather than serving stale state"));
            return;
        }
        scheduler.runAsyncDelayed(
                POLL_INTERVAL, () -> poll(condition, deadline, scheduler, clock, future));
    }
}
