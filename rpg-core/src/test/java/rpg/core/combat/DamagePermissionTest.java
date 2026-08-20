package rpg.core.combat;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** T091-T094: the one place that decides who may hit whom (FR-041 to FR-043, SC-010). */
class DamagePermissionTest {

    private static final DamagePermission RULE = DamagePermission.defaultRule();

    @Test
    @DisplayName("the shipped rule table, line by line")
    void theTable() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();

        assertThat(RULE.isAllowed(a, true, b, false)).as("player -> mob").isTrue();
        assertThat(RULE.isAllowed(a, false, b, true)).as("mob -> player").isTrue();
        assertThat(RULE.isAllowed(a, true, b, true)).as("player -> player").isFalse();
        assertThat(RULE.isAllowed(a, false, b, false)).as("mob -> mob").isFalse();
        assertThat(RULE.isAllowed(a, true, a, true)).as("player -> self").isTrue();
        assertThat(RULE.isAllowed(a, false, a, false)).as("mob -> self").isTrue();
        assertThat(RULE.isAllowed(null, false, b, true)).as("environment -> player").isTrue();
        assertThat(RULE.isAllowed(null, false, b, false)).as("environment -> mob").isTrue();
    }

    @Test
    @DisplayName("a player cannot hurt another player, and nothing is left behind")
    void noPvp() {
        CombatFixture fixture = new CombatFixture();
        UUID attacker = fixture.player(50.0, 0.0, 4.0);
        UUID victim = fixture.player(10.0, 0.0, 4.0);
        double before = fixture.health(victim);

        DamageResult result = fixture.pipeline.meleeAttack(attacker, victim);

        assertThat(result.applied()).isFalse();
        assertThat(result.reason()).isEqualTo(RejectReason.NOT_PERMITTED);
        assertThat(fixture.health(victim)).isEqualTo(before);
        assertThat(fixture.pipeline.currentShares(victim)).isEmpty();
        assertThat(fixture.damageEvents).isEmpty();
    }

    @Test
    @DisplayName("a mob cannot hurt another mob - a creeper does not clear the horde around it")
    void noMobAgainstMob() {
        CombatFixture fixture = new CombatFixture();
        UUID creeper = fixture.mob(50.0, 0.0, 40.0);
        UUID zombie = fixture.mob(80.0, 0.0, 10.0);
        double before = fixture.health(zombie);

        DamageResult result = fixture.pipeline.meleeAttack(creeper, zombie);

        assertThat(result.reason()).isEqualTo(RejectReason.NOT_PERMITTED);
        assertThat(fixture.health(zombie)).isEqualTo(before);
    }

    @Test
    @DisplayName("the same explosion still hurts a player standing next to it")
    void explosionStillHurtsPlayers() {
        CombatFixture fixture = new CombatFixture();
        UUID player = fixture.player(10.0, 0.0, 4.0);
        double before = fixture.health(player);

        fixture.pipeline.environmentDamage(player, EnvironmentSource.ENTITY_EXPLOSION);

        assertThat(fixture.health(player)).isLessThan(before);
    }

    @Test
    @DisplayName("self damage lands but produces no attribution")
    void selfDamageWithoutAttribution() {
        CombatFixture fixture = new CombatFixture();
        UUID mob = fixture.mob(500.0, 0.0, 20.0);
        double before = fixture.health(mob);

        DamageResult result = fixture.pipeline.meleeAttack(mob, mob);

        assertThat(result.applied()).isTrue();
        assertThat(fixture.health(mob)).isLessThan(before);
        assertThat(fixture.pipeline.currentShares(mob)).isEmpty();
    }

    @Test
    @DisplayName("a replacement rule takes effect at that one place, and nowhere else")
    void ruleIsReplaceable() {
        CombatFixture fixture = new CombatFixture();
        // What B09 will do: allow PvP inside a zone.
        fixture.pipeline.setPermission((attackerId, attackerIsPlayer, targetId, targetIsPlayer) -> true);

        UUID attacker = fixture.player(50.0, 0.0, 4.0);
        UUID victim = fixture.player(10.0, 0.0, 4.0);
        double before = fixture.health(victim);

        assertThat(fixture.pipeline.meleeAttack(attacker, victim).applied()).isTrue();
        assertThat(fixture.health(victim)).isLessThan(before);
    }
}
