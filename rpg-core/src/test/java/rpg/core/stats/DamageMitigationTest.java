package rpg.core.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** T014: the divisor model from ADR-008 (FR-015, SC-006). */
class DamageMitigationTest {

    @Test
    @DisplayName("300 defense is exactly 75 percent mitigation")
    void threeHundredIsSeventyFivePercent() {
        assertThat(DamageMitigation.reductionFactor(300.0)).isEqualTo(0.75);
        assertThat(DamageMitigation.afterDefense(100.0, 300.0)).isEqualTo(25.0);
    }

    @Test
    @DisplayName("zero defense changes nothing")
    void zeroDefenceIsNeutral() {
        assertThat(DamageMitigation.reductionFactor(0.0)).isEqualTo(0.0);
        assertThat(DamageMitigation.afterDefense(37.5, 0.0)).isEqualTo(37.5);
    }

    @Test
    @DisplayName("100 defense halves the damage")
    void hundredHalves() {
        assertThat(DamageMitigation.afterDefense(100.0, 100.0)).isEqualTo(50.0);
    }

    @Test
    @DisplayName("mitigation approaches but never reaches complete immunity")
    void asymptotic() {
        assertThat(DamageMitigation.reductionFactor(900.0)).isEqualTo(0.90);
        assertThat(DamageMitigation.reductionFactor(1_000_000.0)).isLessThan(1.0).isGreaterThan(0.9999);
        assertThat(DamageMitigation.afterDefense(1000.0, 1_000_000.0)).isPositive();
    }

    @Test
    @DisplayName("negative defense amplifies, stays finite and never flips the sign")
    void negativeDefenceAmplifies() {
        assertThat(DamageMitigation.afterDefense(10.0, -50.0)).isEqualTo(20.0);

        double extreme = DamageMitigation.afterDefense(10.0, -1000.0);
        assertThat(extreme).isFinite().isPositive();
        // Clamped at the divisor floor: 100x amplification, not a sign flip and not a division by
        // zero.
        assertThat(extreme).isEqualTo(1000.0);
    }

    @Test
    @DisplayName("defense of exactly -100 does not divide by zero")
    void minusHundredIsSafe() {
        assertThat(DamageMitigation.afterDefense(5.0, -100.0)).isFinite().isEqualTo(500.0);
    }

    @Test
    @DisplayName("zero damage stays zero regardless of defense")
    void zeroDamage() {
        assertThat(DamageMitigation.afterDefense(0.0, 300.0)).isEqualTo(0.0);
        assertThat(DamageMitigation.afterDefense(0.0, -300.0)).isEqualTo(0.0);
    }

    @Test
    @DisplayName("NaN defense is refused rather than silently producing NaN damage")
    void nanRefused() {
        assertThatThrownBy(() -> DamageMitigation.afterDefense(10.0, Double.NaN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NaN");
    }
}
