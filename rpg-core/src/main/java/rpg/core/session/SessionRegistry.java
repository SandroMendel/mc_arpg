package rpg.core.session;

import java.util.Optional;
import java.util.UUID;

/**
 * How B04 to B12 reach the state of a connected player.
 *
 * <p>See {@code contracts/session-registry.md}. Read-only on purpose: there is no method that
 * creates, changes or removes a session. The lifecycle belongs to B03, and a block that could open
 * a session could open a second one - which is exactly what FR-014 forbids.
 *
 * <p>No Bukkit reference; usable and testable without a running server.
 */
public interface SessionRegistry {

    /**
     * The session of a player, if there is one and it is ready.
     *
     * <p>Empty for a player who is not connected <em>and</em> for one whose session is still
     * loading. Both mean "you cannot have values yet" - and neither means "here are some defaults".
     */
    Optional<PlayerSession> find(UUID playerId);

    /**
     * The session of a player.
     *
     * @throws SessionNotReadyException if there is none or it is not ready. For calling blocks this
     *     is an ordinary condition, not a catastrophe - they either handle it or check
     *     {@link #isReady} first.
     */
    PlayerSession require(UUID playerId);

    /** Whether this player's values may be queried right now. */
    boolean isReady(UUID playerId);

    /** How many sessions are currently held; the number SC-008 measures. */
    int activeSessionCount();
}
