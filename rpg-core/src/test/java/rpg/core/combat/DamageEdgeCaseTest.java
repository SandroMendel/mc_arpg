package rpg.core.combat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Damage that is not a number, or is negative (FR-006).
 *
 * <p>The failure mode this guards against is specific and nasty: a negative value read as damage
 * <b>heals</b>, because applying it subtracts a negative. An ability with a sign error would turn
 * into an infinite heal, and nothing in the pipeline would look wrong. So every entry point refuses
 * such a value rather than passing it on.
 */
class DamageEdgeCaseTest {

    @Test
    @DisplayName("a negative base attribute is refused, not multiplied")
    void negativeBaseAttribute() {
        assertThatThrownBy(() -> DamageFormula.rawDamage(-10.0, 1.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be finite and not negative")
                .hasMessageContaining("-10.0");
    }

    @Test
    @DisplayName("a negative factor is refused - it would flip damage into healing")
    void negativeFactor() {
        assertThatThrownBy(() -> DamageFormula.rawDamage(50.0, -1.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("damage factor");
    }

    @Test
    @DisplayName("NaN and infinity are refused on both sides of the multiplication")
    void nonFiniteValues() {
        for (double broken : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertThatThrownBy(() -> DamageFormula.rawDamage(broken, 1.0))
                    .as("base attribute " + broken)
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> DamageFormula.rawDamage(50.0, broken))
                    .as("factor " + broken)
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    @DisplayName("zero is allowed on both sides - it is a real answer, not a fault")
    void zeroIsFine() {
        // A factor of zero is how an ability says "no direct damage"; a base attribute of zero is a
        // creature with no offence. Refusing either would make callers special-case them.
        assertThat(DamageFormula.rawDamage(0.0, 1.0)).isZero();
        assertThat(DamageFormula.rawDamage(50.0, 0.0)).isZero();
    }

    @Test
    @DisplayName("isUsable is the gate every source passes through")
    void isUsableRejectsTheSameValues() {
        assertThat(DamageFormula.isUsable(0.0)).isTrue();
        assertThat(DamageFormula.isUsable(12.5)).isTrue();
        assertThat(DamageFormula.isUsable(-0.1)).as("a heal in disguise").isFalse();
        assertThat(DamageFormula.isUsable(Double.NaN)).isFalse();
        assertThat(DamageFormula.isUsable(Double.POSITIVE_INFINITY)).isFalse();
        assertThat(DamageFormula.isUsable(Double.NEGATIVE_INFINITY)).isFalse();
    }

    @Test
    @DisplayName("the pipeline rejects an ability with a negative factor instead of healing")
    void pipelineRejectsNegativeAbilityFactor() {
        CombatFixture fixture = new CombatFixture();
        UUID attacker = fixture.player(50.0, 0.0, 4.0);
        UUID target = fixture.mob(100.0, 0.0, 5.0);
        double healthBefore = fixture.health(target);

        DamageResult result =
                fixture.pipeline.abilityDamage(attacker, target, DamageType.MAGIC, -2.0);

        assertThat(result.reason()).isEqualTo(RejectReason.INVALID_DAMAGE);
        assertThat(result.applied()).isFalse();
        assertThat(result.finalDamage()).isZero();
        assertThat(fixture.health(target))
                .as("the target must not be healed by a sign error")
                .isEqualTo(healthBefore);
    }

    @Test
    @DisplayName("the pipeline rejects a non-finite ability factor")
    void pipelineRejectsNonFiniteFactor() {
        CombatFixture fixture = new CombatFixture();
        UUID attacker = fixture.player(50.0, 0.0, 4.0);
        UUID target = fixture.mob(100.0, 0.0, 5.0);

        for (double broken : new double[] {Double.NaN, Double.POSITIVE_INFINITY}) {
            DamageResult result =
                    fixture.pipeline.abilityDamage(attacker, target, DamageType.MAGIC, broken);
            assertThat(result.reason()).as("factor " + broken).isEqualTo(RejectReason.INVALID_DAMAGE);
        }
        assertThat(fixture.health(target)).isEqualTo(100.0);
    }

    @Test
    @DisplayName("a projectile carrying a broken number is neutralised, not applied")
    void projectileWithBrokenNumber() {
        CombatFixture fixture = new CombatFixture();
        UUID shooter = fixture.player(50.0, 0.0, 4.0);
        UUID target = fixture.mob(100.0, 0.0, 5.0);

        assertThat(fixture.pipeline.projectileDamage(shooter, target, -5.0).reason())
                .isEqualTo(RejectReason.INVALID_DAMAGE);
        assertThat(fixture.pipeline.projectileDamage(shooter, target, Double.NaN).reason())
                .isEqualTo(RejectReason.INVALID_DAMAGE);

        assertThat(fixture.health(target)).isEqualTo(100.0);
    }

    @Test
    @DisplayName("fall damage from a broken height is zero rather than an exception")
    void fallDamageFromBrokenHeight() {
        FallDamageConfig config = new FallDamageConfig(3.0, 4.0, 200.0);

        // Different treatment on purpose: a fall height is measured by the server, not supplied by
        // an ability. A nonsense value there means "no fall", not "somebody made a mistake worth
        // stopping for".
        assertThat(DamageFormula.fallDamage(Double.NaN, config)).isZero();
        assertThat(DamageFormula.fallDamage(-10.0, config)).isZero();
        assertThat(DamageFormula.fallDamage(Double.POSITIVE_INFINITY, config)).isZero();
        assertThat(DamageFormula.fallDamage(2.0, config)).as("inside the safe height").isZero();
        assertThat(DamageFormula.fallDamage(10.0, config)).isEqualTo(28.0);
    }

    @Test
    @DisplayName("the fall damage ceiling holds, so a drop next to the void is not absurd")
    void fallDamageIsCapped() {
        FallDamageConfig config = new FallDamageConfig(3.0, 4.0, 200.0);

        assertThat(DamageFormula.fallDamage(10_000.0, config)).isEqualTo(200.0);
    }
}
