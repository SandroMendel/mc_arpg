package rpg.core.stats;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** T085: a reloaded configuration takes effect, and a rejected one changes nothing (User Story 7). */
class StatConfigReloadTest {

    private static StatConfig withHealthMax(double max) {
        var definitions =
                new java.util.EnumMap<Attribute, AttributeDefinition>(
                        StatConfig.defaults().definitions());
        definitions.put(Attribute.HEALTH, new AttributeDefinition(Attribute.HEALTH, 100.0, 1.0, max, 0.0));
        return new StatConfig(definitions);
    }

    @Test
    @DisplayName("a reload marks every holder, so new numbers actually take effect")
    void reloadMarksEveryHolder() {
        EngineFixture fixture = new EngineFixture();

        List<UUID> holders = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            UUID playerId = UUID.randomUUID();
            fixture.engine.createForCharacter(playerId, UUID.randomUUID(), new ResourcePool(0.0, 0.0));
            fixture.engine.recalculateNow(playerId);
            fixture.engine.apply(
                    playerId,
                    EngineFixture.equipment(
                            "slot:CHEST", StatModifier.flat(Attribute.HEALTH, 5000.0)));
            holders.add(playerId);
        }
        fixture.tick();
        holders.forEach(
                holder -> assertThat(fixture.engine.value(holder, Attribute.HEALTH)).isEqualTo(2000.0));
        fixture.clearRecorded();

        fixture.engine.reload(withHealthMax(5000.0));
        fixture.tick();

        // 100 base + 5000 flat = 5100, still clamped - but by the new ceiling, not the old one.
        holders.forEach(
                holder -> assertThat(fixture.engine.value(holder, Attribute.HEALTH)).isEqualTo(5000.0));
        assertThat(fixture.recalculations).hasSize(5);
    }

    @Test
    @DisplayName("the reload is the one place that walks all holders - and only on demand")
    void reloadIsTheOnlySweep() {
        EngineFixture fixture = new EngineFixture();
        for (int i = 0; i < 50; i++) {
            UUID playerId = UUID.randomUUID();
            fixture.engine.createForCharacter(playerId, UUID.randomUUID(), new ResourcePool(0.0, 0.0));
            fixture.engine.recalculateNow(playerId);
        }
        fixture.clearRecorded();

        // Nothing happens without a reload: idle ticks stay free.
        for (int tick = 0; tick < 100; tick++) {
            fixture.tick();
        }
        assertThat(fixture.scheduler.scheduledCount()).isZero();

        fixture.engine.reload(withHealthMax(3000.0));
        assertThat(fixture.scheduler.scheduledCount()).isEqualTo(50);
    }

    @Test
    @DisplayName("an invalid configuration is refused before it can reach the engine")
    void invalidConfigurationNeverArrives() {
        // B01's loader keeps the previously valid document on a rejected reload, so a broken file
        // never reaches reload() at all. What this asserts is the layer below: such a config cannot
        // even be constructed.
        var definitions =
                new java.util.EnumMap<Attribute, AttributeDefinition>(
                        StatConfig.defaults().definitions());
        definitions.remove(Attribute.MANA);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new StatConfig(definitions))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mana");
    }

    @Test
    @DisplayName("after a reload the old snapshots are still readable")
    void oldSnapshotsStayValid() {
        EngineFixture fixture = new EngineFixture();
        UUID holder = fixture.character();

        StatSnapshot before = fixture.engine.snapshot(holder);
        fixture.engine.reload(withHealthMax(5000.0));
        fixture.tick();

        assertThat(before.get(Attribute.HEALTH)).isEqualTo(100.0);
        assertThat(fixture.engine.snapshot(holder).revision()).isGreaterThan(before.revision());
    }
}
