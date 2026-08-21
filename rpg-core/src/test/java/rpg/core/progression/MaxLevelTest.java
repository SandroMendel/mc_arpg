package rpg.core.progression;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The ceiling (FR-049 to FR-052, SC-008).
 *
 * <p>Level 60 is the end of level progression. Further growth runs through coins (B08) and equipment
 * (B11). Experience above it lapses <b>silently</b> - no error, no overflow, and no log line per
 * event, because a player at the maximum keeps killing things for hours.
 */
class MaxLevelTest {

    @Test
    @DisplayName("at the maximum level nothing changes, whatever arrives")
    void nothingChangesAtTheCeiling() {
        ProgressionFixture fixture = maxedOut();
        UUID character = fixture.character(new ProgressState(10, 0L));

        XpResult result = fixture.progression.grant(character, 5_000L, XpSource.MOB_KILL);

        assertThat(result.rejection()).isEqualTo(XpRejection.AT_MAX_LEVEL);
        assertThat(result.granted()).isZero();
        assertThat(result.discarded()).isEqualTo(5_000L);
        assertThat(fixture.progression.levelOf(character)).hasValue(10);
    }

    @Test
    @DisplayName("ten thousand events at the ceiling publish no event at all")
    void noEventsAtTheCeiling() {
        ProgressionFixture fixture = maxedOut();
        UUID character = fixture.character(new ProgressState(10, 0L));

        for (int i = 0; i < 10_000; i++) {
            fixture.progression.grant(character, 12L, XpSource.MOB_KILL);
        }

        assertThat(fixture.levelUps).isEmpty();
        assertThat(fixture.repository.totalMarks())
                .as("nothing changed, so there is nothing to write")
                .isZero();
    }

    @Test
    @DisplayName("progress at the ceiling reads as complete, not as 0 % of the next level")
    void progressReadsAsComplete() {
        ProgressionFixture fixture = maxedOut();
        UUID character = fixture.character(new ProgressState(10, 0L));

        ProgressView view = fixture.progression.progressOf(character).orElseThrow();

        assertThat(view.atMaxLevel()).isTrue();
        assertThat(view.xpForNextLevel()).isZero();
        assertThat(view.fraction()).as("a full bar, not an empty one").isEqualTo(1.0);
    }

    @Test
    @DisplayName("a gain that overshoots the ceiling lands exactly on it")
    void overshootLandsOnTheCeiling() {
        ProgressionFixture fixture = maxedOut();
        // One level below the ceiling, so the gain crosses it with plenty to spare.
        UUID character = fixture.character(new ProgressState(9, 0L));

        XpResult result = fixture.progression.grant(character, 100_000L, XpSource.MOB_KILL);

        assertThat(result.levelUp().newLevel()).isEqualTo(10);
        assertThat(result.levelUp().xpInLevel()).as("no negative remainder, no overflow").isZero();
        assertThat(result.discarded()).isPositive();
        assertThat(fixture.progression.progressOf(character).orElseThrow().atMaxLevel()).isTrue();
    }

    @Test
    @DisplayName("the maximum level comes from the curve, so a longer table raises it")
    void maxLevelComesFromTheCurve() {
        assertThat(maxedOut().progression.maxLevel()).isEqualTo(10);
        assertThat(
                        new ProgressionFixture(
                                        ProgressionFixture.config(CurveFixture.upTo60()))
                                .progression
                                .maxLevel())
                .isEqualTo(60);
    }

    private static ProgressionFixture maxedOut() {
        // Curve 2..10, so level 10 is the ceiling - short enough to reach in a test, and the rule
        // under test does not care how far away the ceiling is.
        return new ProgressionFixture(ProgressionFixture.config(CurveFixture.valid()));
    }
}
