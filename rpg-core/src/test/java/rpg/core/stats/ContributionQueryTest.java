package rpg.core.stats;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** T030: tracing where a value comes from, without computing anything (FR-010). */
class ContributionQueryTest {

    @Test
    @DisplayName("every contributing source is named with its own contribution")
    void listsEveryContributor() {
        EngineFixture fixture = new EngineFixture();
        UUID holder = fixture.character();

        fixture.engine.apply(
                holder,
                EngineFixture.equipment(
                        "slot:CHEST",
                        StatModifier.flat(Attribute.HEALTH, 250.0),
                        StatModifier.percent(Attribute.HEALTH, 0.15)));
        fixture.engine.apply(
                holder, EngineFixture.buff("vitality", StatModifier.flat(Attribute.HEALTH, 40.0)));
        fixture.engine.apply(
                holder, EngineFixture.equipment("slot:LEGS", StatModifier.flat(Attribute.DEFENSE, 12.0)));
        fixture.tick();

        List<AttributeContribution> health = fixture.engine.contributions(holder, Attribute.HEALTH);

        assertThat(health).hasSize(3);
        assertThat(health)
                .extracting(AttributeContribution::source)
                .containsExactly(
                        SourceId.of(SourceKind.EQUIPMENT, "slot:CHEST"),
                        SourceId.of(SourceKind.EQUIPMENT, "slot:CHEST"),
                        SourceId.of(SourceKind.BUFF, "vitality"));
        assertThat(health)
                .extracting(AttributeContribution::value)
                .containsExactly(250.0, 0.15, 40.0);
    }

    @Test
    @DisplayName("contributions to other attributes are not listed")
    void filtersByAttribute() {
        EngineFixture fixture = new EngineFixture();
        UUID holder = fixture.character();

        fixture.engine.apply(
                holder, EngineFixture.equipment("slot:LEGS", StatModifier.flat(Attribute.DEFENSE, 12.0)));
        fixture.tick();

        assertThat(fixture.engine.contributions(holder, Attribute.MANA)).isEmpty();
        assertThat(fixture.engine.contributions(holder, Attribute.DEFENSE)).hasSize(1);
    }

    @Test
    @DisplayName("querying triggers no recalculation")
    void queryIsFree() {
        EngineFixture fixture = new EngineFixture();
        UUID holder = fixture.character();

        fixture.engine.apply(
                holder, EngineFixture.equipment("slot:CHEST", StatModifier.flat(Attribute.HEALTH, 10.0)));
        fixture.tick();
        fixture.clearRecorded();

        fixture.engine.contributions(holder, Attribute.HEALTH);

        assertThat(fixture.scheduler.scheduledCount()).isZero();
        assertThat(fixture.recalculations).isEmpty();
    }

    @Test
    @DisplayName("the listing follows the summation order, not the order things were applied")
    void deterministicOrder() {
        EngineFixture fixture = new EngineFixture();
        UUID holder = fixture.character();

        // Applied buff-first, but CLASS and LEVEL sort before EQUIPMENT, which sorts before BUFF.
        fixture.engine.apply(
                holder, EngineFixture.buff("might", StatModifier.flat(Attribute.PHYSICAL_DAMAGE, 1.0)));
        fixture.engine.apply(
                holder,
                ModifierSet.of(
                        SourceId.of(SourceKind.LEVEL, "level"),
                        StatModifier.flat(Attribute.PHYSICAL_DAMAGE, 2.0)));
        fixture.engine.apply(
                holder,
                ModifierSet.of(
                        SourceId.of(SourceKind.CLASS, "warrior"),
                        StatModifier.flat(Attribute.PHYSICAL_DAMAGE, 3.0)));
        fixture.tick();

        assertThat(fixture.engine.contributions(holder, Attribute.PHYSICAL_DAMAGE))
                .extracting(AttributeContribution::value)
                .containsExactly(3.0, 2.0, 1.0);
    }
}
