package rpg.core.progression;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Granting experience and rising a level (FR-017 to FR-024, SC-001, SC-009).
 *
 * <p>The arithmetic examples are the ones from the specification, with their numbers spelled out. A
 * test that asserts "level went up" would pass on a formula that is off by a factor of two.
 */
class LevelUpTest {

    @Test
    @DisplayName("exactly the threshold reaches the next level with no remainder")
    void exactThreshold() {
        ProgressionFixture fixture = new ProgressionFixture();
        UUID character = fixture.character();

        XpResult result = fixture.progression.grant(character, 100L, XpSource.MOB_KILL);

        assertThat(result.leveledUp()).isTrue();
        assertThat(result.levelUp().previousLevel()).isEqualTo(1);
        assertThat(result.levelUp().newLevel()).isEqualTo(2);
        assertThat(result.levelUp().xpInLevel()).as("nothing left over").isZero();
        assertThat(fixture.progression.progressOf(character)).isPresent();
        assertThat(fixture.progression.progressOf(character).get().level()).isEqualTo(2);
    }

    @Test
    @DisplayName("250 experience against thresholds 100 and 120 lands on level 3 with 30 left")
    void twoLevelsWithRemainder() {
        ProgressionFixture fixture = new ProgressionFixture();
        UUID character = fixture.character();

        XpResult result = fixture.progression.grant(character, 250L, XpSource.MOB_KILL);

        assertThat(result.levelUp().newLevel()).isEqualTo(3);
        assertThat(result.levelUp().xpInLevel()).isEqualTo(30L);
    }

    @Test
    @DisplayName("a gain below the threshold keeps the level and accumulates")
    void belowThresholdAccumulates() {
        ProgressionFixture fixture = new ProgressionFixture();
        UUID character = fixture.character();

        fixture.progression.grant(character, 40L, XpSource.MOB_KILL);
        XpResult second = fixture.progression.grant(character, 30L, XpSource.MOB_KILL);

        assertThat(second.leveledUp()).isFalse();
        ProgressView view = fixture.progression.progressOf(character).orElseThrow();
        assertThat(view.level()).isEqualTo(1);
        assertThat(view.xpInLevel()).isEqualTo(70L);
        assertThat(view.xpForNextLevel()).isEqualTo(100L);
    }

    @Test
    @DisplayName("crossing three levels at once publishes ONE event, not three")
    void multipleLevelsPublishOneEvent() {
        // Curve 2..10 at 100, 120, 140, ... - 100 + 120 + 140 = 360 reaches level 4.
        ProgressionFixture fixture =
                new ProgressionFixture(ProgressionFixture.config(CurveFixture.valid()));
        UUID character = fixture.character();

        XpResult result = fixture.progression.grant(character, 360L, XpSource.MOB_KILL);

        assertThat(result.levelUp().previousLevel()).isEqualTo(1);
        assertThat(result.levelUp().newLevel()).isEqualTo(4);
        assertThat(result.levelUp().levelsGained()).isEqualTo(3);
        assertThat(fixture.levelUps)
                .as("one moment for the player is one event, not a stack of three")
                .hasSize(1);
        assertThat(fixture.levelUps.get(0).previousLevel()).isEqualTo(1);
        assertThat(fixture.levelUps.get(0).newLevel()).isEqualTo(4);
        assertThat(fixture.levelUps.get(0).byAdmin()).isFalse();
    }

    @Test
    @DisplayName("a gain marks the character dirty and never reads the database")
    void gainMarksAndDoesNotRead() {
        ProgressionFixture fixture = new ProgressionFixture();
        UUID character = fixture.character();

        fixture.progression.grant(character, 10L, XpSource.MOB_KILL);

        assertThat(fixture.repository.marksFor(character)).isEqualTo(1);
        assertThat(fixture.repository.reads).isZero();
    }

    @Test
    @DisplayName("level and experience never go down through play")
    void progressNeverFallsInPlay() {
        ProgressionFixture fixture = new ProgressionFixture();
        UUID character = fixture.character();
        fixture.progression.grant(character, 150L, XpSource.MOB_KILL);
        ProgressView before = fixture.progression.progressOf(character).orElseThrow();

        // Death is not modelled here - the point is that nothing in this block offers a way down
        // except setProgress, which AdminProgressTest covers. Every other source is rejected.
        XpResult negative = fixture.progression.grant(character, -50L, XpSource.MOB_KILL);
        XpResult zero = fixture.progression.grant(character, 0L, XpSource.ZONE_OBJECTIVE);

        assertThat(negative.rejection()).isEqualTo(XpRejection.INVALID_AMOUNT);
        assertThat(zero.rejection()).isEqualTo(XpRejection.INVALID_AMOUNT);
        ProgressView after = fixture.progression.progressOf(character).orElseThrow();
        assertThat(after.level()).isEqualTo(before.level());
        assertThat(after.xpInLevel()).isEqualTo(before.xpInLevel());
    }

    @Test
    @DisplayName("a source other than ADMIN may not lower anything")
    void onlyAdminMayLower() {
        assertThat(XpSource.MOB_KILL.mayLower()).isFalse();
        assertThat(XpSource.ZONE_OBJECTIVE.mayLower()).isFalse();
        assertThat(XpSource.ADMIN.mayLower()).isTrue();
    }

    @Test
    @DisplayName("the progress view needs no arithmetic from its reader")
    void progressViewIsComplete() {
        ProgressionFixture fixture = new ProgressionFixture();
        UUID character = fixture.character();
        fixture.progression.grant(character, 50L, XpSource.MOB_KILL);

        ProgressView view = fixture.progression.progressOf(character).orElseThrow();

        assertThat(view.level()).isEqualTo(1);
        assertThat(view.xpInLevel()).isEqualTo(50L);
        assertThat(view.xpForNextLevel()).isEqualTo(100L);
        assertThat(view.atMaxLevel()).isFalse();
        assertThat(view.fraction()).isEqualTo(0.5);
    }
}
