package rpg.core.persistence;

/**
 * The kinds of aggregate this layer persists.
 *
 * <p>An enum rather than an open registry: the set is small, fixed by the data model, and every
 * value needs a matching table and batch writer. A block that needs a new aggregate adds a value
 * here together with its migration, which keeps the two from drifting apart.
 */
public enum AggregateType {
    /** Durable state of one player, keyed by their unique id. */
    PLAYER_STATE,
    /** One metric for one player on one calendar day (FR-016a). */
    STATISTICS,
    /** One concrete item instance owned by a player. */
    ITEM_INSTANCE,
    /** One administrative action; append-only. */
    AUDIT_LOG,
    /** One character of an account, bound to a class (B03). */
    CHARACTER
}
