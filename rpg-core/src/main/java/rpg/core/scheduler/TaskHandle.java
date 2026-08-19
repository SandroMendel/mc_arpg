package rpg.core.scheduler;

/** Handle for a submitted task, allowing it to be cancelled before it runs. */
public interface TaskHandle {

    /**
     * Prevents a not-yet-executed task from running.
     *
     * <p>Repeated calls are a no-op. Cancelling a task that has already started has no effect on the
     * running execution.
     */
    void cancel();

    /** Whether {@link #cancel()} has been called. */
    boolean isCancelled();
}
