package rpg.core.progression;

/**
 * Why a grant did nothing.
 *
 * <p>Returned rather than thrown: granting runs in the combat path, and an exception per rejected
 * amount would be an allocation plus a stack trace in the one path that promises to allocate
 * nothing (FR-062).
 */
public enum XpRejection {

    /** Nothing was rejected. */
    NONE,

    /** Zero, negative or not finite - never read as a deduction (FR-015). */
    INVALID_AMOUNT,

    /** The character's session is not ready; the share lapses silently (FR-014). */
    SESSION_NOT_READY,

    /** Already at the maximum level, so the amount is discarded (FR-049). */
    AT_MAX_LEVEL,

    /** No progress state is loaded for this character. */
    UNKNOWN_CHARACTER
}
