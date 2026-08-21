package rpg.core.progression;

/**
 * Where a character stands: the level reached and the experience inside that level (FR-053a).
 *
 * <p><b>Not a running total.</b> Storing one total and deriving the level from the curve would make
 * the level a function of whatever curve is loaded right now - raise the curve later and every
 * existing character silently drops levels, losing zone access and abilities. FR-024 forbids
 * exactly that. Level plus remainder keeps a balancing change away from characters that already
 * exist, the same property ADR-004 buys for items by storing template and rolls instead of computed
 * values.
 *
 * <p>{@code xpInLevel} may exceed the threshold of the next level after the curve has been
 * <em>lowered</em>. That is not an invalid state but a pending level-up, resolved on load by the
 * same code that handles an ordinary gain.
 *
 * @param level 1 up to the maximum level from the curve
 * @param xpInLevel experience inside the current level, never negative
 */
public record ProgressState(int level, long xpInLevel) {

    /** Every character without a stored row starts here (FR-058). */
    public static final ProgressState INITIAL = new ProgressState(1, 0L);

    public ProgressState {
        if (level < 1) {
            throw new IllegalArgumentException("level must be at least 1, but was " + level);
        }
        if (xpInLevel < 0L) {
            throw new IllegalArgumentException(
                    "xpInLevel must not be negative, but was " + xpInLevel);
        }
    }

    public ProgressState withLevel(int newLevel) {
        return new ProgressState(newLevel, xpInLevel);
    }

    public ProgressState withXp(long newXp) {
        return new ProgressState(level, newXp);
    }
}
