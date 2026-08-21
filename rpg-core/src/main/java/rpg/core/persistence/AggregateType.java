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
    CHARACTER,
    /**
     * The current health and mana of one character (B04).
     *
     * <p>A table of its own rather than two columns on {@code character}: sharing a row would mean
     * sharing a writer and a revision counter between B03 and B04, so every change to B04's values
     * would be a change to B03's write path.
     */
    CHARACTER_STATS,
    /**
     * The level and the experience inside that level of one character (B06).
     *
     * <p>Own table for the same reason as {@link #CHARACTER_STATS}: one owner, one writer, one
     * position in the flush order. Additive, so no existing contract changes.
     */
    CHARACTER_PROGRESS
}
