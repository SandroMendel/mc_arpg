package rpg.core.module;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Whether the plugin has finished starting up.
 *
 * <p>Backs FR-013: a player must not get a session before the bootstrap completed successfully. The
 * platform's pre-join guard reads this from the async login thread while the bootstrap writes it from
 * the main thread, hence the atomic.
 */
public final class BootstrapState {

    /** The phases the bootstrap itself passes through. */
    public enum Phase {
        /** {@code onEnable} has not run yet. */
        NOT_STARTED,
        /** Modules are being started; joins must still be refused. */
        IN_PROGRESS,
        /** Every module reached {@link Module.LifecycleState#ACTIVE}; joins are allowed. */
        READY,
        /** A module failed or the configuration was rejected; joins stay refused. */
        FAILED,
        /** Shutdown has begun; joins are refused again. */
        SHUTTING_DOWN
    }

    private final AtomicReference<Phase> phase = new AtomicReference<>(Phase.NOT_STARTED);
    private final AtomicReference<String> failureReason = new AtomicReference<>();

    public Phase phase() {
        return phase.get();
    }

    /** Whether players may be given a session right now (FR-013). */
    public boolean acceptsPlayers() {
        return phase.get() == Phase.READY;
    }

    /** Why the bootstrap failed, if it did. */
    public String failureReason() {
        String reason = failureReason.get();
        return reason == null ? "" : reason;
    }

    public void markInProgress() {
        phase.set(Phase.IN_PROGRESS);
    }

    public void markReady() {
        phase.set(Phase.READY);
    }

    public void markFailed(String reason) {
        failureReason.set(reason);
        phase.set(Phase.FAILED);
    }

    /**
     * Records that shutdown has begun.
     *
     * <p>A failure is <strong>not</strong> overwritten. A bootstrap that failed is followed
     * immediately by a shutdown, so plainly setting the phase here would replace "the database was
     * unreachable" with "shutting down" every single time - and an operator looking at the state
     * would see the consequence instead of the cause. Players are refused in either phase, so
     * nothing is lost by keeping the more informative one.
     */
    public void markShuttingDown() {
        phase.compareAndSet(Phase.NOT_STARTED, Phase.SHUTTING_DOWN);
        phase.compareAndSet(Phase.IN_PROGRESS, Phase.SHUTTING_DOWN);
        phase.compareAndSet(Phase.READY, Phase.SHUTTING_DOWN);
    }
}
