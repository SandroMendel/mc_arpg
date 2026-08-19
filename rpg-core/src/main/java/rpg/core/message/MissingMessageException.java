package rpg.core.message;

/**
 * Thrown when a {@link MessageKey} has no configured text.
 *
 * <p>Unchecked on purpose: with {@link MessageKeyValidator} running at startup, reaching this at
 * runtime means a key was constructed dynamically and escaped validation - a programming error,
 * not an operating condition.
 */
public class MissingMessageException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    @SuppressWarnings("serial") // MessageKey is a record of a String and serializes fine
    private final MessageKey key;

    public MissingMessageException(MessageKey key) {
        super("no text configured for message key '" + key.value() + "'");
        this.key = key;
    }

    /** The key that could not be resolved. */
    public MessageKey key() {
        return key;
    }
}
