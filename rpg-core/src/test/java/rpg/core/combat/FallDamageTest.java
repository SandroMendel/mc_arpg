package rpg.core.combat;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T018, T019: fall damage as a fixed amount (FR-012a to FR-012c, SC-012a).
 *
 * <p>The last test is the design decision written down: a hazard should matter to a beginner and
 * become negligible to a geared player. A percentage of maximum health would stay equally dangerous
 * across the whole progression, which is the opposite of what was asked for.
 */
class FallDamageTest {

    private static final FallDamageConfig SHIPPED = FallDamageConfig.defaults();

    @Test
    @DisplayName("a fall from 10 blocks costs 28 with the shipped values")
    void tenBlocks() {
        assertThat(DamageFormula.fallDamage(10.0, SHIPPED)).isEqualTo(28.0);
    }

    @Test
    @DisplayName("below the safe height nothing happens")
    void safeHeight() {
        assertThat(DamageFormula.fallDamage(3.0, SHIPPED)).isEqualTo(0.0);
        assertThat(DamageFormula.fallDamage(1.0, SHIPPED)).isEqualTo(0.0);
        assertThat(DamageFormula.fallDamage(0.0, SHIPPED)).isEqualTo(0.0);
    }

    @Test
    @DisplayName("damage grows with the height")
    void growsWithHeight() {
        assertThat(DamageFormula.fallDamage(4.0, SHIPPED)).isEqualTo(4.0);
        assertThat(DamageFormula.fallDamage(5.0, SHIPPED)).isEqualTo(8.0);
        assertThat(DamageFormula.fallDamage(20.0, SHIPPED)).isEqualTo(68.0);
    }

    @Test
    @DisplayName("the ceiling stops a drop next to the void producing an absurd number")
    void ceiling() {
        assertThat(DamageFormula.fallDamage(10_000.0, SHIPPED)).isEqualTo(SHIPPED.maxDamage());
    }

    @Test
    @DisplayName("a negative or non-finite height is simply no damage")
    void nonsenseHeight() {
        assertThat(DamageFormula.fallDamage(-5.0, SHIPPED)).isEqualTo(0.0);
        assertThat(DamageFormula.fallDamage(Double.NaN, SHIPPED)).isEqualTo(0.0);
    }

    @Test
    @DisplayName("the same fall costs a beginner far more of their health than a geared player")
    void theDesignDecisionAsATest() {
        double damage = DamageFormula.fallDamage(10.0, SHIPPED);

        double beginnerShare = damage / 100.0;
        double gearedShare = damage / 2000.0;

        assertThat(damage).isEqualTo(28.0); // the same absolute amount for both
        assertThat(beginnerShare).isEqualTo(0.28);
        assertThat(gearedShare).isEqualTo(0.014);
        assertThat(beginnerShare / gearedShare).isEqualTo(20.0);
    }

    @Test
    @DisplayName("an implausible configuration is refused rather than producing odd falls")
    void configurationInvariants() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> new FallDamageConfig(-1.0, 4.0, 200.0))
                .hasMessageContaining("safe-blocks");
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> new FallDamageConfig(3.0, 0.0, 200.0))
                .hasMessageContaining("damage-per-block");
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> new FallDamageConfig(3.0, 4.0, 0.0))
                .hasMessageContaining("max-damage");
    }
}
