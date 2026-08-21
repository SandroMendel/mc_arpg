package rpg.platform.combat;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import rpg.core.combat.CombatConfig;
import rpg.core.combat.DefaultCombatPipeline;
import rpg.core.combat.EnvironmentSource;
import rpg.core.event.DefaultEventBus;
import rpg.core.stats.Attribute;
import rpg.core.stats.DefaultStatEngine;
import rpg.core.stats.ResourcePool;
import rpg.core.stats.StatConfig;
import rpg.platform.scheduler.ImmediateScheduler;

/**
 * The gate no vanilla damage gets past (FR-013 to FR-018).
 *
 * <p>This is the listener ADR-003 exists for. Its promise is absolute: whatever the cause, vanilla's
 * own number never reaches a holder of this combat system. What happens instead is decided per cause
 * in {@link VanillaDamageMapping} - and that table is covered by its own test. Covered here is what
 * the listener <em>does</em> with each decision.
 */
class VanillaDamageListenerTest {

    private static final Logger QUIET = quiet();

    private static Logger quiet() {
        Logger logger = Logger.getLogger("vanilla-damage-listener-test");
        logger.setLevel(Level.OFF);
        return logger;
    }

    private ServerMock server;
    private WorldMock world;
    private DefaultStatEngine stats;
    private DefaultCombatPipeline pipeline;
    private VanillaDamageListener listener;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        var eventBus = new DefaultEventBus(QUIET);
        stats =
                new DefaultStatEngine(
                        StatConfig.defaults(), new ImmediateScheduler(), eventBus, null, QUIET);
        pipeline =
                new DefaultCombatPipeline(
                        CombatConfig.defaults(), stats, eventBus, null, Clock.systemUTC(), QUIET);
        listener = new VanillaDamageListener(pipeline, new VanillaDamageMapping(QUIET), QUIET);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    /** A creature that is part of this combat system. */
    private LivingEntity holder() {
        LivingEntity entity = (LivingEntity) world.spawnEntity(world.getSpawnLocation(), EntityType.ZOMBIE);
        stats.createForEntity(entity.getUniqueId());
        stats.recalculateNow(entity.getUniqueId());
        var snapshot = stats.snapshot(entity.getUniqueId());
        stats.restoreResources(
                entity.getUniqueId(),
                ResourcePool.full(snapshot.get(Attribute.HEALTH), snapshot.get(Attribute.MANA)));
        return entity;
    }

    /** A creature that is not - an ordinary animal (FR-018). */
    private LivingEntity outsider() {
        return (LivingEntity) world.spawnEntity(world.getSpawnLocation(), EntityType.COW);
    }

    private EntityDamageEvent damage(LivingEntity target, EntityDamageEvent.DamageCause cause, double amount) {
        EntityDamageEvent event =
                new EntityDamageEvent(
                        target,
                        cause,
                        new java.util.EnumMap<>(
                                java.util.Map.of(EntityDamageEvent.DamageModifier.BASE, amount)),
                        new java.util.EnumMap<>(
                                java.util.Map.of(
                                        EntityDamageEvent.DamageModifier.BASE,
                                        (com.google.common.base.Function<Double, Double>) d -> d)));
        listener.onDamage(event);
        return event;
    }

    private double health(LivingEntity entity) {
        return stats.resources(entity.getUniqueId()).currentHealth();
    }

    // --- T028: nothing leaves with damage above zero -------------------------------------------

    @Test
    @DisplayName("no vanilla cause leaves the listener with a damage above zero")
    void everyCauseLeavesWithZeroDamage() {
        for (EntityDamageEvent.DamageCause cause : EntityDamageEvent.DamageCause.values()) {
            LivingEntity target = holder();

            EntityDamageEvent event = damage(target, cause, 7.5);

            // FR-016, and this is the whole point of ADR-003: the vanilla number is never the answer,
            // whether the cause is mapped, disabled, lethal or combat.
            assertThat(event.getDamage())
                    .as("vanilla damage for cause " + cause + " must be neutralised")
                    .isZero();
        }
    }

    @Test
    @DisplayName("an unmapped future cause is neutralised as well, not waved through")
    void unmappedCauseIsStillNeutralised() {
        // A Minecraft update adding a cause must not be able to let damage through. The mapping
        // answers with a refusal by default; here the listener's half of that is checked.
        LivingEntity target = holder();
        double before = health(target);

        EntityDamageEvent event = damage(target, EntityDamageEvent.DamageCause.CUSTOM, 100.0);

        assertThat(event.getDamage()).isZero();
        assertThat(health(target)).isEqualTo(before);
    }

    // --- T030: a creature without a holder stays untouched -------------------------------------

    @Test
    @DisplayName("a creature that is not part of this system is left entirely alone")
    void outsiderIsUntouched() {
        LivingEntity cow = outsider();

        EntityDamageEvent event = damage(cow, EntityDamageEvent.DamageCause.FALL, 6.0);

        // FR-018. The event is still zeroed - the listener cannot tell before asking the pipeline -
        // but nothing is applied and nothing throws, because the pipeline answers NO_HOLDER.
        assertThat(event.getDamage()).isZero();
        assertThat(stats.findSnapshot(cow.getUniqueId()))
                .as("no holder was invented for it")
                .isEmpty();
    }

    // --- T031: void and /kill are immediate ----------------------------------------------------

    @Test
    @DisplayName("the void kills at once, whatever the health value says")
    void voidKillsImmediately() {
        LivingEntity target = holder();
        assertThat(health(target)).isGreaterThan(0.0);

        EntityDamageEvent event = damage(target, EntityDamageEvent.DamageCause.VOID, 4.0);

        assertThat(event.isCancelled()).as("vanilla must not do it - the pipeline does").isTrue();
        assertThat(health(target)).as("FR-014: the void is not survivable").isZero();
    }

    @Test
    @DisplayName("/kill kills at once too")
    void killCommandKillsImmediately() {
        LivingEntity target = holder();

        EntityDamageEvent event = damage(target, EntityDamageEvent.DamageCause.KILL, 4.0);

        assertThat(event.isCancelled()).isTrue();
        assertThat(health(target)).as("FR-015").isZero();
    }

    @Test
    @DisplayName("a lethal cause is idempotent - a second one changes nothing")
    void lethalTwiceIsHarmless() {
        LivingEntity target = holder();
        damage(target, EntityDamageEvent.DamageCause.VOID, 4.0);

        damage(target, EntityDamageEvent.DamageCause.KILL, 4.0);

        assertThat(health(target)).isZero();
    }

    // --- T032: the disabled status effects do nothing ------------------------------------------

    @Test
    @DisplayName("every cause on the disabled list has no effect at all")
    void disabledCausesDoNothing() {
        VanillaDamageMapping mapping = new VanillaDamageMapping(QUIET);

        for (EntityDamageEvent.DamageCause cause : EntityDamageEvent.DamageCause.values()) {
            if (mapping.resolve(cause).treatment() != VanillaDamageMapping.Treatment.DISABLED) {
                continue;
            }
            LivingEntity target = holder();
            double before = health(target);

            EntityDamageEvent event = damage(target, cause, 20.0);

            // FR-013. Starvation, wither, poison and the instant-damage family are switched off
            // entirely: they belong to a progression system this project does not use.
            assertThat(event.isCancelled()).as(cause + " must be cancelled").isTrue();
            assertThat(event.getDamage()).as(cause + " must deal nothing").isZero();
            assertThat(health(target)).as(cause + " must not move health").isEqualTo(before);
        }
    }

    @Test
    @DisplayName("at least one cause really is on the disabled list")
    void theDisabledListIsNotEmpty() {
        // Without this the loop above could pass by skipping everything.
        VanillaDamageMapping mapping = new VanillaDamageMapping(QUIET);
        long disabled =
                java.util.Arrays.stream(EntityDamageEvent.DamageCause.values())
                        .filter(c -> mapping.resolve(c).treatment() == VanillaDamageMapping.Treatment.DISABLED)
                        .count();

        assertThat(disabled).isGreaterThanOrEqualTo(4);
    }

    // --- T029: the hurt animation has to be asked for -----------------------------------------

    @Test
    @DisplayName("a mapped hazard applies our own damage, and vanilla's stays at zero")
    void mappedHazardAppliesOwnDamage() {
        LivingEntity target = holder();
        double before = health(target);

        EntityDamageEvent event = damage(target, EntityDamageEvent.DamageCause.LAVA, 4.0);

        assertThat(event.getDamage()).isZero();
        assertThat(health(target))
                .as("the configured lava amount, not vanilla's four")
                .isEqualTo(before - CombatConfig.defaults().environmentDamageOf(EnvironmentSource.LAVA));
    }

    @Test
    @DisplayName("invulnerability ticks are cleared, or they would cap attack speed silently")
    void invulnerabilityTicksAreCleared() {
        LivingEntity target = holder();
        target.setNoDamageTicks(10);

        damage(target, EntityDamageEvent.DamageCause.LAVA, 4.0);

        // Vanilla's invulnerability window is a second, hidden attack window: left in place it would
        // quietly cap everyone at about two hits per second, whatever attackSpeed says (research.md
        // E6). The hurt animation is driven separately through DamageFeedback, which
        // PaperDamageFeedbackTest covers - a zeroed event shows none by itself (FR-017).
        assertThat(target.getNoDamageTicks()).isZero();
    }

    @Test
    @DisplayName("a fall uses the fallen distance, not vanilla's number")
    void fallUsesTheDistance() {
        LivingEntity target = holder();
        target.setFallDistance(10.0f);
        double before = health(target);

        damage(target, EntityDamageEvent.DamageCause.FALL, 99.0);

        // (10 - 3) * 4 = 28 from the shipped configuration; vanilla's 99 is irrelevant.
        assertThat(before - health(target)).isEqualTo(28.0);
    }

    @Test
    @DisplayName("a fall inside the safe height costs nothing")
    void shortFallCostsNothing() {
        LivingEntity target = holder();
        target.setFallDistance(2.0f);
        double before = health(target);

        damage(target, EntityDamageEvent.DamageCause.FALL, 4.0);

        assertThat(health(target)).isEqualTo(before);
    }

    @Test
    @DisplayName("combat damage without an attacker is cancelled rather than guessed at")
    void combatWithoutAttackerIsCancelled() {
        LivingEntity target = holder();
        double before = health(target);

        // ENTITY_ATTACK on a plain EntityDamageEvent: the cause says combat, but there is no damager
        // to price it from.
        EntityDamageEvent event = damage(target, EntityDamageEvent.DamageCause.ENTITY_ATTACK, 5.0);

        assertThat(event.isCancelled()).isTrue();
        assertThat(health(target)).isEqualTo(before);
    }

    @Test
    @DisplayName("a non-living entity is ignored entirely")
    void nonLivingEntityIsIgnored() {
        var stand = world.spawnEntity(world.getSpawnLocation(), EntityType.ARMOR_STAND);
        EntityDamageEvent event =
                new EntityDamageEvent(
                        stand,
                        EntityDamageEvent.DamageCause.FALL,
                        new java.util.EnumMap<>(
                                java.util.Map.of(EntityDamageEvent.DamageModifier.BASE, 5.0)),
                        new java.util.EnumMap<>(
                                java.util.Map.of(
                                        EntityDamageEvent.DamageModifier.BASE,
                                        (com.google.common.base.Function<Double, Double>) d -> d)));

        listener.onDamage(event);

        // An armour stand is a LivingEntity in Bukkit but not a combatant here; either way the
        // listener must not throw on something it has no holder for.
        assertThat(event.isCancelled() || event.getDamage() == 0.0).isTrue();
    }
}
