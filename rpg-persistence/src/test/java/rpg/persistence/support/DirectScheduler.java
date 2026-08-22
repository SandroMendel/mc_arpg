package rpg.persistence.support;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import rpg.core.scheduler.EntityRef;
import rpg.core.scheduler.Scheduler;
import rpg.core.scheduler.TaskHandle;
import rpg.core.scheduler.WorldPosition;

/**
 * Scheduler that runs work on the calling thread.
 *
 * <p>Makes the integration tests deterministic: a flush has finished when the call returns, so no
 * test needs to sleep or poll. The real concurrency behaviour is covered where it belongs - in the
 * MockBukkit tests of the Paper adapter.
 *
 * <p>Delayed tasks run immediately. Tests that care about delay assert on the recorded delays
 * instead of waiting for them.
 */
public final class DirectScheduler implements Scheduler {

    private final AtomicInteger asyncRuns = new AtomicInteger();
    private final AtomicInteger delayedRuns = new AtomicInteger();

    @Override
    public TaskHandle runSyncAtLocation(WorldPosition position, Runnable task) {
        throw new UnsupportedOperationException("persistence never schedules tick work");
    }

    @Override
    public TaskHandle runSyncOnEntity(EntityRef entity, Runnable task) {
        throw new UnsupportedOperationException("persistence never schedules tick work");
    }

    /** ADR-024: verzoegert, aber im Test genauso behandelt wie sofort. */
    @Override
    public TaskHandle runSyncOnEntityDelayed(EntityRef entity, Duration delay, Runnable task) {
        throw new UnsupportedOperationException("persistence never schedules tick work");
    }

    @Override
    public TaskHandle runAsync(Runnable task) {
        asyncRuns.incrementAndGet();
        task.run();
        return new CompletedHandle();
    }

    @Override
    public TaskHandle runAsyncDelayed(Duration delay, Runnable task) {
        delayedRuns.incrementAndGet();
        // Not run: a self-rescheduling cycle would otherwise recurse without end here. Tests that
        // want another round call flushNow themselves.
        return new CompletedHandle();
    }

    public int asyncRuns() {
        return asyncRuns.get();
    }

    public int delayedRuns() {
        return delayedRuns.get();
    }

    private static final class CompletedHandle implements TaskHandle {
        private final AtomicBoolean cancelled = new AtomicBoolean(false);

        @Override
        public void cancel() {
            cancelled.set(true);
        }

        @Override
        public boolean isCancelled() {
            return cancelled.get();
        }
    }
}
