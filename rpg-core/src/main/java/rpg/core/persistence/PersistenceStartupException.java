package rpg.core.persistence;

/**
 * Thrown when persistence cannot be brought up: storage unreachable, or a migration failed
 * (FR-014).
 *
 * <p>Aborts the bootstrap through the B01 module contract. That is the intended outcome - a server
 * without working persistence must not accept players, because every session it granted would lose
 * its progress.
 *
 * <p>Messages built for this must never contain the password (FR-022).
 */
public class PersistenceStartupException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public PersistenceStartupException(String message) {
        super(message);
    }

    public PersistenceStartupException(String message, Throwable cause) {
        super(message, cause);
    }
}
