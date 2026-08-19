package rpg.core.stats;

/**
 * Where a set of contributions comes from (FR-006).
 *
 * <p>The declaration order is also the summation order. That is not decoration: floating point
 * addition is not associative, so two players with identical equipment would otherwise be able to
 * end up with values differing in the last digits depending on the order they equipped things.
 * Fixing the order here makes "same sources, same numbers" a property rather than a coincidence
 * (FR-016).
 */
public enum SourceKind {

    /** The character's class (B07). */
    CLASS,

    /** The character's level (B06). */
    LEVEL,

    /** A worn or held item (B11). ADR-004 makes this the dominant source. */
    EQUIPMENT,

    /** A temporary effect (B08). */
    BUFF,

    /** An effect radiating from another holder (B08). */
    AURA,

    /** An effect that applies while inside a zone (B09). */
    ZONE
}
