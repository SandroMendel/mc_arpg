package rpg.core.progression;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.OptionalLong;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What a creature is worth (FR-009, FR-010, FR-060, SC-002).
 *
 * <p>The decision under test is a deliberate one: <b>no scaling by level difference</b>. Powerlevel\
 * ling by being dragged into a high zone is possible, and starter mobs stay farmable forever. The
 * limit is the level requirement on zones in B09, not the arithmetic here.
 */
class XpAmountTest {

    @Test
    @DisplayName("the amount depends on the mob alone, not on the player's level")
    void amountIgnoresPlayerLevel() {
        ProgressionFixture fixture =
                new ProgressionFixture(ProgressionFixture.config(CurveFixture.upTo60()));
        UUID fresh = fixture.character();
        UUID veteran = fixture.character(new ProgressState(59, 0L));

        long forFresh = fixture.progression.xpForMob("ZOMBIE");
        long forVeteran = fixture.progression.xpForMob("ZOMBIE");

        assertThat(forFresh).isEqualTo(12L);
        assertThat(forVeteran)
                .as("the same creature is worth the same to everyone (FR-010)")
                .isEqualTo(forFresh);

        // And the credited amount matches, for both.
        assertThat(fixture.progression.grant(fresh, forFresh, XpSource.MOB_KILL).granted())
                .isEqualTo(12L);
        assertThat(fixture.progression.grant(veteran, forVeteran, XpSource.MOB_KILL).granted())
                .isEqualTo(12L);
    }

    @Test
    @DisplayName("a creature without an entry of its own gets the configured default")
    void unknownMobGetsTheDefault() {
        ProgressionFixture fixture = new ProgressionFixture();

        // Zero would mean every mob Mojang adds next release is silently worthless (FR-060).
        assertThat(fixture.progression.xpForMob("SHEEP")).isEqualTo(10L);
        assertThat(fixture.progression.xpForMob("SOMETHING_ADDED_IN_A_FUTURE_VERSION"))
                .isEqualTo(10L);
    }

    @Test
    @DisplayName("an entry of its own wins over the default")
    void ownEntryWins() {
        ProgressionFixture fixture = new ProgressionFixture();

        assertThat(fixture.progression.xpForMob("CREEPER")).isEqualTo(18L);
    }

    @Test
    @DisplayName("B10 can replace the amounts through the same interface")
    void providerIsReplaceable() {
        ProgressionFixture fixture = new ProgressionFixture();

        // Exactly the arrangement B05 uses for mob attribute values: B06 answers from its own
        // configuration until B10 exists, then B10 takes over without a code change here.
        fixture.progression.setMobXpProvider(
                key -> "ZOMBIE".equals(key) ? OptionalLong.of(999L) : OptionalLong.empty());

        assertThat(fixture.progression.xpForMob("ZOMBIE")).isEqualTo(999L);
        assertThat(fixture.progression.xpForMob("CREEPER"))
                .as("no answer from the provider falls back to the default, not to the old table")
                .isEqualTo(10L);
    }
}
