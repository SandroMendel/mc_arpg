package rpg.core.persistence;

/**
 * Thrown when a write carries a revision that no longer matches the stored one (FR-019b).
 *
 * <p>The realistic cause is not two simultaneous logins - Minecraft prevents those - but a ghost
 * session: a player loses connection, their unwritten changes sit in the buffer, they reconnect. If
 * the new session loaded the older stored state and the old session then flushed on top, progress
 * would be rolled back or items duplicated. Rejecting the stale write is the safety net behind the
 * handover wait.
 */
public class StaleVersionException extends PersistenceException {

    private static final long serialVersionUID = 1L;

    private final String aggregateId;
    private final long expectedRevision;
    private final long actualRevision;

    public StaleVersionException(String aggregateId, long expectedRevision, long actualRevision) {
        super(
                "refused a stale write for '"
                        + aggregateId
                        + "': it was based on revision "
                        + expectedRevision
                        + ", but the stored revision is "
                        + actualRevision);
        this.aggregateId = aggregateId;
        this.expectedRevision = expectedRevision;
        this.actualRevision = actualRevision;
    }

    public String aggregateId() {
        return aggregateId;
    }

    /** The revision the rejected write was based on. */
    public long expectedRevision() {
        return expectedRevision;
    }

    /** The revision actually stored. */
    public long actualRevision() {
        return actualRevision;
    }
}
