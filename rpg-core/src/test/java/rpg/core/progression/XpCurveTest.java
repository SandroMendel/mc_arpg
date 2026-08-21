package rpg.core.progression;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The curve and its three rules (FR-001 to FR-004, SC-003).
 *
 * <p>Each failing case asserts the <b>message</b>, not just that something was thrown. An operator
 * editing a sixty-line table needs to be told which line is wrong; "invalid curve" would send them
 * reading all sixty.
 */
class XpCurveTest {

    @Test
    @DisplayName("a valid curve loads and the maximum level comes from the table")
    void validCurveLoads() {
        XpCurve curve = XpCurve.of(CurveFixture.valid());

        assertThat(curve.maxLevel()).as("highest key in the table, not a constant").isEqualTo(10);
        assertThat(curve.thresholdFor(2)).isEqualTo(100L);
        assertThat(curve.thresholdFor(3)).isEqualTo(120L);
        assertThat(curve.thresholdFor(10)).isEqualTo(260L);
    }

    @Test
    @DisplayName("the maximum level follows the table, so a longer table raises the ceiling")
    void maxLevelFollowsTheTable() {
        assertThat(XpCurve.of(CurveFixture.twoLevels()).maxLevel()).isEqualTo(3);
        assertThat(XpCurve.of(CurveFixture.upTo60()).maxLevel()).isEqualTo(60);
    }

    @Test
    @DisplayName("level 1 and anything above the maximum cost nothing, rather than throwing")
    void thresholdOutsideTheTable() {
        XpCurve curve = XpCurve.of(CurveFixture.valid());

        // Zero rather than an exception: the level-up loop then needs no special case at either end.
        assertThat(curve.thresholdFor(1)).isZero();
        assertThat(curve.thresholdFor(11)).isZero();
        assertThat(curve.isMaxLevel(10)).isTrue();
        assertThat(curve.isMaxLevel(9)).isFalse();
    }

    @Test
    @DisplayName("a gap names the missing level")
    void gapIsRejected() {
        assertThatThrownBy(() -> XpCurve.of(CurveFixture.withGap()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("level 7 is missing");
    }

    @Test
    @DisplayName("a non-positive value names the level and the value")
    void zeroIsRejected() {
        assertThatThrownBy(() -> XpCurve.of(CurveFixture.withZero()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("level 5 must be positive")
                .hasMessageContaining("but was 0");
    }

    @Test
    @DisplayName("a sequence that stops rising names both levels and both values")
    void nonMonotonicIsRejected() {
        assertThatThrownBy(() -> XpCurve.of(CurveFixture.notMonotonic()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("level 6 must be greater than level 5");
    }

    @Test
    @DisplayName("a table that does not start at level 2 is rejected")
    void missingLevelTwoIsRejected() {
        assertThatThrownBy(() -> XpCurve.of(CurveFixture.withoutLevelTwo()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must define at least level 2");
    }

    @Test
    @DisplayName("an empty table is rejected")
    void emptyTableIsRejected() {
        assertThatThrownBy(() -> XpCurve.of(Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must define at least level 2");
    }

    @Test
    @DisplayName("validation reports the FIRST offending level, not the last")
    void reportsTheFirstViolation() {
        // Two faults at once: level 4 missing and level 8 not rising. The message must name 4 -
        // otherwise an operator fixes the second problem and hits the first on the next start.
        Map<Integer, Long> table = CurveFixture.valid();
        table.remove(4);
        table.put(8, 10L);

        assertThatThrownBy(() -> XpCurve.of(table))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("level 4 is missing");
    }
}
