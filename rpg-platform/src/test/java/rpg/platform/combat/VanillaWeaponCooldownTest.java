package rpg.platform.combat;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.attribute.Attributable;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import rpg.core.combat.CombatConfig;
import rpg.core.combat.DefaultCombatPipeline;
import rpg.core.combat.RejectReason;
import rpg.core.event.DefaultEventBus;
import rpg.core.stats.Attribute;
import rpg.core.stats.DefaultStatEngine;
import rpg.core.stats.ModifierSet;
import rpg.core.stats.ResourcePool;
import rpg.core.stats.SourceId;
import rpg.core.stats.SourceKind;
import rpg.core.stats.StatConfig;
import rpg.core.stats.StatModifier;
import rpg.platform.scheduler.ImmediateScheduler;

/**
 * The vanilla weapon cooldown has no say in the damage (FR-024, research.md E7).
 *
 * <p>This looks like a contradiction between two blocks and is not. B04 mirrors {@code attackSpeed}
 * onto the vanilla attribute; B05 requires that the vanilla weapon cooldown be without effect. Both
 * hold at once, because the vanilla cooldown only ever scaled <b>vanilla</b> damage - and B05 sets
 * that to zero anyway. What the mirroring still does is drive the cooldown indicator in the client,
 * and thanks to the same number it shows exactly what B05 actually enforces.
 *
 * <p>The rate is governed by B05's own attack window, which is what these tests measure.
 */
class VanillaWeaponCooldownTest {

    private static final Logger QUIET = quiet();

    private static Logger quiet() {
        Logger logger = Logger.getLogger("weapon-cooldown-test");
        logger.setLevel(Level.OFF);
        return logger;
    }

    private static final class TestClock extends Clock {

        private Instant now = Instant.parse("2026-08-21T12:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }

        void advanceMillis(long millis) {
            now = now.plusMillis(millis);
        }
    }

    private ServerMock server;
    private WorldMock world;
    private TestClock clock;
    private DefaultStatEngine stats;
    private DefaultCombatPipeline pipeline;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        clock = new TestClock();
        var eventBus = new DefaultEventBus(QUIET);
        stats =
                new DefaultStatEngine(
                        StatConfig.defaults(), new ImmediateScheduler(), eventBus, null, QUIET);
        pipeline =
                new DefaultCombatPipeline(
                        CombatConfig.defaults(), stats, eventBus, null, clock, QUIET);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private UUID player(double physicalDamage, double attackSpeed) {
        LivingEntity entity = server.addPlayer();
        stats.createForCharacter(entity.getUniqueId(), UUID.randomUUID(), new ResourcePool(0.0, 0.0));
        double base = StatConfig.defaults().definition(Attribute.ATTACK_SPEED).base();
        stats.apply(
                entity.getUniqueId(),
                ModifierSet.of(
                        SourceId.of(SourceKind.EQUIPMENT, "weapon"),
                        StatModifier.flat(
                                Attribute.PHYSICAL_DAMAGE,
                                physicalDamage
                                        - StatConfig.defaults()
                                                .definition(Attribute.PHYSICAL_DAMAGE)
                                                .base()),
                        StatModifier.percent(Attribute.ATTACK_SPEED, (attackSpeed / base) - 1.0)));
        stats.recalculateNow(entity.getUniqueId());
        fill(entity.getUniqueId());
        return entity.getUniqueId();
    }

    private UUID mob(double health) {
        LivingEntity entity =
                (LivingEntity) world.spawnEntity(world.getSpawnLocation(), EntityType.ZOMBIE);
        stats.createForEntity(entity.getUniqueId());
        stats.apply(
                entity.getUniqueId(),
                ModifierSet.of(
                        SourceId.of(SourceKind.CLASS, "mob:TEST"),
                        StatModifier.flat(
                                Attribute.HEALTH,
                                health - StatConfig.defaults().definition(Attribute.HEALTH).base())));
        stats.recalculateNow(entity.getUniqueId());
        fill(entity.getUniqueId());
        return entity.getUniqueId();
    }

    private void fill(UUID id) {
        var snapshot = stats.snapshot(id);
        stats.restoreResources(
                id, ResourcePool.full(snapshot.get(Attribute.HEALTH), snapshot.get(Attribute.MANA)));
    }

    @Test
    @DisplayName("the same swing costs the same, cooled down or not")
    void cooldownDoesNotScaleDamage() {
        UUID attacker = player(40.0, 4.0);
        UUID first = mob(1_000.0);
        UUID second = mob(1_000.0);
        var shooter = server.getPlayer(attacker);

        // A vanilla client would deal a fraction of the damage in this state.
        shooter.setCooldown(org.bukkit.Material.DIAMOND_SWORD, 20);
        pipeline.meleeAttack(attacker, first);

        clock.advanceMillis(1_000);
        shooter.setCooldown(org.bukkit.Material.DIAMOND_SWORD, 0);
        pipeline.meleeAttack(attacker, second);

        assertThat(1_000.0 - stats.resources(first).currentHealth())
                .as("FR-024: the vanilla cooldown has no say in our arithmetic")
                .isEqualTo(1_000.0 - stats.resources(second).currentHealth())
                .isEqualTo(40.0);
    }

    @Test
    @DisplayName("the rate comes from our attack window, not from vanilla")
    void rateComesFromTheAttackWindow() {
        // Four per second means one every 250 ms.
        UUID attacker = player(10.0, 4.0);
        UUID target = mob(10_000.0);

        int landed = 0;
        for (int i = 0; i < 10; i++) {
            if (pipeline.meleeAttack(attacker, target).applied()) {
                landed++;
            }
            clock.advanceMillis(100);
        }

        // Ten attempts across 1000 ms at four per second: four count, the rest are too soon.
        assertThat(landed).isEqualTo(4);
    }

    @Test
    @DisplayName("a swing that comes too early is discarded, not weakened")
    void tooEarlyIsDiscardedNotWeakened() {
        UUID attacker = player(40.0, 4.0);
        UUID target = mob(1_000.0);

        pipeline.meleeAttack(attacker, target);
        double afterFirst = stats.resources(target).currentHealth();

        clock.advanceMillis(10);
        var second = pipeline.meleeAttack(attacker, target);

        // The decision from the clarify round: discard, never scale down. A weakened hit would make
        // click-spamming better than nothing, which is exactly what attackSpeed is meant to prevent.
        assertThat(second.reason()).isEqualTo(RejectReason.ATTACK_TOO_SOON);
        assertThat(second.finalDamage()).isZero();
        assertThat(stats.resources(target).currentHealth()).isEqualTo(afterFirst);
    }

    @Test
    @DisplayName("B04 still mirrors attackSpeed, and its band still bounds what gear can do")
    void mirroringIsStillInPlace() {
        // Inside the band: mirrored exactly. The mirroring is what drives the cooldown indicator in
        // the client, and it has to stay - otherwise the bar shows something other than what B05
        // enforces (research.md E7).
        UUID inBand = player(10.0, 5.0);
        assertThat(stats.value(inBand, Attribute.ATTACK_SPEED)).isEqualTo(5.0);

        // Beyond it: clamped by B04's modifier band of +-50% around a base of 4.0, so 6.0 and not
        // 8.0. B05 does not check that itself - there must be exactly one truth about the limit.
        UUID beyondBand = player(10.0, 8.0);
        assertThat(stats.value(beyondBand, Attribute.ATTACK_SPEED)).isEqualTo(6.0);

        assertThat(server.getPlayer(inBand)).isInstanceOf(Attributable.class);
    }

    @Test
    @DisplayName("a clamped attack speed is the rate that is actually enforced")
    void clampedSpeedIsTheEnforcedRate() {
        // Asking for 8 per second yields 6 after the band. The window must limit to six, not eight -
        // otherwise the client's indicator and the server's rule would disagree.
        UUID attacker = player(10.0, 8.0);
        UUID target = mob(10_000.0);

        int landed = 0;
        for (int i = 0; i < 12; i++) {
            if (pipeline.meleeAttack(attacker, target).applied()) {
                landed++;
            }
            clock.advanceMillis(100);
        }

        // Twelve attempts across 1200 ms at six per second.
        assertThat(landed).isEqualTo(6);
    }

    @Test
    @DisplayName("an ability ignores the attack window entirely")
    void abilitiesAreNotRateLimited() {
        UUID attacker = player(10.0, 4.0);
        UUID target = mob(10_000.0);

        // Abilities carry their own cooldowns in B08; checking both would limit them twice.
        int landed = 0;
        for (int i = 0; i < 5; i++) {
            if (pipeline.abilityDamage(attacker, target, rpg.core.combat.DamageType.MAGIC, 1.0)
                    .applied()) {
                landed++;
            }
        }

        assertThat(landed).isEqualTo(5);
    }

    @Test
    @DisplayName("a projectile is not rate-limited by the melee window either")
    void projectilesAreNotRateLimited() {
        UUID attacker = player(10.0, 4.0);
        UUID target = mob(10_000.0);

        int landed = 0;
        for (int i = 0; i < 5; i++) {
            if (pipeline.projectileDamage(attacker, target, 10.0).applied()) {
                landed++;
            }
        }

        // A bow's rate is the draw time, which is vanilla's business; the melee window has nothing
        // to say about it.
        assertThat(landed).isEqualTo(5);
    }
}
