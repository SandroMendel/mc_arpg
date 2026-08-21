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

    /**
     * When this attachment runs relative to the others: lower builds up earlier, and tears down later.
     *
     * <p>Exists because the module start order is the wrong order here. Modules start along their
     * dependencies, so B04 starts before B06 and B07 and therefore attaches first - but B04 is the one
     * that <em>calculates</em>, and B06 and B07 are what it calculates <em>from</em>. Running B04 first
     * means its first snapshot is computed without the level and without the class, and
     * {@code restoreResources} clamps the stored health against that snapshot: a level 60 warrior comes
     * back at the bare value from {@code stats.yml}. Since ADR-017 made the class the dominant source,
     * that is not a rounding error but most of the character.
     *
     * <p>So the rule is: <b>suppliers before the calculation.</b> A block that provides values to B04
     * leaves this at the default; B04 itself moves late. Teardown runs the other way round, so nothing
     * is calculated from state that was already released.
     *
     * <p>Ties keep their registration order, which is the module start order.
     */
    default int order() {
        return 0;
    }

    /** Called once per session, after the bundle is loaded and before the player is released. */
    void onSessionOpened(PlayerSession session, SessionBundle bundle);

    /**
     * Called when the session takes a character into play, on the player's own tick.
     *
     * <p><b>This, not {@link #onSessionOpened}, is where a character's state is built.</b> A session
     * opens without one and stays that way until the selection decides - on every join, for a character
     * that already exists just as for one created on the spot (ADR-020). So {@code onSessionOpened} has
     * no character to work with and returns early; this is the moment that does the work.
     *
     * <p>{@code bundle} is what the login read, handed over unchanged. A character that already existed
     * finds its stored resources, level and tiers in it; one created a moment ago finds nothing and
     * starts at the initial values. Neither case needs a query here, which matters because this runs on
     * the tick.
     *
     * <p>Default empty, because an attachment that keeps nothing per character has nothing to do. It
     * fires at most once per session - {@link PlayerSession#activate} only ever fills an empty slot,
     * and there is no counterpart for a character being replaced. Whatever this builds is released by
     * {@link #onSessionClosing} like everything else.
     */
    default void onCharacterActivated(
            PlayerSession session, PlayerCharacter character, SessionBundle bundle) {
        // nothing to do
    }

    /** Called once per session, before its final write. */
    void onSessionClosing(UUID playerId);
}
