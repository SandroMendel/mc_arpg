package rpg.core.stats;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T053a: the mirror runs before the event is published (FR-023, FR-032, contracts/events.md).
 *
 * <p>Lives in {@code rpg-core} rather than {@code rpg-platform} as tasks.md suggested: the ordering
 * is a property of the engine, and the bridge is an interface, so a recording double proves it
 * without a server. Principle VII prefers that over the same assertion made through MockBukkit.
 *
 * <p>What the order buys: a HUD subscriber that reads the health bar when it hears about a
 * recalculation never sees a bar that has not caught up yet. The other order would produce a
 * one-tick flicker that is very hard to attribute to anything.
 */
class MirrorBeforeEventTest {

    private static final class RecordingBridge implements VanillaAttributeBridge {
        private final List<String> log;

        RecordingBridge(List<String> log) {
            this.log = log;
        }

        @Override
        public void mirrorHealth(UUID holderId, double currentHealth, double maxHealth) {
            log.add("mirror:health");
        }

        @Override
        public void mirrorAttackSpeed(UUID holderId, double value) {
            log.add("mirror:attackSpeed");
        }

        @Override
        public void mirrorMovementSpeed(UUID holderId, double value) {
            log.add("mirror:movementSpeed");
        }
    }

    @Test
    @DisplayName("every mirror call happens before the recalculation event is published")
    void mirrorComesFirst() {
        List<String> log = new ArrayList<>();
        EngineFixture fixture = new EngineFixture();
        fixture.engine.registerVanillaBridge(new RecordingBridge(log));
        fixture.eventBus.subscribe(StatsRecalculatedEvent.class, event -> log.add("event"));

        UUID holder = fixture.character();
        log.clear();

        fixture.engine.apply(
                holder,
                EngineFixture.equipment(
                        "slot:BOOTS",
                        StatModifier.flat(Attribute.HEALTH, 100.0),
                        StatModifier.percent(Attribute.MOVEMENT_SPEED, 0.2)));
        fixture.tick();

        assertThat(log).contains("mirror:health", "mirror:movementSpeed", "event");
        assertThat(log.indexOf("event")).isEqualTo(log.size() - 1);
    }

    @Test
    @DisplayName("without a registered bridge the engine simply does not mirror")
    void noBridgeIsNotAnError() {
        EngineFixture fixture = new EngineFixture();
        UUID holder = fixture.character();

        fixture.engine.apply(
                holder, EngineFixture.buff("might", StatModifier.flat(Attribute.HEALTH, 10.0)));
        fixture.tick();

        assertThat(fixture.engine.value(holder, Attribute.HEALTH)).isEqualTo(110.0);
        assertThat(fixture.recalculations).hasSize(1);
    }

    @Test
    @DisplayName("speeds are only mirrored when they actually changed")
    void speedsAreNotMirroredWithoutChange() {
        List<String> log = new ArrayList<>();
        EngineFixture fixture = new EngineFixture();
        fixture.engine.registerVanillaBridge(new RecordingBridge(log));

        UUID holder = fixture.character();
        log.clear();

        // Health only: no reason to write the speed attributes, which would be two Bukkit calls per
        // recalculation for nothing.
        fixture.engine.apply(
                holder, EngineFixture.buff("vitality", StatModifier.flat(Attribute.HEALTH, 10.0)));
        fixture.tick();

        assertThat(log).containsExactly("mirror:health");
    }
}
