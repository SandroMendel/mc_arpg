package rpg.core.progression;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * How much experience each level costs (FR-001 to FR-004).
 *
 * <p><b>A table, never a formula.</b> At sixty levels the table stays readable, every single level
 * can be retuned without shifting all the others, and there is exactly one source for each number.
 *
 * <p><b>Held as an array, not a map.</b> The curve is read on every gain that crosses a threshold.
 * An array is an indexed read; a {@code Map<Integer, Long>} would box an {@code Integer} per lookup
 * in the combat path. The map exists only while validating.
 *
 * <p>The maximum level is derived from the table (FR-004) - it is deliberately not a constant
 * anywhere in this codebase, so raising the ceiling is one more line of configuration.
 */
public final class XpCurve {

    /** Index 0 holds the threshold for level 2. */
    private final long[] thresholds;

    private XpCurve(long[] thresholds) {
        this.thresholds = thresholds;
    }

    /**
     * Builds and fully validates a curve, failing on the <b>first</b> violation and naming the level
     * responsible (FR-002, FR-003).
     *
     * <p>Three rules, and each one guards against a failure that would otherwise only show up in
     * play: a gap leaves a level unreachable, a non-positive value makes it free, and a
     * non-monotonic sequence pins a player in place forever.
     *
     * @param table level to required experience; keys start at 2
     * @throws IllegalArgumentException with a message naming the offending level
     */
    public static XpCurve of(Map<Integer, Long> table) {
        Objects.requireNonNull(table, "table");
        if (table.isEmpty()) {
            throw new IllegalArgumentException("progression.xp-curve: must define at least level 2");
        }
        TreeMap<Integer, Long> sorted = new TreeMap<>(table);
        int lowest = sorted.firstKey();
        int highest = sorted.lastKey();
        if (lowest != 2) {
            throw new IllegalArgumentException(
                    "progression.xp-curve: must define at least level 2, but the lowest level is "
                            + lowest);
        }
        long[] values = new long[highest - 1];
        long previous = 0L;
        for (int level = 2; level <= highest; level++) {
            Long value = sorted.get(level);
            if (value == null) {
                throw new IllegalArgumentException(
                        "progression.xp-curve: level " + level + " is missing");
            }
            if (value <= 0L) {
                throw new IllegalArgumentException(
                        "progression.xp-curve: level "
                                + level
                                + " must be positive, but was "
                                + value);
            }
            if (level > 2 && value <= previous) {
                throw new IllegalArgumentException(
                        "progression.xp-curve: level "
                                + level
                                + " must be greater than level "
                                + (level - 1)
                                + " ("
                                + previous
                                + "), but was "
                                + value);
            }
            values[level - 2] = value;
            previous = value;
        }
        return new XpCurve(values);
    }

    /** The highest level the table defines (FR-004). */
    public int maxLevel() {
        return thresholds.length + 1;
    }

    /**
     * Experience needed to reach {@code level} from the one below it.
     *
     * @return the threshold, or 0 for level 1 and for anything above the maximum - "no further cost"
     *     rather than an exception, so the level-up loop needs no special case at the ceiling
     */
    public long thresholdFor(int level) {
        if (level < 2 || level > maxLevel()) {
            return 0L;
        }
        return thresholds[level - 2];
    }

    /** Whether this level is the ceiling. */
    public boolean isMaxLevel(int level) {
        return level >= maxLevel();
    }
}
