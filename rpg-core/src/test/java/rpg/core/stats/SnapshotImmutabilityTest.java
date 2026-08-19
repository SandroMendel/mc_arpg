package rpg.core.stats;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** T040: a snapshot taken is a snapshot kept (FR-020, FR-021). */
class SnapshotImmutabilityTest {

    @Test
    @DisplayName("a snapshot taken before a change still reports the old values")
    void snapshotSurvivesChange() {
        EngineFixture fixture = new EngineFixture();
        UUID holder = fixture.character();

        StatSnapshot inFlight = fixture.engine.snapshot(holder);
        assertThat(inFlight.get(Attribute.HEALTH)).isEqualTo(100.0);

        fixture.engine.apply(
                holder,
                EngineFixture.equipment("slot:CHEST", StatModifier.flat(Attribute.HEALTH, 500.0)));
        fixture.tick();

        assertThat(inFlight.get(Attribute.HEALTH)).isEqualTo(100.0);
        assertThat(fixture.engine.value(holder, Attribute.HEALTH)).isEqualTo(600.0);
    }

    @Test
    @DisplayName("a snapshot outlives its holder")
    void snapshotSurvivesRemoval() {
        EngineFixture fixture = new EngineFixture();
        UUID holder = fixture.character();

        StatSnapshot inFlight = fixture.engine.snapshot(holder);
        fixture.engine.remove(holder);

        assertThat(fixture.engine.findSnapshot(holder)).isEmpty();
        assertThat(inFlight.get(Attribute.HEALTH)).isEqualTo(100.0);
    }

    @Test
    @DisplayName("the internal value array is never handed out")
    void arrayIsNotExposed() {
        // Nothing on the public surface returns a double[]. An exposed array would make
        // immutability a promise any caller could break by accident, and the resulting bug would
        // look like a balancing problem rather than a mutation.
        assertThat(Arrays.stream(StatSnapshot.class.getMethods()).map(Method::getReturnType))
                .noneMatch(type -> type.isArray());
    }

    @Test
    @DisplayName("the constructor copies its input, so a later change to the caller's array is ignored")
    void constructorCopies() {
        double[] values = new double[Attribute.count()];
        values[Attribute.HEALTH.ordinal()] = 100.0;

        StatSnapshot snapshot = new StatSnapshot(values, 1L);
        values[Attribute.HEALTH.ordinal()] = 9999.0;

        assertThat(snapshot.get(Attribute.HEALTH)).isEqualTo(100.0);
    }

    @Test
    @DisplayName("revisions increase strictly, so a consumer can detect change without comparing values")
    void revisionsIncrease() {
        EngineFixture fixture = new EngineFixture();
        UUID holder = fixture.character();

        StatSnapshot first = fixture.engine.snapshot(holder);
        fixture.engine.apply(
                holder, EngineFixture.buff("might", StatModifier.flat(Attribute.PHYSICAL_DAMAGE, 5.0)));
        fixture.tick();
        StatSnapshot second = fixture.engine.snapshot(holder);

        assertThat(second.revision()).isGreaterThan(first.revision());
        assertThat(second.isNewerThan(first)).isTrue();
        assertThat(first.isNewerThan(second)).isFalse();
        assertThat(first.isNewerThan(null)).isTrue();
    }
}
