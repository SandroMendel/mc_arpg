package rpg.core.session;

/**
 * Thrown when a session could not be loaded (FR-011).
 *
 * <p>The caller turns this into a refusal. It must never be turned into an empty profile: that
 * profile would be written over the player's real record at the next flush, and the loss would only
 * surface hours later.
 */
public class SessionLoadException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public SessionLoadException(String message) {
        super(message);
    }

    public SessionLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
