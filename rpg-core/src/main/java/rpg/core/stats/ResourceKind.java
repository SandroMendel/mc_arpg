package rpg.core.stats;

/** Which resource a {@link ResourceChangedEvent} is about. */
public enum ResourceKind {

    /** Current health. Reaching zero is reported, never acted upon - that is B05. */
    HEALTH,

    /** Current mana. */
    MANA
}
