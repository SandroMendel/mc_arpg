package rpg.core.zone;

/**
 * The level range a region is meant for (FR-003).
 *
 * <p><b>Both bounds are inclusive.</b> A character on exactly {@code min} is inside the band and is
 * not warned; so is one on exactly {@code max}. The bands of the shipped regions cover level 1 up to
 * the maximum level with no gap and no overlap (FR-065d).
 *
 * <p><b>Only the lower bound gates anything.</b> Being above {@code max} is never warned about
 * (FR-023) - a level 60 character walking through the starting area is not doing anything wrong, and
 * a message telling them otherwise would be noise.
 *
 * @param min lowest level this region is meant for, inclusive
 * @param max highest level this region is meant for, inclusive
 */
public record LevelBand(int min, int max) {

    public LevelBand {
        if (min < 1) {
            throw new IllegalArgumentException("level band min must be >= 1, but was " + min);
        }
        if (max < min) {
            throw new IllegalArgumentException(
                    "level band max must be >= min, but was " + max + " < " + min);
        }
    }

    /** Whether this level is inside the band. Both bounds inclusive. */
    public boolean contains(int level) {
        return level >= min && level <= max;
    }

    /** Whether this level is below the band - the only case that warns (FR-021). */
    public boolean isBelow(int level) {
        return level < min;
    }
}
