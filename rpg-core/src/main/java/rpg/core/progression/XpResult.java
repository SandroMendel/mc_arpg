package rpg.core.progression;

import java.util.Optional;

/**
 * Outcome of a grant.
 *
 * <p>A return value, not an exception. Granting runs in the combat path, and an exception per
 * rejected amount would be an allocation plus a stack trace in the one path that promises to
 * allocate nothing (FR-062).
 *
 * @param granted experience actually credited
 * @param discarded experience thrown away at the maximum level (FR-049)
 * @param levelUp the rise, or {@code null} when nothing crossed a threshold
 * @param rejection why nothing happened, or {@link XpRejection#NONE}
 */
public record XpResult(long granted, long discarded, LevelUp levelUp, XpRejection rejection) {

    private static final XpResult NOTHING = new XpResult(0L, 0L, null, XpRejection.NONE);

    public XpResult {
        if (rejection == null) {
            throw new IllegalArgumentException("rejection must not be null; use XpRejection.NONE");
        }
    }

    /** Credited without crossing a threshold. */
    public static XpResult granted(long amount) {
        return new XpResult(amount, 0L, null, XpRejection.NONE);
    }

    /** Credited and crossed at least one threshold. */
    public static XpResult leveled(long amount, LevelUp levelUp) {
        return new XpResult(amount, levelUp.discarded(), levelUp, XpRejection.NONE);
    }

    /** Nothing happened, and here is why. */
    public static XpResult rejected(XpRejection reason) {
        return new XpResult(0L, 0L, null, reason);
    }

    /** Silently discarded at the maximum level - a regular case, not a failure (FR-050). */
    public static XpResult discarded(long amount) {
        return new XpResult(0L, amount, null, XpRejection.AT_MAX_LEVEL);
    }

    /** Neither credited nor rejected - an empty damage split, for instance. */
    public static XpResult nothing() {
        return NOTHING;
    }

    public boolean rejected() {
        return rejection != XpRejection.NONE;
    }

    public boolean leveledUp() {
        return levelUp != null;
    }

    public Optional<LevelUp> levelUpIfAny() {
        return Optional.ofNullable(levelUp);
    }
}
