package rpg.core.progression;

/**
 * What B13 and B14 read to show progress, with nothing left to compute (FR-028).
 *
 * <p>{@code atMaxLevel} is a field rather than something a receiver derives from
 * {@code xpForNextLevel == 0}. FR-051 wants a character at the maximum reported as complete, not as
 * "0 % towards the next level" - and a derived rule would have every receiver reinvent that
 * distinction, each slightly differently.
 *
 * @param level current level
 * @param xpInLevel experience inside the current level
 * @param xpForNextLevel threshold of the next level, or 0 at the maximum
 * @param atMaxLevel whether the maximum level has been reached
 */
public record ProgressView(int level, long xpInLevel, long xpForNextLevel, boolean atMaxLevel) {

    /** Fraction of the current level completed, 1.0 at the maximum. For a progress bar in B13. */
    public double fraction() {
        if (atMaxLevel || xpForNextLevel <= 0L) {
            return 1.0;
        }
        double raw = (double) xpInLevel / (double) xpForNextLevel;
        return raw > 1.0 ? 1.0 : raw;
    }
}
