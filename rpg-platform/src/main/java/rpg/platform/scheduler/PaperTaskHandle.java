package rpg.platform.scheduler;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import rpg.core.scheduler.TaskHandle;

/**
 * {@link TaskHandle} over a Paper {@link ScheduledTask}.
 *
 * <p>The handle is returned to the caller before Paper hands us its own task object, and a task can
 * in principle be cancelled in that window. Cancellation is therefore recorded in our own flag, which
 * the scheduled body checks before running, and additionally forwarded to Paper once its task is
 * known so Paper need not dispatch it at all.
 *
 * <p>Our flag - not Paper's task - is what makes {@code cancel()} authoritative. Forwarding is a
 * best-effort optimisation: if the platform refuses the cancellation, the task is still reliably
 * prevented from running, so the failure is logged rather than propagated to a caller whose contract
 * has in fact been honoured.
 */
final class PaperTaskHandle implements TaskHandle {

    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final AtomicReference<ScheduledTask> delegate = new AtomicReference<>();
    private final Logger logger;

    PaperTaskHandle(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void cancel() {
        if (!cancelled.compareAndSet(false, true)) {
            return; // repeated cancel() is a no-op
        }
        cancelDelegate(delegate.get());
    }

    @Override
    public boolean isCancelled() {
        return cancelled.get();
    }

    /** Called once Paper returned its task object. */
    void bind(ScheduledTask task) {
        delegate.set(task);
        if (cancelled.get()) {
            // cancelled between submission and binding - forward it now
            cancelDelegate(task);
        }
    }

    private void cancelDelegate(ScheduledTask task) {
        if (task == null) {
            return;
        }
        try {
            task.cancel();
        } catch (RuntimeException platformRefused) {
            // The task is already prevented from running by our own flag, so the caller's contract
            // holds either way; only the platform-side cleanup did not happen.
            logger.log(
                    Level.FINE,
                    "[scheduler] the platform rejected cancelling an already-cancelled task",
                    platformRefused);
        }
    }
}
