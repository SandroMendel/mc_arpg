package rpg.core.session;

/**
 * Why a session is ending.
 *
 * <p>The first three are factually the same event - the player is gone - and are handled by a
 * single trigger. Splitting them across separate listeners would mean a kick fires the unload twice
 * (FR-014). The distinction is kept only so the log says what happened.
 */
public enum SessionEndReason {
    /** The player left deliberately. */
    QUIT,
    /** The player was kicked. */
    KICK,
    /** The connection dropped. */
    TIMEOUT,
    /** The server is stopping (FR-010). */
    SHUTDOWN,
    /** The reconciliation found a session whose player is no longer connected. */
    RECONCILED
}
