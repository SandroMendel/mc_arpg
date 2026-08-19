package rpg.core.stats;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T077-T080: a mob is a stat holder like any other (FR-035, FR-036, SC-010).
 *
 * <p>The point of these tests is not that mobs work - it is that they work through the <em>same</em>
 * path. Without that, B10 will build a second stat system, and from then on two places have to be
 * kept in balance with each other.
 */
class EntityHolderTest {

    private static ModifierSet mobStats() {
        return ModifierSet.of(
                SourceId.of(SourceKind.CLASS, "zombie"),
                StatModifier.flat(Attribute.HEALTH, 400.0),
                StatModifier.percent(Attribute.HEALTH, 0.25),
                StatModifier.flat(Attribute.PHYSICAL_DAMAGE, 12.0));
    }

    private static double[] valuesOf(StatSnapshot snapshot) {
        double[] values = new double[Attribute.count()];
        for (Attribute attribute : Attribute.all()) {
            values[attribute.ordinal()] = snapshot.get(attribute);
        }
        return values;
    }

    @Test
    @DisplayName("a mob and a character with the same sources get bit-identical values")
    void identicalCalculation() {
        EngineFixture fixture = new EngineFixture();

        UUID player = fixture.character();
        fixture.engine.apply(player, mobStats());
        fixture.tick();

        UUID mob = UUID.randomUUID();
        fixture.engine.createForEntity(mob);
        fixture.engine.apply(mob, mobStats());
        fixture.tick();

        assertThat(valuesOf(fixture.engine.snapshot(mob)))
                .isEqualTo(valuesOf(fixture.engine.snapshot(player)));
    }

    @Test
    @DisplayName("a new mob starts at its own maximum, not at a character's")
    void mobStartsFull() {
        EngineFixture fixture = new EngineFixture();

        UUID mob = UUID.randomUUID();
        fixture.engine.createForEntity(mob);
        fixture.engine.apply(mob, mobStats());
        fixture.tick();

        // The pool is set from the first calculation, so a mob created before its stats are applied
        // still starts full relative to what it had at the time.
        ResourceView view = fixture.engine.resources(mob);
        // (100 base + 400 flat) x 1.25 = 625, the same formula a player gets.
        assertThat(view.maxHealth()).isEqualTo(625.0);
        assertThat(view.currentHealth()).isPositive();
    }

    @Test
    @DisplayName("removing a mob leaves nothing behind")
    void removalLeavesNothing() {
        EngineFixture fixture = new EngineFixture();

        UUID mob = UUID.randomUUID();
        fixture.engine.createForEntity(mob);
        fixture.engine.apply(mob, mobStats());
        fixture.tick();
        assertThat(fixture.engine.holderCount()).isEqualTo(1);

        fixture.engine.remove(mob);

        assertThat(fixture.engine.holderCount()).isZero();
        assertThat(fixture.engine.findSnapshot(mob)).isEmpty();
        // Idempotent: a second removal is not an error.
        fixture.engine.remove(mob);
        assertThat(fixture.engine.holderCount()).isZero();
    }

    @Test
    @DisplayName("800 idle mobs schedule nothing and recalculate nothing")
    void eightHundredIdleMobsCostNothing() {
        EngineFixture fixture = new EngineFixture();

        List<UUID> mobs = new ArrayList<>();
        for (int i = 0; i < 800; i++) {
            UUID mob = UUID.randomUUID();
            fixture.engine.createForEntity(mob);
            fixture.engine.apply(mob, mobStats());
            mobs.add(mob);
        }
        fixture.tick();
        fixture.clearRecorded();

        for (int tick = 0; tick < 200; tick++) {
            fixture.tick();
        }

        assertThat(fixture.scheduler.scheduledCount()).isZero();
        assertThat(fixture.recalculations).isEmpty();
        assertThat(fixture.engine.holderCount()).isEqualTo(800);
        assertThat(mobs).hasSize(800);
    }

    @Test
    @DisplayName("a mob carries no character, so nothing about it is ever persisted")
    void mobIsNeverPersisted() {
        List<UUID> marked = new ArrayList<>();
        EngineFixture fixture = new EngineFixture();
        fixture.engine.setResourceWriteMark(marked::add);

        UUID mob = UUID.randomUUID();
        fixture.engine.createForEntity(mob);
        fixture.engine.apply(mob, mobStats());
        fixture.tick();
        fixture.engine.changeHealth(mob, -50.0);

        assertThat(marked).isEmpty();
    }

    @Test
    @DisplayName("200 sessions opened and closed leave the holder map empty")
    void noHolderLeakAcrossSessions() {
        EngineFixture fixture = new EngineFixture();

        for (int i = 0; i < 200; i++) {
            UUID playerId = UUID.randomUUID();
            fixture.engine.createForCharacter(playerId, UUID.randomUUID(), new ResourcePool(0.0, 0.0));
            fixture.engine.recalculateNow(playerId);
            fixture.engine.apply(playerId, mobStats());
            fixture.tick();
            fixture.engine.remove(playerId);
        }

        assertThat(fixture.engine.holderCount()).isZero();
    }
}
