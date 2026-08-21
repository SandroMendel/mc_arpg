package rpg.core.combat;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** T081-T084: hits become one number for B13 to draw (FR-038, FR-040, SC-009). */
class DamageAggregatorTest {

    @Test
    @DisplayName("twenty hits inside the window become one event with the correct sum")
    void twentyHitsOneEvent() {
        CombatFixture.TestClock clock = new CombatFixture.TestClock();
        DamageAggregator aggregator = new DamageAggregator(clock, Duration.ofMillis(500));
        UUID attacker = UUID.randomUUID();
        UUID target = UUID.randomUUID();

        for (int i = 0; i < 20; i++) {
            assertThat(aggregator.record(attacker, target, DamageType.PHYSICAL, 5.0)).isNull();
            clock.advanceMillis(10);
        }

        var events = aggregator.closeFor(target);

        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.hitCount()).isEqualTo(20);
            assertThat(event.totalDamage()).isEqualTo(100.0);
            assertThat(event.lethal()).isTrue();
        });
    }

    @Test
    @DisplayName("an idle window closes on its own once its time is up")
    void anIdleWindowClosesByItself() {
        // The gap this covers was visible in play: hitting a mob and walking away published nothing at
        // all, because a window only closed when the NEXT hit arrived after it expired. Everything that
        // listens - the target readout, and later statistics - never heard about those hits.
        CombatFixture.TestClock clock = new CombatFixture.TestClock();
        DamageAggregator aggregator = new DamageAggregator(clock, Duration.ofMillis(500));
        UUID attacker = UUID.randomUUID();
        UUID target = UUID.randomUUID();

        aggregator.record(attacker, target, DamageType.PHYSICAL, 7.0);
        aggregator.record(attacker, target, DamageType.PHYSICAL, 8.0);
        clock.advanceMillis(600);

        var events = aggregator.closeExpired();

        assertThat(events)
                .singleElement()
                .satisfies(
                        event -> {
                            assertThat(event.hitCount()).isEqualTo(2);
                            assertThat(event.totalDamage()).isEqualTo(15.0);
                            assertThat(event.lethal())
                                    .as("a window running out is not something dying")
                                    .isFalse();
                        });
        assertThat(aggregator.openWindowCount()).as("and it is gone, not left open").isZero();
    }

    @Test
    @DisplayName("ein Fenster, dessen Zeit noch läuft, bleibt beim Sweep unberührt")
    void anOpenWindowSurvivesTheSweep() {
        CombatFixture.TestClock clock = new CombatFixture.TestClock();
        DamageAggregator aggregator = new DamageAggregator(clock, Duration.ofMillis(500));
        UUID attacker = UUID.randomUUID();
        UUID target = UUID.randomUUID();

        aggregator.record(attacker, target, DamageType.PHYSICAL, 7.0);
        clock.advanceMillis(100);

        assertThat(aggregator.closeExpired()).isEmpty();
        assertThat(aggregator.openWindowCount())
                .as("sonst würde jeder Sweep die Bündelung zerschlagen")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("a hit after the window closes the previous one and starts a new one")
    void windowRollsOver() {
        CombatFixture.TestClock clock = new CombatFixture.TestClock();
        DamageAggregator aggregator = new DamageAggregator(clock, Duration.ofMillis(500));
        UUID attacker = UUID.randomUUID();
        UUID target = UUID.randomUUID();

        aggregator.record(attacker, target, DamageType.PHYSICAL, 10.0);
        aggregator.record(attacker, target, DamageType.PHYSICAL, 10.0);
        clock.advanceMillis(600);

        DamageDealtEvent closed = aggregator.record(attacker, target, DamageType.PHYSICAL, 3.0);

        assertThat(closed).isNotNull();
        assertThat(closed.hitCount()).isEqualTo(2);
        assertThat(closed.totalDamage()).isEqualTo(20.0);
        assertThat(closed.lethal()).isFalse();
    }

    @Test
    @DisplayName("a hit that did nothing produces no event at all")
    void zeroDamageIsNotAnEvent() {
        CombatFixture.TestClock clock = new CombatFixture.TestClock();
        DamageAggregator aggregator = new DamageAggregator(clock, Duration.ofMillis(500));
        UUID target = UUID.randomUUID();

        assertThat(aggregator.record(UUID.randomUUID(), target, DamageType.PHYSICAL, 0.0)).isNull();
        assertThat(aggregator.closeFor(target)).isEmpty();
        assertThat(aggregator.openWindowCount()).isZero();
    }

    @Test
    @DisplayName("different attackers on the same target are not merged")
    void perAttackerTargetPair() {
        CombatFixture.TestClock clock = new CombatFixture.TestClock();
        DamageAggregator aggregator = new DamageAggregator(clock, Duration.ofMillis(500));
        UUID target = UUID.randomUUID();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();

        aggregator.record(a, target, DamageType.PHYSICAL, 10.0);
        aggregator.record(b, target, DamageType.PHYSICAL, 4.0);

        assertThat(aggregator.openWindowCount()).isEqualTo(2);
        assertThat(aggregator.closeFor(target))
                .hasSize(2)
                .extracting(DamageDealtEvent::totalDamage)
                .containsExactlyInAnyOrder(10.0, 4.0);
    }

    @Test
    @DisplayName("death closes the window immediately rather than leaving it hanging")
    void deathClosesTheWindow() {
        CombatFixture fixture = new CombatFixture();
        UUID attacker = fixture.player(5000.0, 0.0, 4.0);
        UUID target = fixture.mob(100.0, 0.0, 5.0);

        fixture.pipeline.meleeAttack(attacker, target);

        assertThat(fixture.damageEvents)
                .anySatisfy(event -> assertThat(event.lethal()).isTrue());
        assertThat(fixture.deaths).hasSize(1);
    }

    @Test
    @DisplayName("forgetting a target drops its windows without publishing")
    void forgetting() {
        CombatFixture.TestClock clock = new CombatFixture.TestClock();
        DamageAggregator aggregator = new DamageAggregator(clock, Duration.ofMillis(500));
        UUID target = UUID.randomUUID();
        aggregator.record(UUID.randomUUID(), target, DamageType.PHYSICAL, 10.0);

        aggregator.forget(target);

        assertThat(aggregator.openWindowCount()).isZero();
        assertThat(aggregator.closeFor(target)).isEmpty();
    }

    @Test
    @DisplayName("environmental damage aggregates too, with no attacker")
    void environmentAggregates() {
        CombatFixture.TestClock clock = new CombatFixture.TestClock();
        DamageAggregator aggregator = new DamageAggregator(clock, Duration.ofMillis(500));
        UUID target = UUID.randomUUID();

        aggregator.record(null, target, DamageType.ENVIRONMENT, 2.0);
        aggregator.record(null, target, DamageType.ENVIRONMENT, 2.0);

        assertThat(aggregator.closeFor(target)).singleElement().satisfies(event -> {
            assertThat(event.attacker()).isEmpty();
            assertThat(event.totalDamage()).isEqualTo(4.0);
        });
    }
}
