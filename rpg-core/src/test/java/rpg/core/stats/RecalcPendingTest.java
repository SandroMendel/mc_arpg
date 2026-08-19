package rpg.core.stats;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** T041: what happens between a change and the recalculation that follows it (FR-022, edge cases). */
class RecalcPendingTest {

    @Test
    @DisplayName("a query while a mark is outstanding returns the last valid snapshot without computing")
    void queryDuringPendingReturnsLastValid() {
        EngineFixture fixture = new EngineFixture();
        UUID holder = fixture.character();

        fixture.engine.apply(
                holder,
                EngineFixture.equipment("slot:CHEST", StatModifier.flat(Attribute.HEALTH, 500.0)));

        // The task is scheduled but has not run: the value is still the old one, at most one tick
        // stale - the same concession FR-021 already makes for in-flight actions.
        assertThat(fixture.engine.value(holder, Attribute.HEALTH)).isEqualTo(100.0);
        assertThat(fixture.recalculations).isEmpty();

        fixture.tick();
        assertThat(fixture.engine.value(holder, Attribute.HEALTH)).isEqualTo(600.0);
    }

    @Test
    @DisplayName("a repeated query without a change computes nothing")
    void repeatedQueryIsFree() {
        EngineFixture fixture = new EngineFixture();
        UUID holder = fixture.character();

        for (int i = 0; i < 100; i++) {
            fixture.engine.snapshot(holder);
        }

        assertThat(fixture.recalculations).isEmpty();
        assertThat(fixture.scheduler.scheduledCount()).isZero();
    }

    @Test
    @DisplayName("a holder removed while a mark is outstanding lets the task expire without effect")
    void removalDuringPendingIsHarmless() {
        EngineFixture fixture = new EngineFixture();
        UUID holder = fixture.character();

        fixture.engine.apply(
                holder,
                EngineFixture.equipment("slot:CHEST", StatModifier.flat(Attribute.HEALTH, 500.0)));
        assertThat(fixture.scheduler.pendingCount()).isEqualTo(1);

        fixture.engine.remove(holder);
        fixture.tick(); // the task runs, finds the holder gone, and does nothing

        assertThat(fixture.recalculations).isEmpty();
        assertThat(fixture.engine.findSnapshot(holder)).isEmpty();
    }

    @Test
    @DisplayName("recalculateNow skips the bundling and clears the mark")
    void recalculateNowClearsTheMark() {
        EngineFixture fixture = new EngineFixture();
        UUID holder = fixture.character();

        fixture.engine.apply(
                holder,
                EngineFixture.equipment("slot:CHEST", StatModifier.flat(Attribute.HEALTH, 500.0)));

        StatSnapshot immediate = fixture.engine.recalculateNow(holder);
        assertThat(immediate.get(Attribute.HEALTH)).isEqualTo(600.0);

        // The already-scheduled task still runs, but there is nothing left for it to catch up on.
        fixture.tick();
        assertThat(fixture.engine.value(holder, Attribute.HEALTH)).isEqualTo(600.0);
    }

    @Test
    @DisplayName("a recalculation publishes exactly one event carrying both snapshots")
    void eventCarriesBothSnapshots() {
        EngineFixture fixture = new EngineFixture();
        UUID holder = fixture.character();

        fixture.engine.apply(
                holder,
                EngineFixture.equipment("slot:CHEST", StatModifier.flat(Attribute.HEALTH, 500.0)));
        fixture.tick();

        assertThat(fixture.recalculations).hasSize(1);
        StatsRecalculatedEvent event = fixture.recalculations.get(0);
        assertThat(event.holderId()).isEqualTo(holder);
        assertThat(event.previous().get(Attribute.HEALTH)).isEqualTo(100.0);
        assertThat(event.current().get(Attribute.HEALTH)).isEqualTo(600.0);
    }

    @Test
    @DisplayName("the very first calculation reports no previous snapshot")
    void firstCalculationHasNoPrevious() {
        EngineFixture fixture = new EngineFixture();
        UUID playerId = UUID.randomUUID();
        fixture.engine.createForCharacter(playerId, UUID.randomUUID(), new ResourcePool(0.0, 0.0));

        fixture.engine.recalculateNow(playerId);

        assertThat(fixture.recalculations).hasSize(1);
        assertThat(fixture.recalculations.get(0).previous()).isNull();
        assertThat(fixture.recalculations.get(0).current()).isNotNull();
    }
}
