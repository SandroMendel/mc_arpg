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
    CHARACTER_PROGRESS,
    /**
     * The reached armour and weapon tier of one character (B07).
     *
     * <p>Own table for the same reason as the two above. The class itself is <b>not</b> stored here -
     * it already lives in {@code rpg.character} from B03, and a second copy would be a second truth.
     *
     * <p>Registration 1 of 3 (ADR-015): adding this value is not enough. It also has to appear in
     * {@code FlushCycle.WRITE_ORDER} after {@code CHARACTER}, and a repository has to be wired for
     * it. A type missing from the write order has its marks counted as failed on every flush and is
     * never written - which looks like a database problem and is none.
     */
    CHARACTER_CLASS_PROGRESS,

    /**
     * The stored contents of one character's inventory.
     *
     * <p>Groundwork for B11, brought forward because B07 made it necessary. The selection lets a player
     * switch between their characters, and the Minecraft inventory belongs to the <em>player</em>, not
     * to any one of them. With nowhere to keep it, the only consistent behaviour was to empty it on
     * every entry - which threw away whatever had been farmed.
     *
     * <p>Distinct from {@link #ITEM_INSTANCE}, which is B11's model for RPG items with a template and
     * rolled values. This is the raw contents, vanilla loot included, and B11 may well replace it.
     *
     * <p>Registration 1 of 3 (ADR-015), as above.
     */
    CHARACTER_INVENTORY,

    /**
     * What a character owns per ability: its rank, its running cooldown and its toggle (B08).
     *
     * <p><b>Many rows per character, unlike every type above.</b> The flush therefore writes a set and
     * deletes what fell back to the default - rank 1, no cooldown, no toggle. Without that the table
     * would keep a row for every ability a player ever used, all of them empty afterwards.
     *
     * <p>What is deliberately <em>not</em> here: the unlock state, which follows from the level
     * (FR-061), and rage, charges and any running ability, which are runtime and computed from a
     * timestamp plus elapsed time (ADR-025).
     *
     * <p>Registration 1 of 3 (ADR-015), as above.
     */
    CHARACTER_ABILITIES
}
