package rpg.core.persistence;

/**
 * A failure while reading from or writing to durable storage.
 *
 * <p>Unchecked because these failures are handled by the flush cycle and the outage state, not by
 * every individual call site. A caller that must react - the login path - inspects the failed
 * {@code CompletableFuture} instead.
 */
public class PersistenceException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public PersistenceException(String message) {
        super(message);
    }

    public PersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
