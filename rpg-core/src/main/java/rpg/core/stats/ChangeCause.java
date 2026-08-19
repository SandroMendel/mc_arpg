package rpg.core.stats;

/**
 * Why a resource value moved.
 *
 * <p>The distinction matters to B13: a value pulled down because a maximum shrank is not damage and
 * must not play a hurt animation. Without this, an equipment change would look like being hit.
 */
public enum ChangeCause {

    /** Somebody called changeHealth or changeMana. */
    DELTA,

    /** A falling maximum pulled the current value down with it (FR-026). */
    CLAMPED_BY_MAX,

    /** The holder was created or loaded (FR-027). */
    INITIALISED
}
