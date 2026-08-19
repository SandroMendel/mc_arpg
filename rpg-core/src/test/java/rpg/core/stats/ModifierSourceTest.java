package rpg.core.stats;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** T029: source bookkeeping - replace, remove, remove-unknown (FR-007, FR-008, FR-018). */
class ModifierSourceTest {

    @Test
    @DisplayName("removing one source leaves the others fully in effect")
    void removingOneLeavesTheRest() {
        EngineFixture fixture = new EngineFixture();
        UUID holder = fixture.character();

        fixture.engine.apply(
                holder, EngineFixture.equipment("slot:CHEST", StatModifier.flat(Attribute.DEFENSE, 40.0)));
        fixture.engine.apply(
                holder, EngineFixture.equipment("slot:LEGS", StatModifier.flat(Attribute.DEFENSE, 25.0)));
        fixture.engine.apply(
                holder, EngineFixture.buff("stoneskin", StatModifier.flat(Attribute.DEFENSE, 10.0)));
        fixture.tick();
        assertThat(fixture.engine.value(holder, Attribute.DEFENSE)).isEqualTo(75.0);

        fixture.engine.remove(holder, SourceId.of(SourceKind.EQUIPMENT, "slot:LEGS"));
        fixture.tick();

        assertThat(fixture.engine.value(holder, Attribute.DEFENSE)).isEqualTo(50.0);
    }

    @Test
    @DisplayName("re-registering a source id replaces its set instead of adding to it")
    void reRegisteringReplaces() {
        EngineFixture fixture = new EngineFixture();
        UUID holder = fixture.character();

        fixture.engine.apply(
                holder, EngineFixture.equipment("slot:CHEST", StatModifier.flat(Attribute.DEFENSE, 40.0)));
        fixture.tick();
        assertThat(fixture.engine.value(holder, Attribute.DEFENSE)).isEqualTo(40.0);

        fixture.engine.apply(
                holder, EngineFixture.equipment("slot:CHEST", StatModifier.flat(Attribute.DEFENSE, 15.0)));
        fixture.tick();

        assertThat(fixture.engine.value(holder, Attribute.DEFENSE)).isEqualTo(15.0);
    }

    @Test
    @DisplayName("removing a source that is not there does nothing and schedules nothing")
    void removingUnknownSourceIsFree() {
        EngineFixture fixture = new EngineFixture();
        UUID holder = fixture.character();

        fixture.engine.remove(holder, SourceId.of(SourceKind.BUFF, "never-applied"));

        assertThat(fixture.scheduler.scheduledCount()).isZero();
        assertThat(fixture.recalculations).isEmpty();
    }

    @Test
    @DisplayName("removeKind drops every source of that kind and no others")
    void removeKind() {
        EngineFixture fixture = new EngineFixture();
        UUID holder = fixture.character();

        fixture.engine.apply(
                holder, EngineFixture.equipment("slot:CHEST", StatModifier.flat(Attribute.DEFENSE, 40.0)));
        fixture.engine.apply(
                holder, EngineFixture.equipment("slot:LEGS", StatModifier.flat(Attribute.DEFENSE, 25.0)));
        fixture.engine.apply(
                holder, EngineFixture.buff("stoneskin", StatModifier.flat(Attribute.DEFENSE, 10.0)));
        fixture.tick();

        fixture.engine.removeKind(holder, SourceKind.EQUIPMENT);
        fixture.tick();

        assertThat(fixture.engine.value(holder, Attribute.DEFENSE)).isEqualTo(10.0);
    }

    @Test
    @DisplayName("an empty set is accepted and contributes nothing")
    void emptySetIsLegal() {
        EngineFixture fixture = new EngineFixture();
        UUID holder = fixture.character();

        fixture.engine.apply(holder, ModifierSet.empty(SourceId.of(SourceKind.ZONE, "hub")));
        fixture.tick();

        assertThat(fixture.engine.value(holder, Attribute.HEALTH)).isEqualTo(100.0);
    }

    @Test
    @DisplayName("operations on an unknown holder do not throw")
    void unknownHolderIsTolerated() {
        EngineFixture fixture = new EngineFixture();
        UUID stranger = UUID.randomUUID();

        fixture.engine.remove(stranger, SourceId.of(SourceKind.BUFF, "x"));
        fixture.engine.removeKind(stranger, SourceKind.BUFF);
        fixture.engine.remove(stranger);

        assertThat(fixture.engine.findSnapshot(stranger)).isEmpty();
    }
}
