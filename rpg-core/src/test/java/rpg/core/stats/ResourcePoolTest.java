package rpg.core.stats;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** T058, T059: the resource container and its clamping rules (FR-025 to FR-027, FR-029). */
class ResourcePoolTest {

    private static ModifierSet health(double flat) {
        return EngineFixture.equipment("slot:CHEST", StatModifier.flat(Attribute.HEALTH, flat));
    }

    @Test
    @DisplayName("a rising maximum leaves the current value alone - gear is not a heal")
    void risingMaximumDoesNotHeal() {
        EngineFixture fixture = new EngineFixture();
        UUID holder = fixture.character();
        fixture.engine.apply(holder, health(900.0)); // max 1000
        fixture.tick();
        fixture.engine.restoreResources(holder, new ResourcePool(500.0, 0.0));
        fixture.clearRecorded();

        fixture.engine.apply(holder, health(1100.0)); // max 1200
        fixture.tick();

        ResourceView view = fixture.engine.resources(holder);
        assertThat(view.maxHealth()).isEqualTo(1200.0);
        assertThat(view.currentHealth()).isEqualTo(500.0);
    }

    @Test
    @DisplayName("a falling maximum pulls the value down with it, and nobody dies of it")
    void fallingMaximumClamps() {
        EngineFixture fixture = new EngineFixture();
        UUID holder = fixture.character();
        fixture.engine.apply(holder, health(900.0)); // max 1000
        fixture.tick();
        fixture.engine.restoreResources(holder, new ResourcePool(900.0, 0.0));
        fixture.clearRecorded();

        fixture.engine.apply(holder, health(700.0)); // max 800
        fixture.tick();

        ResourceView view = fixture.engine.resources(holder);
        assertThat(view.maxHealth()).isEqualTo(800.0);
        assertThat(view.currentHealth()).isEqualTo(800.0);
        assertThat(view.currentHealth()).isNotZero(); // clamping is not a death

        assertThat(fixture.resourceChanges)
                .filteredOn(e -> e.kind() == ResourceKind.HEALTH)
                .singleElement()
                .satisfies(
                        event -> {
                            assertThat(event.cause()).isEqualTo(ChangeCause.CLAMPED_BY_MAX);
                            assertThat(event.previous()).isEqualTo(900.0);
                            assertThat(event.current()).isEqualTo(800.0);
                        });
    }

    @Test
    @DisplayName("spending more than there is leaves zero, never a negative value")
    void cannotGoNegative() {
        EngineFixture fixture = new EngineFixture();
        UUID holder = fixture.character();
        fixture.engine.restoreResources(holder, new ResourcePool(10.0, 10.0));
        fixture.clearRecorded();

        assertThat(fixture.engine.changeHealth(holder, -50.0)).isEqualTo(0.0);
        assertThat(fixture.engine.changeMana(holder, -50.0)).isEqualTo(0.0);
    }

    @Test
    @DisplayName("gaining more than the maximum stops at the maximum")
    void cannotExceedMaximum() {
        EngineFixture fixture = new EngineFixture();
        UUID holder = fixture.character();
        fixture.engine.restoreResources(holder, new ResourcePool(10.0, 10.0));

        assertThat(fixture.engine.changeHealth(holder, 9999.0)).isEqualTo(100.0);
        assertThat(fixture.engine.changeMana(holder, 9999.0)).isEqualTo(50.0);
    }

    @Test
    @DisplayName("a fresh holder starts at its maxima")
    void freshHolderStartsFull() {
        EngineFixture fixture = new EngineFixture();
        UUID playerId = UUID.randomUUID();
        fixture.engine.createForCharacter(playerId, UUID.randomUUID(), new ResourcePool(0.0, 0.0));
        StatSnapshot snapshot = fixture.engine.recalculateNow(playerId);

        fixture.engine.restoreResources(
                playerId,
                ResourcePool.full(snapshot.get(Attribute.HEALTH), snapshot.get(Attribute.MANA)));

        ResourceView view = fixture.engine.resources(playerId);
        assertThat(view.currentHealth()).isEqualTo(view.maxHealth());
        assertThat(view.currentMana()).isEqualTo(view.maxMana());
        assertThat(fixture.resourceChanges)
                .allSatisfy(event -> assertThat(event.cause()).isEqualTo(ChangeCause.INITIALISED));
    }

    @Test
    @DisplayName("a change of zero publishes nothing")
    void noEventForANonChange() {
        EngineFixture fixture = new EngineFixture();
        UUID holder = fixture.character();
        fixture.engine.restoreResources(holder, new ResourcePool(100.0, 0.0));
        fixture.clearRecorded();

        fixture.engine.changeMana(holder, -5.0); // already empty

        assertThat(fixture.resourceChanges).isEmpty();
    }

    @Test
    @DisplayName("running out of health is reported, not acted upon")
    void depletionIsReportedOnly() {
        EngineFixture fixture = new EngineFixture();
        UUID holder = fixture.character();
        fixture.engine.restoreResources(holder, new ResourcePool(30.0, 0.0));
        fixture.clearRecorded();

        fixture.engine.changeHealth(holder, -30.0);

        assertThat(fixture.resourceChanges).singleElement().satisfies(event -> {
            assertThat(event.isDepleted()).isTrue();
            assertThat(event.cause()).isEqualTo(ChangeCause.DELTA);
        });
        // The holder is still there. Dying is B05's decision.
        assertThat(fixture.engine.findSnapshot(holder)).isPresent();
    }

    @Test
    @DisplayName("the pool itself refuses nonsense")
    void poolInvariants() {
        assertThat(new ResourcePool(5.0, 3.0).clampedTo(10.0, 10.0))
                .isEqualTo(new ResourcePool(5.0, 3.0));
        assertThat(new ResourcePool(5.0, 3.0).clampedTo(2.0, 1.0))
                .isEqualTo(new ResourcePool(2.0, 1.0));
        assertThat(ResourcePool.full(100.0, 50.0)).isEqualTo(new ResourcePool(100.0, 50.0));
        assertThat(new ResourcePool(0.0, 0.0).isDepleted()).isTrue();
    }

    @Test
    @DisplayName("a non-finite delta is refused rather than poisoning the pool")
    void nonFiniteDeltaRefused() {
        EngineFixture fixture = new EngineFixture();
        UUID holder = fixture.character();

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> fixture.engine.changeHealth(holder, Double.NaN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("finite");
    }
}
