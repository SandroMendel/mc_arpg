package rpg.core.combat;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T051-T054: attack speed as a real limit (FR-020 to FR-023, SC-006).
 *
 * <p>All of it on a controlled clock. The last test is the one that matters most: over the whole
 * run, <b>nothing is scheduled</b>. A timing measurement would pass just as happily with a task per
 * player, and that is exactly what Principle II rules out.
 */
class AttackWindowTest {

    @Test
    @DisplayName("at 4 attacks per second, ten swings in one second count four times")
    void fourPerSecond() {
        CombatFixture fixture = new CombatFixture();
        UUID attacker = fixture.player(50.0, 0.0, 4.0);
        UUID target = fixture.mob(100_000.0, 0.0, 5.0);

        int counted = 0;
        for (int i = 0; i < 10; i++) {
            if (fixture.pipeline.meleeAttack(attacker, target).applied()) {
                counted++;
            }
            fixture.clock.advanceMillis(100); // ten swings spread over one second
        }

        assertThat(counted).isEqualTo(4);
    }

    @Test
    @DisplayName("a discarded swing leaves no damage, no event and no attribution")
    void discardedSwingLeavesNothing() {
        CombatFixture fixture = new CombatFixture();
        UUID attacker = fixture.player(50.0, 0.0, 4.0);
        UUID target = fixture.mob(1000.0, 0.0, 5.0);

        fixture.pipeline.meleeAttack(attacker, target);
        double afterFirst = fixture.health(target);
        fixture.clearRecorded();

        DamageResult second = fixture.pipeline.meleeAttack(attacker, target);

        assertThat(second.applied()).isFalse();
        assertThat(second.reason()).isEqualTo(RejectReason.ATTACK_TOO_SOON);
        assertThat(fixture.health(target)).isEqualTo(afterFirst);
        assertThat(fixture.damageEvents).isEmpty();
    }

    @Test
    @DisplayName("waiting long enough makes the next swing count")
    void waitingWorks() {
        CombatFixture fixture = new CombatFixture();
        UUID attacker = fixture.player(50.0, 0.0, 4.0);
        UUID target = fixture.mob(1000.0, 0.0, 5.0);

        assertThat(fixture.pipeline.meleeAttack(attacker, target).applied()).isTrue();
        fixture.clock.advanceMillis(250); // exactly the gap at 4 per second
        assertThat(fixture.pipeline.meleeAttack(attacker, target).applied()).isTrue();
    }

    @Test
    @DisplayName("a changed attack speed applies to the very next swing")
    void changedSpeedAppliesImmediately() {
        CombatFixture fixture = new CombatFixture();
        UUID slow = fixture.player(50.0, 0.0, 2.0); // gap 500 ms
        UUID target = fixture.mob(1000.0, 0.0, 5.0);

        assertThat(fixture.pipeline.meleeAttack(slow, target).applied()).isTrue();
        fixture.clock.advanceMillis(300);
        assertThat(fixture.pipeline.meleeAttack(slow, target).applied()).isFalse();

        UUID fast = fixture.player(50.0, 0.0, 6.0); // gap 166 ms
        assertThat(fixture.pipeline.meleeAttack(fast, target).applied()).isTrue();
        fixture.clock.advanceMillis(200);
        assertThat(fixture.pipeline.meleeAttack(fast, target).applied()).isTrue();
    }

    @Test
    @DisplayName("abilities are not subject to the attack window - they have their own cooldowns")
    void abilitiesBypassTheWindow() {
        CombatFixture fixture = new CombatFixture();
        UUID caster = fixture.player(50.0, 0.0, 4.0);
        UUID target = fixture.mob(100_000.0, 0.0, 5.0);

        for (int i = 0; i < 5; i++) {
            assertThat(fixture.pipeline.abilityDamage(caster, target, DamageType.MAGIC, 1.0).applied())
                    .as("ability " + i)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("nothing is ever scheduled - the whole point of a timestamp")
    void nothingIsScheduled() {
        CombatFixture fixture = new CombatFixture();
        UUID attacker = fixture.player(50.0, 0.0, 4.0);
        UUID target = fixture.mob(100_000.0, 0.0, 5.0);
        fixture.clearRecorded();

        for (int i = 0; i < 500; i++) {
            fixture.pipeline.meleeAttack(attacker, target);
            fixture.clock.advanceMillis(50);
        }

        assertThat(fixture.scheduler.scheduled).isZero();
    }

    @Test
    @DisplayName("the gap follows from the attribute, and a nonsensical speed does not freeze anyone")
    void gapDerivation() {
        assertThat(AttackWindow.minimumGapMillis(4.0)).isEqualTo(250L);
        assertThat(AttackWindow.minimumGapMillis(1.0)).isEqualTo(1000L);
        assertThat(AttackWindow.minimumGapMillis(0.0)).isEqualTo(1000L);
        assertThat(AttackWindow.minimumGapMillis(-5.0)).isEqualTo(1000L);
        assertThat(AttackWindow.minimumGapMillis(Double.NaN)).isEqualTo(1000L);
    }

    @Test
    @DisplayName("canAttackNow answers without recording a swing")
    void canAttackDoesNotRecord() {
        CombatFixture fixture = new CombatFixture();
        UUID attacker = fixture.player(50.0, 0.0, 4.0);
        UUID target = fixture.mob(1000.0, 0.0, 5.0);

        assertThat(fixture.pipeline.canAttackNow(attacker)).isTrue();
        assertThat(fixture.pipeline.canAttackNow(attacker)).isTrue();
        assertThat(fixture.pipeline.meleeAttack(attacker, target).applied()).isTrue();
        assertThat(fixture.pipeline.canAttackNow(attacker)).isFalse();
    }
}
