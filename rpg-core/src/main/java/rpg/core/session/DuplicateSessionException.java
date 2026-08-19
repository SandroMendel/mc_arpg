package rpg.core.session;

import java.util.UUID;

/**
 * Thrown when a second session is opened for a player who already has one (FR-014).
 *
 * <p>Rejected rather than replacing the existing one: overwriting would drop the first session
 * together with whatever progress it had not written yet.
 */
public class DuplicateSessionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    @SuppressWarnings("serial")
    private final UUID playerId;

    public DuplicateSessionException(UUID playerId) {
        super("a session already exists for " + playerId + " - refusing to open a second one");
        this.playerId = playerId;
    }

    public UUID playerId() {
        return playerId;
    }
}
