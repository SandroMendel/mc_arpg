package rpg.core.session;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Decides when a session comes into existence, becomes ready and disappears.
 *
 * <p>See {@code contracts/session-lifecycle.md}. The rule worth restating: this interface owns the
 * <em>lifecycle</em>, not the writing. Persisting is done by B02 - {@link #endSession} triggers
 * B02's existing immediate write rather than performing one of its own. A second write path in this
 * block would be the most expensive mistake available here, because it could break B02's guarantee
 * without any B03 test noticing.
 */
public interface SessionLifecycle {

    /**
     * Loads a player's state and opens a session for it.
     *
     * <p>Runs off the tick (FR-001), from the async pre-login event - before a player object
     * exists. That is what makes a refusal harmless: there is nothing yet that a failed load could
     * overwrite.
     *
     * @throws DuplicateSessionException if the player already has a session (FR-014)
     */
    CompletableFuture<PlayerSession> beginLoad(UUID playerId, Duration timeout);

    /** Marks a loaded session ready and releases the player (FR-003). */
    void markReady(UUID playerId);

    /**
     * Ends a session on any of the paths in {@link SessionEndReason}.
     *
     * <p>Triggers B02's immediate write and removes the session only once that write finished
     * (FR-007, FR-008).
     */
    CompletableFuture<Void> endSession(UUID playerId, SessionEndReason reason);

    /**
     * Discards a load in progress because the player disconnected first (FR-015).
     *
     * <p>Writes nothing: the player never received a state, so there is none to save.
     */
    void abandonLoad(UUID playerId);

    /**
     * Takes a character created during the session into play, and lets the attachments catch up.
     *
     * <p>The counterpart to {@link PlayerSession#activate} at the lifecycle level: the session gains
     * the character, then every {@link SessionAttachment#onCharacterActivated} runs, so the stat
     * holder, the progress and the equipment tiers exist before the player is let into the game state.
     *
     * <p>Not a second entry into the lifecycle. No load happens, nothing is read, nothing is written -
     * the character is new and has no stored state behind it. Runs on the caller's thread, which is the
     * player's own tick, because everything it triggers is in-memory.
     *
     * @return {@code false} if the session is gone or already had a character, in which case no
     *     attachment was called
     */
    boolean activateCharacter(UUID playerId, PlayerCharacter character);
}
