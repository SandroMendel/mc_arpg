package rpg.core.session;

import java.util.UUID;

/**
 * Thrown when a session is asked for values before it is ready (FR-004).
 *
 * <p>Deliberately an exception rather than a default value. Handing back defaults would let a
 * caller carry on with numbers that were never the player's - and the first write would then make
 * them permanent.
 */
public class SessionNotReadyException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    @SuppressWarnings("serial") // UUID is serializable
    private final UUID playerId;

    private final SessionState state;

    public SessionNotReadyException(UUID playerId, SessionState state) {
        super(
                "session for "
                        + playerId
                        + " is not ready (state "
                        + (state == null ? "<absent>" : state)
                        + ") - callers must wait rather than use defaults");
        this.playerId = playerId;
        this.state = state;
    }

    public UUID playerId() {
        return playerId;
    }

    /** The state the session was in, or {@code null} if there was none. */
    public SessionState state() {
        return state;
    }
}
