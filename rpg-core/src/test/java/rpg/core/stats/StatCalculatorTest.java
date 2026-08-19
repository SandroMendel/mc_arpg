package rpg.core.stats;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T011-T013: the formula itself (FR-011 to FR-014, SC-007).
 *
 * <p>Every expectation here is an exact equality, not a tolerance. A tolerance would let through
 * precisely the drift this block is designed not to have.
 */
class StatCalculatorTest {

    private static final StatConfig CONFIG = StatConfig.defaults();

    private static StatSnapshot compute(ModifierSet... sets) {
        return StatCalculator.compute(CONFIG, List.of(sets), null, 1L);
    }

    private static ModifierSet set(String key, StatModifier... modifiers) {
        return ModifierSet.of(SourceId.of(SourceKind.EQUIPMENT, key), modifiers);
    }

    @Test
    @DisplayName("without any contribution every attribute equals its configured base")
    void baseOnly() {
        StatSnapshot snapshot = compute();
        for (Attribute attribute : Attribute.all()) {
            assertThat(snapshot.get(attribute))
                    .as(attribute.key())
                    .isEqualTo(CONFIG.definition(attribute).base());
        }
    }

    @Test
    @DisplayName("base 100 with +50 flat and +20 percent is exactly 180")
    void flatThenPercent() {
        StatSnapshot snapshot =
                compute(
                        set(
                                "chest",
                                StatModifier.flat(Attribute.HEALTH, 50.0),
                                StatModifier.percent(Attribute.HEALTH, 0.20)));
        assertThat(snapshot.get(Attribute.HEALTH)).isEqualTo(180.0);
    }

    @Test
    @DisplayName("two +50 percent contributions sum to x2.0, not x2.25")
    void percentagesAreSummedNotChained() {
        StatSnapshot snapshot =
                compute(
                        set("a", StatModifier.percent(Attribute.PHYSICAL_DAMAGE, 0.50)),
                        set("b", StatModifier.percent(Attribute.PHYSICAL_DAMAGE, 0.50)));
        double base = CONFIG.definition(Attribute.PHYSICAL_DAMAGE).base();
        assertThat(snapshot.get(Attribute.PHYSICAL_DAMAGE)).isEqualTo(base * 2.0);
        assertThat(snapshot.get(Attribute.PHYSICAL_DAMAGE)).isNotEqualTo(base * 2.25);
    }

    @Test
    @DisplayName("exceeding the ceiling yields the ceiling, not the raw value")
    void ceilingApplies() {
        StatSnapshot snapshot = compute(set("absurd", StatModifier.flat(Attribute.HEALTH, 99_999.0)));
        assertThat(snapshot.get(Attribute.HEALTH)).isEqualTo(2000.0);
    }

    @Test
    @DisplayName("a percentage sum below -100% yields the floor, never a negative value")
    void floorApplies() {
        StatSnapshot snapshot =
                compute(
                        set("curse-a", StatModifier.percent(Attribute.HEALTH, -0.80)),
                        set("curse-b", StatModifier.percent(Attribute.HEALTH, -0.80)));
        assertThat(snapshot.get(Attribute.HEALTH)).isEqualTo(1.0);
        assertThat(snapshot.get(Attribute.HEALTH)).isNotNegative();
    }

    @Test
    @DisplayName("cooldown reduction never exceeds the 40 percent cap, however many sources pile on")
    void cooldownCap() {
        StatSnapshot snapshot =
                compute(
                        set("a", StatModifier.flat(Attribute.ABILITY_COOLDOWN, 0.30)),
                        set("b", StatModifier.flat(Attribute.ABILITY_COOLDOWN, 0.30)),
                        set("c", StatModifier.flat(Attribute.ABILITY_COOLDOWN, 0.30)));
        assertThat(snapshot.get(Attribute.ABILITY_COOLDOWN)).isEqualTo(0.40);
    }

    @Test
    @DisplayName("attack speed with +200 percent lands on base x1.5, the edge of its band")
    void attackSpeedBand() {
        StatSnapshot snapshot = compute(set("haste", StatModifier.percent(Attribute.ATTACK_SPEED, 2.0)));
        double base = CONFIG.definition(Attribute.ATTACK_SPEED).base();
        assertThat(snapshot.get(Attribute.ATTACK_SPEED)).isEqualTo(base * 1.5);
    }

    @Test
    @DisplayName("movement speed with -90 percent lands on base x0.7, the other edge of its band")
    void movementSpeedBand() {
        StatSnapshot snapshot =
                compute(set("slow", StatModifier.percent(Attribute.MOVEMENT_SPEED, -0.90)));
        double base = CONFIG.definition(Attribute.MOVEMENT_SPEED).base();
        assertThat(snapshot.get(Attribute.MOVEMENT_SPEED)).isEqualTo(base * 0.7);
    }

    @Test
    @DisplayName("inside the band nothing is clamped")
    void insideBandUnchanged() {
        StatSnapshot snapshot =
                compute(set("mild", StatModifier.percent(Attribute.MOVEMENT_SPEED, 0.10)));
        double base = CONFIG.definition(Attribute.MOVEMENT_SPEED).base();
        assertThat(snapshot.get(Attribute.MOVEMENT_SPEED)).isEqualTo(base * 1.10);
    }

    @Test
    @DisplayName("the band follows the base contributions, not the configured base")
    void bandFollowsEffectiveBase() {
        double[] baseBonus = new double[Attribute.count()];
        baseBonus[Attribute.ATTACK_SPEED.ordinal()] = 4.0; // effective base 8.0

        StatSnapshot snapshot =
                StatCalculator.compute(
                        CONFIG,
                        List.of(set("haste", StatModifier.percent(Attribute.ATTACK_SPEED, 2.0))),
                        baseBonus,
                        1L);
        assertThat(snapshot.get(Attribute.ATTACK_SPEED)).isEqualTo(12.0);
    }

    @Test
    @DisplayName("a base contribution moves the base the percentage multiplies")
    void baseContributionIsMultiplied() {
        double[] baseBonus = new double[Attribute.count()];
        baseBonus[Attribute.HEALTH.ordinal()] = 100.0; // effective base 200

        StatSnapshot snapshot =
                StatCalculator.compute(
                        CONFIG,
                        List.of(set("vitality", StatModifier.percent(Attribute.HEALTH, 0.50))),
                        baseBonus,
                        1L);
        assertThat(snapshot.get(Attribute.HEALTH)).isEqualTo(300.0);
    }

    @Test
    @DisplayName("contributions to one attribute leave the others at their base")
    void attributesAreIndependent() {
        StatSnapshot snapshot = compute(set("chest", StatModifier.flat(Attribute.DEFENSE, 40.0)));
        assertThat(snapshot.get(Attribute.DEFENSE)).isEqualTo(40.0);
        assertThat(snapshot.get(Attribute.HEALTH)).isEqualTo(100.0);
        assertThat(snapshot.get(Attribute.MANA)).isEqualTo(50.0);
    }
}
