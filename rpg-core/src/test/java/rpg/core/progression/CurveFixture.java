package rpg.core.progression;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Curves for the tests: one valid, and one for each way a curve can be wrong.
 *
 * <p>Kept here rather than inline so the broken variants are named. A test that builds
 * {@code Map.of(2, 100L, 4, 120L)} inline tells a later reader nothing about which rule it is
 * probing.
 */
final class CurveFixture {

    private CurveFixture() {}

    /** Levels 2 to 10, moderate and strictly increasing. */
    static Map<Integer, Long> valid() {
        Map<Integer, Long> table = new LinkedHashMap<>();
        long value = 100L;
        for (int level = 2; level <= 10; level++) {
            table.put(level, value);
            value += 20L;
        }
        return table;
    }

    /** The two thresholds every arithmetic example in the spec uses: 100 then 120. */
    static Map<Integer, Long> twoLevels() {
        Map<Integer, Long> table = new LinkedHashMap<>();
        table.put(2, 100L);
        table.put(3, 120L);
        return table;
    }

    /** Levels 2 to 60, so a maximum-level test does not need sixty literals. */
    static Map<Integer, Long> upTo60() {
        Map<Integer, Long> table = new LinkedHashMap<>();
        long value = 100L;
        for (int level = 2; level <= 60; level++) {
            table.put(level, value);
            value += 10L;
        }
        return table;
    }

    /** Level 7 missing: a gap leaves that level unreachable. */
    static Map<Integer, Long> withGap() {
        Map<Integer, Long> table = valid();
        table.remove(7);
        return table;
    }

    /** Level 5 at zero: a level that costs nothing. */
    static Map<Integer, Long> withZero() {
        Map<Integer, Long> table = valid();
        table.put(5, 0L);
        return table;
    }

    /** Level 6 below level 5: the sequence stops rising, and a player stops rising with it. */
    static Map<Integer, Long> notMonotonic() {
        Map<Integer, Long> table = valid();
        table.put(6, table.get(5) - 10L);
        return table;
    }

    /** Starts at level 3, so level 2 is unreachable. */
    static Map<Integer, Long> withoutLevelTwo() {
        Map<Integer, Long> table = valid();
        table.remove(2);
        return table;
    }
}
