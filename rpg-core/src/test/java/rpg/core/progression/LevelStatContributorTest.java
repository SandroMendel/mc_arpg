package rpg.core.progression;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.stats.Attribute;
import rpg.core.stats.StatConfig;

/**
 * How a level reaches the eight attributes (FR-020 to FR-022c).
 *
 * <p>These tests run against the <b>real</b> {@code DefaultStatEngine}. A stub would happily accept
 * a contribution on the wrong side of the band and prove nothing - and the band is the entire reason
 * ADR-013 chose a base contribution over a modifier.
 */
class LevelStatContributorTest {

    @Test
    @DisplayName("level 1 contributes nothing - the level-1 value IS the base from B04")
    void levelOneContributesNothing() {
        ProgressionFixture fixture = new ProgressionFixture();
        UUID character = fixture.character();

        double base = StatConfig.defaults().definition(Attribute.HEALTH).base();

        assertThat(fixture.attribute(character, Attribute.HEALTH)).isEqualTo(base);
    }

    @Test
    @DisplayName("growth is per-level times levels gained, not a sum over a loop")
    void growthScalesWithLevel() {
        ProgressionFixture fixture = new ProgressionFixture();
        UUID character = fixture.character();
        double base = StatConfig.defaults().definition(Attribute.HEALTH).base();

        // 100 + 120 + 140 = 360 reaches level 4, so three levels of growth at 8.0 each.
        fixture.progression.grant(character, 360L, XpSource.MOB_KILL);

        assertThat(fixture.progression.levelOf(character)).hasValue(4);
        assertThat(fixture.attribute(character, Attribute.HEALTH)).isEqualTo(base + 3 * 8.0);
        assertThat(fixture.attribute(character, Attribute.MANA))
                .isEqualTo(StatConfig.defaults().definition(Attribute.MANA).base() + 3 * 4.0);
    }

    @Test
    @DisplayName("the three attributes configured at zero do not move")
    void zeroGrowthAttributesDoNotMove() {
        ProgressionFixture fixture = new ProgressionFixture();
        UUID character = fixture.character();
        double speedBefore = fixture.attribute(character, Attribute.MOVEMENT_SPEED);
        double attackBefore = fixture.attribute(character, Attribute.ATTACK_SPEED);
        double cooldownBefore = fixture.attribute(character, Attribute.ABILITY_COOLDOWN);

        fixture.progression.grant(character, 360L, XpSource.MOB_KILL);

        assertThat(fixture.attribute(character, Attribute.MOVEMENT_SPEED)).isEqualTo(speedBefore);
        assertThat(fixture.attribute(character, Attribute.ATTACK_SPEED)).isEqualTo(attackBefore);
        assertThat(fixture.attribute(character, Attribute.ABILITY_COOLDOWN))
                .isEqualTo(cooldownBefore);
    }

    @Test
    @DisplayName("growth raises the EFFECTIVE base, so the modifier band moves with the level")
    void growthMovesTheBand() {
        // The whole argument of ADR-013 in one assertion. An equipment modifier is clamped into a
        // band around the effective base; if the level contributed as a FLAT modifier instead, the
        // band would stay anchored to the level-1 base and this equipment value would be clamped
        // differently at level 4 than the level actually warrants.
        ProgressionFixture fixture = new ProgressionFixture();
        UUID atLevelOne = fixture.character();
        UUID grown = fixture.character();

        fixture.progression.grant(grown, 360L, XpSource.MOB_KILL);

        fixture.equip(atLevelOne, Attribute.HEALTH, 500.0);
        fixture.equip(grown, Attribute.HEALTH, 500.0);

        double low = fixture.attribute(atLevelOne, Attribute.HEALTH);
        double high = fixture.attribute(grown, Attribute.HEALTH);

        assertThat(high)
                .as("the same item is worth more on a higher level, because the band moved with it")
                .isGreaterThan(low);
    }

    @Test
    @DisplayName("growth runs into the cap from B04 rather than over it")
    void growthRespectsTheCap() {
        // Defence is capped at 300 in stats.yml. Growth of 400 per level would exceed it after one
        // level; B06 does not check that itself, StatCalculator clamps - and there must be exactly
        // one truth about the limit (FR-022c).
        ProgressionFixture fixture =
                new ProgressionFixture(
                        ProgressionFixture.config(
                                CurveFixture.valid(),
                                ProgressionFixture.growth(8.0, 400.0, 4.0, 1.5, 1.5)));
        UUID character = fixture.character();
        double max = StatConfig.defaults().definition(Attribute.DEFENSE).max();

        fixture.progression.grant(character, 360L, XpSource.MOB_KILL);

        assertThat(fixture.attribute(character, Attribute.DEFENSE)).isEqualTo(max);
    }

    @Test
    @DisplayName("a holder without a character contributes nothing and does not throw")
    void holderWithoutCharacter() {
        ProgressionFixture fixture = new ProgressionFixture();
        UUID mob = UUID.randomUUID();

        fixture.stats.createForEntity(mob);

        // B04 recalculates creatures through the same contributor chain. A contributor that threw
        // here would take the whole mob down with it.
        assertThat(fixture.stats.value(mob, Attribute.HEALTH))
                .isEqualTo(StatConfig.defaults().definition(Attribute.HEALTH).base());
    }

    @Test
    @DisplayName("the contributor keeps a stable id, so a second registration replaces it")
    void stableId() {
        assertThat(LevelStatContributor.ID).isEqualTo("progression-level");
    }
}
