package rpg.core.combat;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** T100, T101: the combat state B08 needs (FR-030c to FR-030f, SC-010e). */
class CombatStateTest {

    @Test
    @DisplayName("a hit puts both sides in combat, and it expires on its own")
    void enteringAndLeaving() {
        CombatFixture fixture = new CombatFixture();
        UUID attacker = fixture.player(50.0, 0.0, 4.0);
        UUID target = fixture.mob(1000.0, 0.0, 5.0);

        fixture.pipeline.meleeAttack(attacker, target);

        assertThat(fixture.pipeline.isInCombat(attacker)).isTrue();
        assertThat(fixture.pipeline.isInCombat(target)).isTrue();

        fixture.clock.advance(Duration.ofSeconds(9)); // shipped timeout is 8s

        assertThat(fixture.pipeline.isInCombat(attacker)).isFalse();
        assertThat(fixture.pipeline.isInCombat(target)).isFalse();
    }

    @Test
    @DisplayName("entering combat is announced exactly once, not on every hit")
    void announcedOnce() {
        CombatFixture fixture = new CombatFixture();
        UUID attacker = fixture.player(50.0, 0.0, 4.0);
        UUID target = fixture.mob(100_000.0, 0.0, 5.0);
        fixture.clearRecorded();

        for (int i = 0; i < 10; i++) {
            fixture.pipeline.meleeAttack(attacker, target);
            fixture.clock.advanceMillis(300);
        }

        assertThat(fixture.combatStates)
                .filteredOn(CombatStateChangedEvent::inCombat)
                .hasSize(2); // attacker and target, once each
    }

    @Test
    @DisplayName("leaving combat is published when it is next evaluated")
    void leavingIsPublished() {
        CombatFixture fixture = new CombatFixture();
        UUID attacker = fixture.player(50.0, 0.0, 4.0);
        UUID target = fixture.mob(1000.0, 0.0, 5.0);
        fixture.pipeline.meleeAttack(attacker, target);
        fixture.clearRecorded();

        fixture.clock.advance(Duration.ofSeconds(9));
        fixture.pipeline.publishExpiredCombatStates();

        assertThat(fixture.combatStates)
                .hasSize(2)
                .allSatisfy(event -> assertThat(event.inCombat()).isFalse());
    }

    @Test
    @DisplayName("remaining time counts down and then reports nothing")
    void remainingTime() {
        CombatFixture fixture = new CombatFixture();
        UUID attacker = fixture.player(50.0, 0.0, 4.0);
        UUID target = fixture.mob(1000.0, 0.0, 5.0);
        fixture.pipeline.meleeAttack(attacker, target);

        // No time has passed since the hit, so the full timeout is left.
        assertThat(fixture.pipeline.remainingCombatTime(attacker))
                .hasValueSatisfying(left -> assertThat(left.toSeconds()).isEqualTo(8));

        fixture.clock.advance(Duration.ofSeconds(3));
        assertThat(fixture.pipeline.remainingCombatTime(attacker))
                .hasValueSatisfying(left -> assertThat(left.toSeconds()).isEqualTo(5));

        fixture.clock.advance(Duration.ofSeconds(9));

        assertThat(fixture.pipeline.remainingCombatTime(attacker)).isEmpty();
    }

    @Test
    @DisplayName("taking damage counts too, not only dealing it")
    void takingDamageCounts() {
        CombatFixture fixture = new CombatFixture();
        UUID victim = fixture.player(5.0, 0.0, 4.0);

        fixture.pipeline.environmentDamage(victim, EnvironmentSource.LAVA);

        assertThat(fixture.pipeline.isInCombat(victim)).isTrue();
    }

    @Test
    @DisplayName("nothing is ever scheduled for the combat state")
    void nothingIsScheduled() {
        CombatFixture fixture = new CombatFixture();
        UUID attacker = fixture.player(50.0, 0.0, 4.0);
        UUID target = fixture.mob(100_000.0, 0.0, 5.0);
        fixture.clearRecorded();

        for (int i = 0; i < 200; i++) {
            fixture.pipeline.meleeAttack(attacker, target);
            fixture.clock.advanceMillis(300);
            fixture.pipeline.isInCombat(attacker);
            fixture.pipeline.publishExpiredCombatStates();
        }

        assertThat(fixture.scheduler.scheduled).isZero();
    }

    @Test
    @DisplayName("someone who never fought is not in combat")
    void neverFought() {
        CombatFixture fixture = new CombatFixture();
        UUID idle = fixture.player(50.0, 0.0, 4.0);

        assertThat(fixture.pipeline.isInCombat(idle)).isFalse();
        assertThat(fixture.pipeline.remainingCombatTime(idle)).isEmpty();
    }

    @Test
    @DisplayName("forgetting a holder clears its state")
    void forgetting() {
        CombatFixture fixture = new CombatFixture();
        UUID attacker = fixture.player(50.0, 0.0, 4.0);
        UUID target = fixture.mob(1000.0, 0.0, 5.0);
        fixture.pipeline.meleeAttack(attacker, target);

        fixture.pipeline.forget(attacker);

        assertThat(fixture.pipeline.isInCombat(attacker)).isFalse();
    }
}
