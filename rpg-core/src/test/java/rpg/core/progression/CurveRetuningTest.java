package rpg.core.progression;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What happens to existing characters when the curve is retuned (FR-053a, FR-024, SC-017).
 *
 * <p>The reason level and remainder are stored separately instead of one running total. With a total
 * the level would be a function of whatever curve is loaded right now, and raising the curve would
 * silently drop everybody a few levels - taking zone access (B09) and abilities (B08) with it.
 */
class CurveRetuningTest {

    private static Map<Integer, Long> curve(long... thresholds) {
        Map<Integer, Long> table = new LinkedHashMap<>();
        for (int i = 0; i < thresholds.length; i++) {
            table.put(i + 2, thresholds[i]);
        }
        return table;
    }

    @Test
    @DisplayName("doubling the curve leaves every existing level untouched")
    void raisingTheCurveKeepsLevels() {
        // A character on level 4 with 50 experience, stored.
        ProgressState stored = new ProgressState(4, 50L);

        ProgressionFixture cheap =
                new ProgressionFixture(
                        ProgressionFixture.config(curve(100, 200, 300, 400, 500)));
        UUID before = cheap.character(stored);
        assertThat(cheap.progression.levelOf(before)).hasValue(4);

        // Same stored state, curve doubled. Nobody drops (SC-017).
        ProgressionFixture expensive =
                new ProgressionFixture(
                        ProgressionFixture.config(curve(200, 400, 600, 800, 1000)));
        UUID after = expensive.character(stored);

        assertThat(expensive.progression.levelOf(after)).hasValue(4);
        assertThat(expensive.progression.progressOf(after).orElseThrow().xpInLevel()).isEqualTo(50L);
        // Only the way onwards changed.
        assertThat(expensive.progression.progressOf(after).orElseThrow().xpForNextLevel())
                .isEqualTo(800L);
    }

    @Test
    @DisplayName("lowering the curve turns the surplus into a rise on load, not into an error")
    void loweringTheCurveResolvesOnLoad() {
        // Stored: level 3 with 500 experience inside it. Under the old curve level 4 cost 600, so
        // that was a legal state.
        ProgressState stored = new ProgressState(3, 500L);

        // The curve is lowered: level 4 now costs 200, level 5 another 250. Still strictly
        // increasing - the validation would refuse anything else.
        ProgressionFixture cheaper =
                new ProgressionFixture(ProgressionFixture.config(curve(100, 150, 200, 250, 300)));

        UUID character = cheaper.character(stored);

        // 500 covers level 4 (200) and level 5 (250) with 50 left. Resolved by the same code an
        // ordinary gain uses, so "up" stays the only direction (FR-024).
        assertThat(cheaper.progression.levelOf(character)).hasValue(5);
        assertThat(cheaper.progression.progressOf(character).orElseThrow().xpInLevel())
                .isEqualTo(50L);
    }

    @Test
    @DisplayName("a normal load changes nothing and writes no dirty mark")
    void ordinaryLoadIsSilent() {
        ProgressionFixture fixture =
                new ProgressionFixture(ProgressionFixture.config(curve(100, 200, 300)));
        UUID playerId = UUID.randomUUID();
        UUID characterId = UUID.randomUUID();
        fixture.sessions.markReady(playerId);
        fixture.stats.createForCharacter(playerId, characterId, new rpg.core.stats.ResourcePool(0, 0));
        fixture.repository.marks.clear();

        fixture.progression.load(characterId, playerId, new ProgressState(2, 50L));

        assertThat(fixture.progression.levelOf(characterId)).hasValue(2);
        assertThat(fixture.repository.marksFor(characterId))
                .as("a login must not write just because it happened")
                .isZero();
    }

    @Test
    @DisplayName("a surplus that overshoots the new ceiling lands exactly on it")
    void surplusAtTheNewCeiling() {
        ProgressState stored = new ProgressState(2, 100_000L);

        // A curve that now ends at level 3.
        ProgressionFixture shortened =
                new ProgressionFixture(ProgressionFixture.config(curve(100, 150)));

        UUID character = shortened.character(stored);

        assertThat(shortened.progression.levelOf(character)).hasValue(3);
        ProgressView view = shortened.progression.progressOf(character).orElseThrow();
        assertThat(view.atMaxLevel()).isTrue();
        assertThat(view.xpInLevel()).as("no overflow, no negative remainder").isZero();
    }
}
