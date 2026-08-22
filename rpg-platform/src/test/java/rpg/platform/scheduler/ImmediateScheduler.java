package rpg.platform.scheduler;

import java.time.Duration;

import rpg.core.scheduler.EntityRef;
import rpg.core.scheduler.Scheduler;
import rpg.core.scheduler.TaskHandle;
import rpg.core.scheduler.WorldPosition;

/**
 * Runs every task on the calling thread, immediately.
 *
 * <p>For tests that are about what a task does, not about when it runs. Deferring here would only
 * add a "now run the queue" line to every assertion without testing anything the real scheduler
 * does not already have its own tests for.
 */
public final class ImmediateScheduler implements Scheduler {

    @Override
    public TaskHandle runSyncAtLocation(WorldPosition position, Runnable task) {
        return run(task);
    }

    @Override
    public TaskHandle runSyncOnEntity(EntityRef entity, Runnable task) {
        return run(task);
    }

    /** ADR-024: verzoegert, aber im Test genauso behandelt wie sofort. */
    @Override
    public TaskHandle runSyncOnEntityDelayed(EntityRef entity, Duration delay, Runnable task) {
        return run(task);
    }

    @Override
    public TaskHandle runAsync(Runnable task) {
        return run(task);
    }

    @Override
    public TaskHandle runAsyncDelayed(Duration delay, Runnable task) {
        return run(task);
    }

    private TaskHandle run(Runnable task) {
        task.run();
        return new TaskHandle() {
            @Override
            public void cancel() {
                // Already finished; nothing to cancel.
            }

            @Override
            public boolean isCancelled() {
                return false;
            }
        };
    }
}
