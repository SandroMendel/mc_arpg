package rpg.core.progression;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.stats.Attribute;
import rpg.core.stats.StatConfig;

/**
 * Health and mana on a level-up (FR-021, FR-021a, FR-021b, SC-019).
 *
 * <p>A rise heals to the <b>new</b> maximum. Deliberate consequence, decided in the second clarify
 * round: with no level-difference scaling a player can save a pending rise for a boss fight and use
 * it as a full heal. Self-limiting - each level rises once, 59 times per character, and at the
 * ceiling it stops entirely.
 */
class LevelUpResourcesTest {

    @Test
    @DisplayName("a rise fills health and mana to the new maximum")
    void riseFillsToNewMaximum() {
        ProgressionFixture fixture = new ProgressionFixture();
        UUID character = fixture.character();
        double baseMax = StatConfig.defaults().definition(Attribute.HEALTH).base();
        fixture.setHealth(character, 12.0);

        fixture.progression.grant(character, 100L, XpSource.MOB_KILL);

        assertThat(fixture.maxHealth(character))
                .as("one level of growth on top of the base")
                .isEqualTo(baseMax + 8.0);
        assertThat(fixture.health(character))
                .as("filled to the NEW maximum, not the old one")
                .isEqualTo(baseMax + 8.0);
    }

    @Test
    @DisplayName("filling happens AFTER recalculation, so it fills against the new maximum")
    void fillsAgainstTheNewMaximum() {
        // The order in FR-021b. Filling first would top up to the old maximum - an error a few
        // percent wide on every single rise, which is exactly the kind that stays unnoticed.
        ProgressionFixture fixture = new ProgressionFixture();
        UUID character = fixture.character();
        double baseMax = StatConfig.defaults().definition(Attribute.HEALTH).base();
        fixture.setHealth(character, 1.0);

        // Three levels at once: 24 points of growth, so the gap between old and new maximum is wide
        // enough that filling on the wrong side of the recalculation cannot pass unnoticed.
        fixture.progression.grant(character, 360L, XpSource.MOB_KILL);

        assertThat(fixture.health(character)).isEqualTo(baseMax + 24.0);
        assertThat(fixture.health(character)).isGreaterThan(baseMax);
    }

    @Test
    @DisplayName("crossing three levels fills once, not three times")
    void fillsOncePerRise() {
        ProgressionFixture fixture = new ProgressionFixture();
        UUID character = fixture.character();
        fixture.setHealth(character, 5.0);

        fixture.progression.grant(character, 360L, XpSource.MOB_KILL);

        // Filling three times would be indistinguishable in the value - so the assertion is on the
        // event, which is published exactly once per rise and comes from the same code path.
        assertThat(fixture.levelUps).hasSize(1);
        assertThat(fixture.levelUps.get(0).levelsGained()).isEqualTo(3);
    }

    @Test
    @DisplayName("a gain without a rise leaves health alone")
    void noRiseNoFill() {
        ProgressionFixture fixture = new ProgressionFixture();
        UUID character = fixture.character();
        fixture.setHealth(character, 12.0);

        fixture.progression.grant(character, 50L, XpSource.MOB_KILL);

        assertThat(fixture.health(character))
                .as("experience is not a healing potion")
                .isEqualTo(12.0);
    }

    @Test
    @DisplayName("mana is filled along with health")
    void manaIsFilledToo() {
        ProgressionFixture fixture = new ProgressionFixture();
        UUID character = fixture.character();
        double manaBase = StatConfig.defaults().definition(Attribute.MANA).base();
        fixture.stats.changeMana(fixture.playerOf(character), -manaBase);
        assertThat(fixture.mana(character)).isZero();

        fixture.progression.grant(character, 100L, XpSource.MOB_KILL);

        assertThat(fixture.mana(character)).isEqualTo(manaBase + 4.0);
    }
}
