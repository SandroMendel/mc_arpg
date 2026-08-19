package rpg.core.stats;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T027, T028: putting a source on and taking it off again lands exactly where it started
 * (FR-016, FR-017, SC-004).
 *
 * <p>Everything here compares with exact equality. A tolerance would pass even if every round trip
 * left a small residue behind, which is the failure this test exists to catch - it does not show up
 * in one round trip, it shows up after a play session.
 */
class ModifierRoundTripTest {

    private static final ModifierSet CHEST =
            EngineFixture.equipment(
                    "slot:CHEST",
                    StatModifier.flat(Attribute.HEALTH, 250.0),
                    StatModifier.percent(Attribute.HEALTH, 0.15),
                    StatModifier.flat(Attribute.DEFENSE, 42.5),
                    StatModifier.percent(Attribute.PHYSICAL_DAMAGE, 0.07),
                    StatModifier.flat(Attribute.ABILITY_COOLDOWN, 0.05));

    private static double[] valuesOf(StatSnapshot snapshot) {
        double[] values = new double[Attribute.count()];
        for (Attribute attribute : Attribute.all()) {
            values[attribute.ordinal()] = snapshot.get(attribute);
        }
        return values;
    }

    @Test
    @DisplayName("one round trip restores every value exactly")
    void singleRoundTrip() {
        EngineFixture fixture = new EngineFixture();
        UUID holder = fixture.character();

        double[] before = valuesOf(fixture.engine.snapshot(holder));

        fixture.engine.apply(holder, CHEST);
        fixture.tick();
        assertThat(fixture.engine.value(holder, Attribute.HEALTH)).isGreaterThan(before[Attribute.HEALTH.ordinal()]);

        fixture.engine.remove(holder, CHEST.source());
        fixture.tick();

        assertThat(valuesOf(fixture.engine.snapshot(holder))).isEqualTo(before);
    }

    @Test
    @DisplayName("a thousand round trips leave no residue")
    void thousandRoundTrips() {
        EngineFixture fixture = new EngineFixture();
        UUID holder = fixture.character();

        double[] before = valuesOf(fixture.engine.snapshot(holder));

        for (int i = 0; i < 1000; i++) {
            fixture.engine.apply(holder, CHEST);
            fixture.tick();
            fixture.engine.remove(holder, CHEST.source());
            fixture.tick();
        }

        assertThat(valuesOf(fixture.engine.snapshot(holder))).isEqualTo(before);
    }

    @Test
    @DisplayName("the same sources in a different order produce bit-identical values")
    void orderIndependence() {
        List<ModifierSet> sets =
                new ArrayList<>(
                        List.of(
                                CHEST,
                                EngineFixture.equipment(
                                        "slot:HELMET",
                                        StatModifier.flat(Attribute.HEALTH, 33.3),
                                        StatModifier.percent(Attribute.HEALTH, 0.031)),
                                EngineFixture.buff(
                                        "might",
                                        StatModifier.percent(Attribute.PHYSICAL_DAMAGE, 0.13),
                                        StatModifier.flat(Attribute.HEALTH, 7.7)),
                                ModifierSet.of(
                                        SourceId.of(SourceKind.ZONE, "swamp"),
                                        StatModifier.percent(Attribute.MOVEMENT_SPEED, -0.11)),
                                ModifierSet.of(
                                        SourceId.of(SourceKind.LEVEL, "level"),
                                        StatModifier.flat(Attribute.HEALTH, 12.34))));

        EngineFixture reference = new EngineFixture();
        UUID a = reference.character();
        sets.forEach(set -> reference.engine.apply(a, set));
        reference.tick();
        double[] expected = valuesOf(reference.engine.snapshot(a));

        // Five shuffles, each applied to a fresh holder. Every one must land on the same bits.
        for (int seed = 0; seed < 5; seed++) {
            Collections.shuffle(sets, new java.util.Random(seed));

            EngineFixture fixture = new EngineFixture();
            UUID b = fixture.character();
            sets.forEach(set -> fixture.engine.apply(b, set));
            fixture.tick();

            assertThat(valuesOf(fixture.engine.snapshot(b))).as("shuffle seed " + seed).isEqualTo(expected);
        }
    }

    @Test
    @DisplayName("re-applying an identical set changes nothing and schedules nothing")
    void reapplyingIdenticalSetIsFree() {
        EngineFixture fixture = new EngineFixture();
        UUID holder = fixture.character();

        fixture.engine.apply(holder, CHEST);
        fixture.tick();
        fixture.clearRecorded();

        fixture.engine.apply(holder, CHEST);

        assertThat(fixture.scheduler.scheduledCount()).isZero();
    }
}
