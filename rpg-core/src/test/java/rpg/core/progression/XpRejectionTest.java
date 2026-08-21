package rpg.core.progression;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Everything that makes a grant do nothing (FR-014, FR-015, FR-027, FR-059).
 *
 * <p>All of these are <b>return values</b>, never exceptions. Granting runs in the combat path, and
 * an exception per rejected amount would be an allocation plus a stack trace in the one path that
 * promises to allocate nothing (FR-062).
 */
class XpRejectionTest {

    @Test
    @DisplayName("zero and negative amounts are refused, never read as a deduction")
    void nonPositiveAmounts() {
        ProgressionFixture fixture = new ProgressionFixture();
        UUID character = fixture.character();
        fixture.progression.grant(character, 50L, XpSource.MOB_KILL);

        assertThat(fixture.progression.grant(character, 0L, XpSource.MOB_KILL).rejection())
                .isEqualTo(XpRejection.INVALID_AMOUNT);
        assertThat(fixture.progression.grant(character, -100L, XpSource.MOB_KILL).rejection())
                .isEqualTo(XpRejection.INVALID_AMOUNT);
        assertThat(fixture.progression.progressOf(character).orElseThrow().xpInLevel())
                .as("the earlier 50 is untouched")
                .isEqualTo(50L);
    }

    @Test
    @DisplayName("a character whose session is not ready loses the amount silently")
    void sessionNotReady() {
        ProgressionFixture fixture = new ProgressionFixture();
        UUID character = fixture.character();
        // Logged out between the kill and the split.
        fixture.sessions.markNotReady(fixture.playerOf(character));

        XpResult result = fixture.progression.grant(character, 100L, XpSource.MOB_KILL);

        assertThat(result.rejection()).isEqualTo(XpRejection.SESSION_NOT_READY);
        assertThat(result.granted()).isZero();
        assertThat(fixture.levelUps).isEmpty();
        assertThat(fixture.repository.totalMarks()).as("nothing to write either").isZero();
    }

    @Test
    @DisplayName("an unknown character is refused without an exception")
    void unknownCharacter() {
        ProgressionFixture fixture = new ProgressionFixture();

        XpResult result = fixture.progression.grant(UUID.randomUUID(), 100L, XpSource.MOB_KILL);

        assertThat(result.rejection()).isEqualTo(XpRejection.UNKNOWN_CHARACTER);
    }

    @Test
    @DisplayName("a level query for an unknown character answers 'not met' rather than throwing")
    void unknownCharacterLevelQuery() {
        ProgressionFixture fixture = new ProgressionFixture();

        // Five blocks gate content on this answer. A query must never abort the caller (FR-027).
        assertThat(fixture.progression.meetsLevel(UUID.randomUUID(), 1)).isFalse();
        assertThat(fixture.progression.progressOf(UUID.randomUUID())).isEmpty();
        assertThat(fixture.progression.levelOf(UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("experience goes to the character that is loaded, never to another")
    void goesToTheRightCharacter() {
        ProgressionFixture fixture = new ProgressionFixture();
        UUID first = fixture.character();
        UUID second = fixture.character();

        fixture.progression.grant(first, 100L, XpSource.MOB_KILL);

        assertThat(fixture.progression.levelOf(first)).hasValue(2);
        assertThat(fixture.progression.levelOf(second)).as("untouched").hasValue(1);
    }

    @Test
    @DisplayName("a fault while granting stays with that character and does not escape")
    void faultStaysLocal() {
        ProgressionFixture fixture = new ProgressionFixture();
        UUID character = fixture.character();
        fixture.repository.failNextMark = true;

        // No exception reaches the caller: the ongoing combat operation must keep running (FR-059).
        XpResult result = fixture.progression.grant(character, 100L, XpSource.MOB_KILL);

        assertThat(result.rejection()).isEqualTo(XpRejection.NONE);
        assertThat(result.leveledUp()).as("the call was cut short before reporting a rise").isFalse();

        // The state change itself stands - the level rose in memory even though the mark was lost.
        // That is on purpose and not a hole: the session-end path marks the character again before
        // releasing it, so the loss is bounded by one autosave interval at worst.
        assertThat(fixture.progression.levelOf(character)).hasValue(2);

        // And the character is not poisoned: the next grant behaves normally and marks again.
        XpResult next = fixture.progression.grant(character, 120L, XpSource.MOB_KILL);
        assertThat(next.leveledUp()).isTrue();
        assertThat(next.levelUp().newLevel()).isEqualTo(3);
        assertThat(fixture.repository.marksFor(character)).isEqualTo(1);
    }

    @Test
    @DisplayName("a released character is gone, with nothing left behind")
    void releaseLeavesNothing() {
        ProgressionFixture fixture = new ProgressionFixture();
        UUID character = fixture.character();
        assertThat(fixture.progression.loadedCount()).isEqualTo(1);

        fixture.progression.release(character);

        assertThat(fixture.progression.loadedCount()).isZero();
        assertThat(fixture.progression.characterOf(fixture.playerOf(character))).isEmpty();
        assertThat(fixture.progression.grant(character, 100L, XpSource.MOB_KILL).rejection())
                .isEqualTo(XpRejection.UNKNOWN_CHARACTER);
    }
}
