package rpg.core.progression;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Bundling progress messages and the order they arrive in (FR-023a to FR-023c, SC-018, SC-020).
 *
 * <p>The rule under test is not "fewer events" but "no message ever contradicts a later one". A
 * bundle of the old level arriving after a level-up would make the progress bar jump backwards - a
 * bug that only shows up on a player and then refuses to reproduce.
 */
class ProgressAggregatorTest {

    private static ProgressionFixture fixtureWithBigCurve() {
        Map<Integer, Long> curve = new LinkedHashMap<>();
        curve.put(2, 1_000L);
        curve.put(3, 2_000L);
        return new ProgressionFixture(ProgressionFixture.config(curve));
    }

    private static List<ProgressChangedEvent> subscribe(ProgressionFixture fixture) {
        List<ProgressChangedEvent> seen = new ArrayList<>();
        fixture.eventBus.subscribe(ProgressChangedEvent.class, seen::add);
        return seen;
    }

    @Test
    @DisplayName("a hundred gains inside one window become one event carrying their sum")
    void hundredGainsOneEvent() {
        ProgressionFixture fixture = fixtureWithBigCurve();
        List<ProgressChangedEvent> events = subscribe(fixture);
        UUID character = fixture.character();

        for (int i = 0; i < 100; i++) {
            fixture.progression.grant(character, 3L, XpSource.MOB_KILL);
        }
        // Nothing reported yet - the window is still open.
        assertThat(events).isEmpty();

        fixture.clock.advanceMillis(500);
        fixture.progression.grant(character, 3L, XpSource.MOB_KILL);

        assertThat(events).hasSize(1);
        assertThat(events.get(0).gained()).as("SC-018: the sum of all 101").isEqualTo(303L);
        assertThat(events.get(0).level()).isEqualTo(1);
        assertThat(events.get(0).xpForNextLevel()).isEqualTo(1_000L);
    }

    @Test
    @DisplayName("a level-up delivers the open bundle FIRST, then the level-up")
    void levelUpFlushesTheBundleFirst() {
        ProgressionFixture fixture = fixtureWithBigCurve();
        List<Object> order = new ArrayList<>();
        fixture.eventBus.subscribe(ProgressChangedEvent.class, order::add);
        fixture.eventBus.subscribe(LevelUpEvent.class, order::add);
        UUID character = fixture.character();

        fixture.progression.grant(character, 400L, XpSource.MOB_KILL);
        fixture.progression.grant(character, 400L, XpSource.MOB_KILL);
        assertThat(order).as("still inside the window").isEmpty();

        // This one crosses the threshold of 1000.
        fixture.progression.grant(character, 400L, XpSource.MOB_KILL);

        assertThat(order).hasSize(2);
        assertThat(order.get(0)).isInstanceOf(ProgressChangedEvent.class);
        assertThat(order.get(1)).isInstanceOf(LevelUpEvent.class);

        ProgressChangedEvent bundle = (ProgressChangedEvent) order.get(0);
        assertThat(bundle.gained()).isEqualTo(800L);
        assertThat(bundle.level())
                .as("SC-020: the bundle belongs to the OLD level, not the new one")
                .isEqualTo(1);
        assertThat(((LevelUpEvent) order.get(1)).newLevel()).isEqualTo(2);
    }

    @Test
    @DisplayName("no event with the old level arrives after the level-up")
    void nothingStaleAfterTheLevelUp() {
        ProgressionFixture fixture = fixtureWithBigCurve();
        List<Object> order = new ArrayList<>();
        fixture.eventBus.subscribe(ProgressChangedEvent.class, order::add);
        fixture.eventBus.subscribe(LevelUpEvent.class, order::add);
        UUID character = fixture.character();

        fixture.progression.grant(character, 900L, XpSource.MOB_KILL);
        fixture.progression.grant(character, 200L, XpSource.MOB_KILL);
        fixture.clock.advanceMillis(1_000);
        fixture.progression.grant(character, 10L, XpSource.MOB_KILL);

        int levelUpAt = -1;
        for (int i = 0; i < order.size(); i++) {
            if (order.get(i) instanceof LevelUpEvent) {
                levelUpAt = i;
            }
        }
        assertThat(levelUpAt).isNotNegative();
        for (int i = levelUpAt + 1; i < order.size(); i++) {
            if (order.get(i) instanceof ProgressChangedEvent progress) {
                assertThat(progress.level())
                        .as("a message after the rise must not speak of level 1")
                        .isEqualTo(2);
            }
        }
    }

    @Test
    @DisplayName("releasing discards the open bundle, and the experience is still credited")
    void releaseDiscardsTheBundle() {
        ProgressionFixture fixture = fixtureWithBigCurve();
        List<ProgressChangedEvent> events = subscribe(fixture);
        UUID character = fixture.character();
        fixture.progression.grant(character, 400L, XpSource.MOB_KILL);
        long credited = fixture.progression.progressOf(character).orElseThrow().xpInLevel();

        fixture.progression.release(character);

        assertThat(events).as("presentation only, and the recipient is gone").isEmpty();
        assertThat(credited).as("the experience itself was credited long before").isEqualTo(400L);
        assertThat(fixture.progression.openProgressWindows()).isZero();
    }

    @Test
    @DisplayName("no window leaks after eight hundred load and release cycles")
    void noWindowLeak() {
        ProgressionFixture fixture = fixtureWithBigCurve();

        for (int i = 0; i < 800; i++) {
            UUID character = fixture.character();
            fixture.progression.grant(character, 7L, XpSource.MOB_KILL);
            fixture.progression.release(character);
        }

        assertThat(fixture.progression.openProgressWindows()).isZero();
        assertThat(fixture.progression.loadedCount()).isZero();
    }

    @Test
    @DisplayName("the window is never closed by a scheduled task")
    void noTaskCloses() {
        ProgressionFixture fixture = fixtureWithBigCurve();
        UUID character = fixture.character();

        for (int i = 0; i < 50; i++) {
            fixture.progression.grant(character, 5L, XpSource.MOB_KILL);
            fixture.clock.advanceMillis(600);
        }

        // FR-061. A window nobody touches again simply stops - it costs one bucket in memory, which
        // the release path clears.
        assertThat(fixture.scheduler.scheduled).isZero();
    }

    @Test
    @DisplayName("the aggregator on its own: a window opens, sums and closes on the next gain")
    void aggregatorInIsolation() {
        ProgressionFixture.TestClock clock = new ProgressionFixture.TestClock();
        ProgressAggregator aggregator = new ProgressAggregator(clock, Duration.ofMillis(500));
        UUID character = UUID.randomUUID();

        assertThat(aggregator.record(character, 10L)).as("opens, reports nothing").isZero();
        assertThat(aggregator.record(character, 10L)).as("still open").isZero();
        clock.advanceMillis(500);
        assertThat(aggregator.record(character, 5L)).as("closes with everything").isEqualTo(25L);
        assertThat(aggregator.record(character, 1L)).as("a fresh window").isZero();
        assertThat(aggregator.flush(character)).isEqualTo(1L);
        assertThat(aggregator.flush(character)).as("nothing left").isZero();
    }
}
