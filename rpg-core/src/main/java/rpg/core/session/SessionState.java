package rpg.core.session;

import java.util.EnumSet;
import java.util.Set;

/**
 * Where a session stands in its lifecycle.
 *
 * <p>The transitions are enumerated here rather than left to whoever writes the next listener,
 * because two of them carry the guarantee this whole block exists for:
 *
 * <ul>
 *   <li>{@link #FAILED} has <strong>no</strong> path that writes. A login that could not be loaded
 *       must leave the stored record untouched (FR-012) - a write from here would overwrite real
 *       progress with nothing.
 *   <li>{@link #LOADING} to {@link #UNLOADING} - the player disconnected mid-load - likewise writes
 *       nothing (FR-015). There is no state the player ever received, so there is nothing to save.
 * </ul>
 */
public enum SessionState {

    /** Being read. If the player is already in the world, they are frozen and invulnerable. */
    LOADING,

    /** Fully loaded. The player is released and other blocks may query their values. */
    READY,

    /** Session end in progress; the final write has been started. */
    UNLOADING,

    /** Loading failed. The player is refused and nothing is written. */
    FAILED;

    /** Whether this state may transition into {@code target}. */
    public boolean canTransitionTo(SessionState target) {
        return allowedTargets().contains(target);
    }

    /** The states reachable from this one. */
    public Set<SessionState> allowedTargets() {
        return switch (this) {
            case LOADING -> EnumSet.of(READY, FAILED, UNLOADING);
            case READY -> EnumSet.of(UNLOADING);
            // Terminal: both are removed from the registry, neither leads anywhere.
            case UNLOADING, FAILED -> EnumSet.noneOf(SessionState.class);
        };
    }

    /**
     * Whether a session in this state may still be written to storage.
     *
     * <p>The single most important predicate in this block. {@code FAILED} means the load never
     * produced a usable state, so writing would replace real progress with nothing.
     */
    public boolean mayBeWritten() {
        return this == READY || this == UNLOADING;
    }

    /** Whether other blocks may query this session's values (FR-004). */
    public boolean isQueryable() {
        return this == READY;
    }

    /** Whether the session is finished and should be removed from the registry. */
    public boolean isTerminal() {
        return this == FAILED || this == UNLOADING;
    }
}
