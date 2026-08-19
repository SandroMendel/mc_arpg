package rpg.core.stats;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T037-T039: bundling, idle cost and the tick budget (FR-018, FR-019, FR-019a, SC-001 to SC-003).
 *
 * <p>The assertions that matter here count <b>scheduled tasks</b>, not elapsed time. A timing
 * measurement would happily pass while a cheap-but-real sweep ran in every tick, and that sweep is
 * exactly what Principle II rules out.
 */
class RecalculationBudgetTest {

    private static ModifierSet slot(String name, double health) {
        return EngineFixture.equipment("slot:" + name, StatModifier.flat(Attribute.HEALTH, health));
    }

    @Test
    @DisplayName("a full equipment set in one tick produces exactly one recalculation")
    void fullEquipmentSetIsOneRecalculation() {
        EngineFixture fixture = new EngineFixture();
        UUID holder = fixture.character();

        fixture.engine.apply(holder, slot("HELMET", 10.0));
        fixture.engine.apply(holder, slot("CHEST", 20.0));
        fixture.engine.apply(holder, slot("LEGS", 15.0));
        fixture.engine.apply(holder, slot("BOOTS", 8.0));
        fixture.engine.apply(holder, slot("MAINHAND", 5.0));
        fixture.engine.apply(holder, slot("OFFHAND", 3.0));

        // One task for six changes - and no caller had to bracket anything (FR-019a).
        assertThat(fixture.scheduler.scheduledCount()).isEqualTo(1);

        fixture.tick();

        assertThat(fixture.recalculations).hasSize(1);
        assertThat(fixture.engine.value(holder, Attribute.HEALTH)).isEqualTo(161.0);
    }

    @Test
    @DisplayName("a login wave of 200 holders costs 200 recalculations, not one per item")
    void loginWave() {
        EngineFixture fixture = new EngineFixture();
        List<UUID> holders = new ArrayList<>();

        for (int i = 0; i < 200; i++) {
            UUID playerId = UUID.randomUUID();
            fixture.engine.createForCharacter(playerId, UUID.randomUUID(), new ResourcePool(0.0, 0.0));
            holders.add(playerId);
        }
        fixture.clearRecorded();

        for (UUID holder : holders) {
            fixture.engine.apply(holder, slot("HELMET", 10.0));
            fixture.engine.apply(holder, slot("CHEST", 20.0));
            fixture.engine.apply(holder, slot("LEGS", 15.0));
            fixture.engine.apply(holder, slot("BOOTS", 8.0));
            fixture.engine.apply(holder, slot("MAINHAND", 5.0));
            fixture.engine.apply(holder, slot("OFFHAND", 3.0));
        }

        assertThat(fixture.scheduler.scheduledCount()).isEqualTo(200);
        fixture.tick();
        assertThat(fixture.recalculations).hasSize(200);
    }

    @Test
    @DisplayName("200 holders with 20 sources each cost nothing at all over 1200 idle ticks")
    void idleCostsNothing() {
        EngineFixture fixture = new EngineFixture();

        for (int i = 0; i < 200; i++) {
            UUID playerId = UUID.randomUUID();
            fixture.engine.createForCharacter(playerId, UUID.randomUUID(), new ResourcePool(0.0, 0.0));
            for (int s = 0; s < 20; s++) {
                fixture.engine.apply(playerId, slot("source" + s, 1.0));
            }
            fixture.tick();
        }
        fixture.clearRecorded();

        for (int tick = 0; tick < 1200; tick++) {
            fixture.tick();
        }

        // No task existed, so no task ran, so nothing was recalculated. There is no sweep to be
        // cheap about.
        assertThat(fixture.scheduler.scheduledCount()).isZero();
        assertThat(fixture.recalculations).isEmpty();
    }

    @Test
    @DisplayName("100 holders changing in the same tick stay well inside the 5 ms subsystem budget")
    void hundredRecalculationsInOneTick() {
        EngineFixture fixture = new EngineFixture();
        List<UUID> holders = new ArrayList<>();

        for (int i = 0; i < 100; i++) {
            UUID playerId = UUID.randomUUID();
            fixture.engine.createForCharacter(playerId, UUID.randomUUID(), new ResourcePool(0.0, 0.0));
            for (int s = 0; s < 20; s++) {
                fixture.engine.apply(playerId, slot("source" + s, 1.0));
            }
            holders.add(playerId);
        }
        fixture.tick();

        // Warm-up, so the measurement is not dominated by class loading and a cold JIT. The value
        // changes every round on purpose - an identical set would be recognised as unchanged and
        // recalculate nothing, warming up nothing.
        for (int round = 1; round <= 50; round++) {
            double value = round;
            holders.forEach(
                    h ->
                            fixture.engine.apply(
                                    h,
                                    EngineFixture.buff(
                                            "pulse", StatModifier.flat(Attribute.HEALTH, value))));
            fixture.tick();
        }

        holders.forEach(
                h ->
                        fixture.engine.apply(
                                h,
                                EngineFixture.buff(
                                        "measured", StatModifier.flat(Attribute.HEALTH, 3.0))));
        long startedAt = System.nanoTime();
        fixture.tick();
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;

        assertThat(elapsedMillis).isLessThan(5L);
    }
}
