package rpg.core.progression;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The query five blocks gate their content on (FR-025 to FR-028, SC-011).
 *
 * <p>B08 unlocks abilities by it, B09 gates zones, B11 gates items, B12 counts and B13 displays. The
 * promise that matters most is not the arithmetic but that the query <b>never aborts the caller</b>:
 * an unknown character answers "not met" and logs.
 */
class LevelRequirementTest {

    @Test
    @DisplayName("a character on level 5 meets 1 through 5, but not 6")
    void meetsUpToItsLevel() {
        ProgressionFixture fixture = new ProgressionFixture();
        UUID character = fixture.character(new ProgressState(5, 0L));

        assertThat(fixture.progression.meetsLevel(character, 1)).isTrue();
        assertThat(fixture.progression.meetsLevel(character, 5)).isTrue();
        assertThat(fixture.progression.meetsLevel(character, 6)).isFalse();
        assertThat(fixture.progression.meetsLevel(character, 60)).isFalse();
    }

    @Test
    @DisplayName("the requirement is a minimum, so rising keeps it satisfied")
    void requirementIsAMinimum() {
        ProgressionFixture fixture = new ProgressionFixture();
        UUID character = fixture.character();
        assertThat(fixture.progression.meetsLevel(character, 2)).isFalse();

        fixture.progression.grant(character, 100L, XpSource.MOB_KILL);

        assertThat(fixture.progression.meetsLevel(character, 2)).isTrue();
        assertThat(fixture.progression.meetsLevel(character, 1)).isTrue();
    }

    @Test
    @DisplayName("an unknown character answers 'not met' instead of throwing")
    void unknownCharacterDoesNotThrow() {
        ProgressionFixture fixture = new ProgressionFixture();

        // If this threw, a zone entry or an ability use would abort mid-flight in a block that has
        // no business knowing how progress is stored (FR-027).
        assertThat(fixture.progression.meetsLevel(UUID.randomUUID(), 1)).isFalse();
    }

    @Test
    @DisplayName("a released character answers 'not met' as well")
    void releasedCharacterDoesNotThrow() {
        ProgressionFixture fixture = new ProgressionFixture();
        UUID character = fixture.character(new ProgressState(5, 0L));
        assertThat(fixture.progression.meetsLevel(character, 5)).isTrue();

        fixture.progression.release(character);

        assertThat(fixture.progression.meetsLevel(character, 5)).isFalse();
        assertThat(fixture.progression.levelOf(character)).isEmpty();
    }

    @Test
    @DisplayName("the maximum level is available without knowing the curve")
    void maxLevelIsQueryable() {
        ProgressionFixture fixture =
                new ProgressionFixture(ProgressionFixture.config(CurveFixture.upTo60()));

        // B09 needs this to reject a zone requirement above the ceiling without reading config.
        assertThat(fixture.progression.maxLevel()).isEqualTo(60);
    }

    @Test
    @DisplayName("a requirement of zero or below is always met")
    void nonPositiveRequirement() {
        ProgressionFixture fixture = new ProgressionFixture();
        UUID character = fixture.character();

        // Level 1 is the floor, so "no requirement" and "requires level 1" are the same thing. A
        // caller passing 0 should not need to special-case it.
        assertThat(fixture.progression.meetsLevel(character, 0)).isTrue();
        assertThat(fixture.progression.meetsLevel(character, -5)).isTrue();
    }
}
