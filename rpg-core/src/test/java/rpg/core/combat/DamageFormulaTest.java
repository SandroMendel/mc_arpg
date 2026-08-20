package rpg.core.combat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T015-T017, T020: the formula, with the worked examples from data-model.md (FR-001 to FR-006,
 * SC-002, SC-012).
 *
 * <p>Every expectation is exact. A tolerance would hide precisely the kind of drift this block must
 * not have - two players with identical gear must do identical damage, to the last digit.
 */
class DamageFormulaTest {

    @Test
    @DisplayName("50 physical against 100 defence is exactly 25")
    void fiftyAgainstHundred() {
        double raw = DamageFormula.rawDamage(50.0, 1.0);
        assertThat(raw).isEqualTo(50.0);
        assertThat(DamageFormula.afterDefence(raw, 100.0)).isEqualTo(25.0);
    }

    @Test
    @DisplayName("300 defence removes exactly 75 percent")
    void threeHundredDefence() {
        assertThat(DamageFormula.afterDefence(100.0, 300.0)).isEqualTo(25.0);
    }

    @Test
    @DisplayName("no defence changes nothing")
    void noDefence() {
        assertThat(DamageFormula.afterDefence(100.0, 0.0)).isEqualTo(100.0);
    }

    @Test
    @DisplayName("an ability at 180 percent of 40 magic against 100 defence is exactly 36")
    void abilityFactor() {
        double raw = DamageFormula.rawDamage(40.0, 1.8);
        assertThat(raw).isEqualTo(72.0);
        assertThat(DamageFormula.afterDefence(raw, 100.0)).isEqualTo(36.0);
    }

    @Test
    @DisplayName("a melee swing uses factor 1.0, so raw damage is the attribute itself")
    void meleeFactorIsOne() {
        assertThat(DamageFormula.rawDamage(37.5, 1.0)).isEqualTo(37.5);
    }

    @Test
    @DisplayName("the same input gives bit-identical output, a thousand times over")
    void deterministic() {
        double expected = DamageFormula.afterDefence(DamageFormula.rawDamage(63.7, 1.35), 137.0);
        for (int i = 0; i < 1000; i++) {
            assertThat(DamageFormula.afterDefence(DamageFormula.rawDamage(63.7, 1.35), 137.0))
                    .isEqualTo(expected);
        }
    }

    @Test
    @DisplayName("a negative attribute or factor is refused, not reinterpreted as healing")
    void negativeRefused() {
        assertThatThrownBy(() -> DamageFormula.rawDamage(-10.0, 1.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not negative");
        assertThatThrownBy(() -> DamageFormula.rawDamage(10.0, -1.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("factor");
    }

    @Test
    @DisplayName("non-finite input is refused at the door")
    void nonFiniteRefused() {
        assertThatThrownBy(() -> DamageFormula.rawDamage(Double.NaN, 1.0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DamageFormula.rawDamage(10.0, Double.POSITIVE_INFINITY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("isUsable is the gate the pipeline checks before applying anything")
    void usability() {
        assertThat(DamageFormula.isUsable(0.0)).isTrue();
        assertThat(DamageFormula.isUsable(12.5)).isTrue();
        assertThat(DamageFormula.isUsable(-1.0)).isFalse();
        assertThat(DamageFormula.isUsable(Double.NaN)).isFalse();
        assertThat(DamageFormula.isUsable(Double.POSITIVE_INFINITY)).isFalse();
    }

    @Test
    @DisplayName("zero attribute yields zero damage, without an exception")
    void zeroAttribute() {
        assertThat(DamageFormula.rawDamage(0.0, 1.8)).isEqualTo(0.0);
        assertThat(DamageFormula.afterDefence(0.0, 100.0)).isEqualTo(0.0);
    }
}
