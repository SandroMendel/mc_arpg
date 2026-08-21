package rpg.core.progression;

/**
 * Where an amount of experience came from (FR-007).
 *
 * <p>The source decides two things a caller must not decide for itself: whether the amount is split
 * across a party, and whether it may lower a character's progress. Passing those as booleans would
 * let any caller grant itself the right to reduce someone's level.
 */
public enum XpSource {

    /** A creature died and the damage split decides who gets what (FR-008). */
    MOB_KILL(true),

    /** A zone objective was completed (B09). Shares like a kill. */
    ZONE_OBJECTIVE(true),

    /**
     * An operator set the value. The <b>only</b> source allowed to lower progress (FR-024a), and
     * never shared - an operator addresses one character, not whoever happens to stand nearby.
     */
    ADMIN(false);

    private final boolean shared;

    XpSource(boolean shared) {
        this.shared = shared;
    }

    /** Whether an amount from this source is split across a party (FR-048). */
    public boolean isShared() {
        return shared;
    }

    /** Whether this source may reduce level or experience (FR-024, FR-024a). */
    public boolean mayLower() {
        return this == ADMIN;
    }
}
