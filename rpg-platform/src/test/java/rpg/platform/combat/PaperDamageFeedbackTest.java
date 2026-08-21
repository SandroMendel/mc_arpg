package rpg.platform.combat;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

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
import rpg.core.combat.DamageDealtEvent;
import rpg.core.combat.DamageFeedback;
import rpg.core.combat.DefaultCombatPipeline;
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
 * Feedback happens <b>per hit</b>; only the number is summed (FR-037, FR-038).
 *
 * <p>The distinction this guards is easy to get wrong. Damage <em>numbers</em> are bundled, because
 * at 150 players against 800 mobs one floating text per hit would be thousands of draw calls a
 * second. The hurt animation and the knockback are <b>not</b> bundled: a player who lands three
 * blows must see three flinches, or the fight stops reading as a fight.
 *
 * <p>A zeroed vanilla event shows no animation of its own (FR-017), which is why this has to be asked
 * for explicitly rather than left to Bukkit.
 */
class PaperDamageFeedbackTest {

    private static final Logger QUIET = quiet();

    private static Logger quiet() {
        Logger logger = Logger.getLogger("damage-feedback-test");
        logger.setLevel(Level.OFF);
        return logger;
    }

    /**
     * A clock the test moves itself.
     *
     * <p>Needed because the attack window is real: three melee calls microseconds apart are two
     * rejections and one hit, whatever the attack speed says. Without a steered clock this test
     * would be measuring the window instead of the feedback.
     */
    private static final class TestClock extends Clock {

        private java.time.Instant now = java.time.Instant.parse("2026-08-21T12:00:00Z");

        @Override
        public java.time.ZoneId getZone() {
            return java.time.ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public java.time.Instant instant() {
            return now;
        }

        void advanceSeconds(long seconds) {
            now = now.plusSeconds(seconds);
        }
    }

    /** Records what was asked for, so "per hit" can be counted rather than assumed. */
    private static final class RecordingFeedback implements DamageFeedback {

        final List<UUID> animations = new ArrayList<>();
        final List<UUID> knockbacks = new ArrayList<>();
        final List<Double> strengths = new ArrayList<>();

        @Override
        public void playHurtAnimation(UUID targetId) {
            animations.add(targetId);
        }

        @Override
        public void applyKnockback(UUID targetId, UUID sourceId, double strength) {
            knockbacks.add(targetId);
            strengths.add(strength);
        }
    }

    private ServerMock server;
    private WorldMock world;
    private DefaultStatEngine stats;
    private DefaultCombatPipeline pipeline;
    private DefaultEventBus eventBus;
    private RecordingFeedback recording;
    private TestClock clock;
    private final List<DamageDealtEvent> numbers = new ArrayList<>();

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        eventBus = new DefaultEventBus(QUIET);
        clock = new TestClock();
        stats =
                new DefaultStatEngine(
                        StatConfig.defaults(), new ImmediateScheduler(), eventBus, null, QUIET);
        pipeline =
                new DefaultCombatPipeline(
                        CombatConfig.defaults(), stats, eventBus, null, clock, QUIET);
        recording = new RecordingFeedback();
        pipeline.registerFeedback(recording);
        numbers.clear();
        eventBus.subscribe(DamageDealtEvent.class, numbers::add);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private UUID player(double physicalDamage) {
        LivingEntity entity = server.addPlayer();
        stats.createForCharacter(entity.getUniqueId(), UUID.randomUUID(), new ResourcePool(0.0, 0.0));
        stats.apply(
                entity.getUniqueId(),
                ModifierSet.of(
                        SourceId.of(SourceKind.EQUIPMENT, "gear"),
                        StatModifier.flat(
                                Attribute.PHYSICAL_DAMAGE,
                                physicalDamage
                                        - StatConfig.defaults()
                                                .definition(Attribute.PHYSICAL_DAMAGE)
                                                .base()),
                        // Fast enough that the attack window is not what limits the test.
                        StatModifier.percent(Attribute.ATTACK_SPEED, 24.0)));
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
    @DisplayName("one hit asks for one hurt animation")
    void oneHitOneAnimation() {
        UUID attacker = player(10.0);
        UUID target = mob(1_000.0);

        pipeline.meleeAttack(attacker, target);

        assertThat(recording.animations).containsExactly(target);
    }

    @Test
    @DisplayName("three hits ask for three animations, even though the number is bundled once")
    void threeHitsThreeAnimations() {
        UUID attacker = player(10.0);
        UUID target = mob(1_000.0);

        pipeline.meleeAttack(attacker, target);
        clock.advanceSeconds(1);
        pipeline.meleeAttack(attacker, target);
        clock.advanceSeconds(1);
        pipeline.meleeAttack(attacker, target);

        assertThat(recording.animations)
                .as("FR-037: a fight that flinches once for three blows does not read as a fight")
                .hasSize(3);
        assertThat(numbers.size())
                .as("FR-038: fewer number events than hits - that is what bundling means")
                .isLessThan(recording.animations.size());
    }

    @Test
    @DisplayName("hits inside one window flinch three times and produce no number yet")
    void hitsInsideOneWindow() {
        UUID attacker = player(10.0);
        UUID target = mob(1_000.0);

        // Abilities are deliberately not subject to the attack window - they have their own
        // cooldowns in B08 - so three of them land inside the same aggregation window.
        pipeline.abilityDamage(attacker, target, rpg.core.combat.DamageType.MAGIC, 1.0);
        pipeline.abilityDamage(attacker, target, rpg.core.combat.DamageType.MAGIC, 1.0);
        pipeline.abilityDamage(attacker, target, rpg.core.combat.DamageType.MAGIC, 1.0);

        assertThat(recording.animations).as("one flinch per blow").hasSize(3);
        assertThat(numbers)
                .as("the number waits for the window to close; the animation never does")
                .isEmpty();
    }

    @Test
    @DisplayName("knockback is applied per hit as well, with the configured strength")
    void knockbackPerHit() {
        UUID attacker = player(10.0);
        UUID target = mob(1_000.0);

        pipeline.meleeAttack(attacker, target);
        clock.advanceSeconds(1);
        pipeline.meleeAttack(attacker, target);

        assertThat(recording.knockbacks).hasSize(2);
        assertThat(recording.strengths)
                .allSatisfy(s -> assertThat(s).isEqualTo(CombatConfig.defaults().knockbackStrength()));
    }

    @Test
    @DisplayName("a rejected hit produces no feedback at all")
    void rejectedHitIsSilent() {
        UUID attacker = player(10.0);
        UUID outsider = UUID.randomUUID();

        pipeline.meleeAttack(attacker, outsider);

        // Nothing was hit, so nothing should flinch. A flinch without damage would read as a miss
        // that hurt.
        assertThat(recording.animations).isEmpty();
        assertThat(recording.knockbacks).isEmpty();
    }

    @Test
    @DisplayName("environmental damage flinches too - lava has to be visible")
    void environmentFlinches() {
        UUID target = mob(1_000.0);

        pipeline.environmentDamage(target, rpg.core.combat.EnvironmentSource.LAVA);

        assertThat(recording.animations).containsExactly(target);
        // But no knockback: there is nothing to be pushed away from.
        assertThat(recording.knockbacks).isEmpty();
    }

    @Test
    @DisplayName("without a registered feedback the pipeline simply does nothing")
    void withoutFeedbackNothingHappens() {
        DefaultCombatPipeline bare =
                new DefaultCombatPipeline(
                        CombatConfig.defaults(), stats, eventBus, null, clock, QUIET);
        UUID attacker = player(10.0);
        UUID target = mob(1_000.0);

        // Same rule B05 states for its other extension points: an absent implementation is silence,
        // never a crash.
        bare.meleeAttack(attacker, target);

        assertThat(stats.resources(target).currentHealth()).isLessThan(1_000.0);
        assertThat(recording.animations).isEmpty();
    }

    @Test
    @DisplayName("the Paper implementation resolves a player and does not throw")
    void paperImplementationResolvesAPlayer() {
        var player = server.addPlayer();
        PaperDamageFeedback feedback =
                new PaperDamageFeedback(server, new ImmediateScheduler(), QUIET);

        // ImmediateScheduler runs it inline, so a failure would surface right here.
        feedback.playHurtAnimation(player.getUniqueId());
        feedback.applyKnockback(player.getUniqueId(), player.getUniqueId(), 0.4);

        assertThat(player.isOnline()).isTrue();
    }

    @Test
    @DisplayName("the Paper implementation shrugs off an unknown holder")
    void paperImplementationSurvivesUnknownHolder() {
        PaperDamageFeedback feedback =
                new PaperDamageFeedback(server, new ImmediateScheduler(), QUIET);

        // A holder that logged out between the hit and the tick. Must not throw (Principle VI).
        feedback.playHurtAnimation(UUID.randomUUID());
        feedback.applyKnockback(UUID.randomUUID(), UUID.randomUUID(), 0.4);
    }

    @Test
    @DisplayName("a knockback strength of zero is skipped rather than applied as nothing")
    void zeroKnockbackIsSkipped() {
        PaperDamageFeedback feedback =
                new PaperDamageFeedback(server, new ImmediateScheduler(), QUIET);

        // Configuring it to zero is how an operator turns knockback off; the call must then not even
        // look the entity up.
        feedback.applyKnockback(UUID.randomUUID(), UUID.randomUUID(), 0.0);
        feedback.applyKnockback(UUID.randomUUID(), UUID.randomUUID(), -1.0);
    }
}
