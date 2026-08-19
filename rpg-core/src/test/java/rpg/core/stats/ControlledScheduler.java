package rpg.core.stats;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;

import rpg.core.scheduler.EntityRef;
import rpg.core.scheduler.Scheduler;
import rpg.core.scheduler.TaskHandle;
import rpg.core.scheduler.WorldPosition;

/**
 * A scheduler that holds entity-bound tasks until a test says otherwise.
 *
 * <p>Running them immediately would make the bundling untestable: with instant execution every
 * change looks like it produced exactly one recalculation, including the case where it produced
 * six. Holding them models what a real tick does - the task runs later - and lets a test assert on
 * how many were scheduled at all, which is the property Principle II actually cares about.
 */
final class ControlledScheduler implements Scheduler {

    private final Deque<Runnable> pending = new ArrayDeque<>();
    private int scheduledCount;

    /** How many tasks were scheduled since this scheduler was created. */
    int scheduledCount() {
        return scheduledCount;
    }

    /** How many are waiting to run. */
    int pendingCount() {
        return pending.size();
    }

    /** Runs everything scheduled so far - the test's equivalent of the next tick. */
    void runPending() {
        while (!pending.isEmpty()) {
            pending.poll().run();
        }
    }

    void reset() {
        pending.clear();
        scheduledCount = 0;
    }

    @Override
    public TaskHandle runSyncAtLocation(WorldPosition position, Runnable task) {
        return enqueue(task);
    }

    @Override
    public TaskHandle runSyncOnEntity(EntityRef entity, Runnable task) {
        return enqueue(task);
    }

    @Override
    public TaskHandle runAsync(Runnable task) {
        return enqueue(task);
    }

    @Override
    public TaskHandle runAsyncDelayed(Duration delay, Runnable task) {
        return enqueue(task);
    }

    private TaskHandle enqueue(Runnable task) {
        scheduledCount++;
        pending.add(task);
        return new RecordedHandle();
    }

    private static final class RecordedHandle implements TaskHandle {
        private volatile boolean cancelled;

        @Override
        public void cancel() {
            cancelled = true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }
    }
}
