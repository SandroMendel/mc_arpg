package rpg.core.progression;

/**
 * The result of a gain that crossed at least one threshold (FR-017 to FR-019).
 *
 * <p>One instance per rise, not per level crossed. A gain that carries a character from 12 to 15 is
 * one event with {@code previousLevel = 12} and {@code newLevel = 15}; three events would make
 * three unlock passes out of what a player experienced as one moment (FR-023).
 *
 * @param previousLevel level before the gain
 * @param newLevel level after it
 * @param xpInLevel remainder inside the new level (FR-019)
 * @param discarded experience thrown away because the maximum was reached (FR-049); otherwise 0
 */
public record LevelUp(int previousLevel, int newLevel, long xpInLevel, long discarded) {

    public LevelUp {
        if (newLevel < previousLevel) {
            throw new IllegalArgumentException(
                    "a level-up cannot go down: " + previousLevel + " -> " + newLevel);
        }
        if (xpInLevel < 0L || discarded < 0L) {
            throw new IllegalArgumentException("xpInLevel and discarded must not be negative");
        }
    }

    /** How many levels were crossed. */
    public int levelsGained() {
        return newLevel - previousLevel;
    }
}
