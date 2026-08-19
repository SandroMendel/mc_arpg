package rpg.core.session;

import java.util.UUID;

/**
 * How a later block hangs its own per-session state off the session lifecycle.
 *
 * <p>Introduced for B04, which needs a calculated stat holder to exist <em>before</em> a player is
 * released (FR-019b). The only moment early enough is inside the load itself, which runs in the
 * async pre-login event before a player object exists. Reacting afterwards - to a "session ready"
 * event - would put someone into the world with the wrong values for at least a tick.
 *
 * <p>An interface rather than a direct call into B04 keeps the dependency pointing the right way:
 * B03 knows that something may want to attach state, and nothing about what. B06, B07 and B11 will
 * use the same seam.
 *
 * <h2>Contract</h2>
 *
 * <ul>
 *   <li>{@link #onSessionOpened} runs on the load thread, after the bundle is read and migrated and
 *       the session is registered, but before it is marked ready. Blocking here delays the login,
 *       so do only what has to be done before release.
 *   <li>{@link #onSessionClosing} runs before the session's final write, so an attachment can hand
 *       over anything that still has to be persisted.
 *   <li>An exception from either is caught, logged and confined to that session. A broken
 *       attachment must not cost a player their login or their save.
 * </ul>
 */
public interface SessionAttachment {

    /** Stable identifier, used in log messages when this attachment misbehaves. */
    String id();

    /** Called once per session, after the bundle is loaded and before the player is released. */
    void onSessionOpened(PlayerSession session, SessionBundle bundle);

    /** Called once per session, before its final write. */
    void onSessionClosing(UUID playerId);
}
