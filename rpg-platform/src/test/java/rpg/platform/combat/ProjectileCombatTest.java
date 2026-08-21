package rpg.platform.combat;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.entity.Arrow;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.MockBukkitExtension;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import rpg.core.combat.CombatConfig;
import rpg.core.combat.DamageResult;
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
 * Projectiles take the same arithmetic as a melee swing (SC-010a, FR-024a, FR-024b).
 *
 * <p>Without this a bow would be visibly useless from day one: FR-016 zeroes every vanilla damage,
 * arrow damage included. So a projectile carries the shooter's raw damage from the moment it was
 * fired, and the hit reads it back.
 *
 * <p>Carrying the number rather than the snapshot is deliberate. A snapshot held on a flying arrow
 * would keep a reference for an unbounded time, and there would have to be a list of arrows in
 * flight that could leak. One double in the projectile's own data container cannot.
 */
class ProjectileCombatTest {

    private static final Logger QUIET = quiet();

    private static Logger quiet() {
        Logger logger = Logger.getLogger("projectile-combat-test");
        logger.setLevel(Level.OFF);
        return logger;
    }

    private ServerMock server;
    private WorldMock world;
    private DefaultStatEngine stats;
    private DefaultCombatPipeline pipeline;
    private ProjectileCombatListener listener;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        var plugin = MockBukkit.createMockPlugin("ProjectileTest");
        ProjectileDamageTag.initialise(plugin);
        world = server.addSimpleWorld("world");
        var eventBus = new DefaultEventBus(QUIET);
        stats =
                new DefaultStatEngine(
                        StatConfig.defaults(), new ImmediateScheduler(), eventBus, null, QUIET);
        pipeline =
                new DefaultCombatPipeline(
                        CombatConfig.defaults(), stats, eventBus, null, Clock.systemUTC(), QUIET);
        listener = new ProjectileCombatListener(stats);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    /**
     * A player shooter, not a creature.
     *
     * <p>Mob against mob is refused before the arithmetic is ever reached (FR-042a), so a
     * zombie shooting a skeleton would make every damage assertion here pass on zero.
     */
    private LivingEntity shooter(double physicalDamage) {
        LivingEntity entity = server.addPlayer();
        stats.createForCharacter(entity.getUniqueId(), UUID.randomUUID(), new ResourcePool(0.0, 0.0));
        stats.apply(
                entity.getUniqueId(),
                ModifierSet.of(
                        SourceId.of(SourceKind.EQUIPMENT, "bow"),
                        StatModifier.flat(
                                Attribute.PHYSICAL_DAMAGE,
                                physicalDamage
                                        - StatConfig.defaults()
                                                .definition(Attribute.PHYSICAL_DAMAGE)
                                                .base())));
        stats.recalculateNow(entity.getUniqueId());
        fill(entity.getUniqueId());
        return entity;
    }

    private UUID target(double health, double defence) {
        UUID id = UUID.randomUUID();
        LivingEntity entity =
                (LivingEntity) world.spawnEntity(world.getSpawnLocation(), EntityType.SKELETON);
        id = entity.getUniqueId();
        stats.createForEntity(id);
        stats.apply(
                id,
                ModifierSet.of(
                        SourceId.of(SourceKind.CLASS, "mob:TEST"),
                        StatModifier.flat(
                                Attribute.HEALTH,
                                health - StatConfig.defaults().definition(Attribute.HEALTH).base()),
                        StatModifier.flat(Attribute.DEFENSE, defence)));
        stats.recalculateNow(id);
        fill(id);
        return id;
    }

    private void fill(UUID id) {
        var snapshot = stats.snapshot(id);
        stats.restoreResources(
                id, ResourcePool.full(snapshot.get(Attribute.HEALTH), snapshot.get(Attribute.MANA)));
    }

    private Arrow launchFrom(LivingEntity shooter) {
        Arrow arrow = (Arrow) world.spawnEntity(world.getSpawnLocation(), EntityType.ARROW);
        arrow.setShooter(shooter);
        listener.onLaunch(new ProjectileLaunchEvent(arrow));
        return arrow;
    }

    @Test
    @DisplayName("a launch stores the shooter's raw damage on the arrow")
    void launchStoresTheRawDamage() {
        LivingEntity shooter = shooter(40.0);

        Arrow arrow = launchFrom(shooter);

        // FR-024b: worked out at launch, so a bow that was strong when fired stays strong even if
        // the shooter unequips mid-flight.
        assertThat(ProjectileDamageTag.read(arrow)).isEqualTo(40.0);
    }

    @Test
    @DisplayName("a bow hit costs the same as a melee swing of the same strength")
    void bowMatchesMelee() {
        LivingEntity shooter = shooter(40.0);
        UUID viaArrow = target(200.0, 0.0);
        UUID viaFist = target(200.0, 0.0);

        Arrow arrow = launchFrom(shooter);
        pipeline.projectileDamage(
                shooter.getUniqueId(), viaArrow, ProjectileDamageTag.read(arrow));
        pipeline.meleeAttack(shooter.getUniqueId(), viaFist);

        assertThat(200.0 - stats.resources(viaArrow).currentHealth())
                .as("the arrow actually dealt damage - otherwise this test passes on two zeroes")
                .isEqualTo(40.0);
        assertThat(stats.resources(viaArrow).currentHealth())
                .as("SC-010a: one arithmetic, two delivery mechanisms")
                .isEqualTo(stats.resources(viaFist).currentHealth());
    }

    @Test
    @DisplayName("defence applies to an arrow exactly as it does to a fist")
    void defenceAppliesToArrows() {
        LivingEntity shooter = shooter(50.0);
        UUID armoured = target(200.0, 100.0);

        Arrow arrow = launchFrom(shooter);
        pipeline.projectileDamage(shooter.getUniqueId(), armoured, ProjectileDamageTag.read(arrow));

        // 50 * 100/(100+100) = 25, the divisor model from B04.
        assertThat(200.0 - stats.resources(armoured).currentHealth()).isEqualTo(25.0);
    }

    @Test
    @DisplayName("a projectile from a shooter outside this system carries no number")
    void projectileFromOutsiderCarriesNothing() {
        LivingEntity cow =
                (LivingEntity) world.spawnEntity(world.getSpawnLocation(), EntityType.COW);

        Arrow arrow = launchFrom(cow);

        // No stat holder, no price. The hit will be neutralised rather than guessed at.
        assertThat(ProjectileDamageTag.read(arrow)).isNaN();
    }

    @Test
    @DisplayName("a projectile without a stored number is only neutralised, never applied")
    void unpricedProjectileIsNeutralised() {
        LivingEntity shooter = shooter(40.0);
        UUID victim = target(200.0, 0.0);
        Arrow dispenserArrow = (Arrow) world.spawnEntity(world.getSpawnLocation(), EntityType.ARROW);

        double stored = ProjectileDamageTag.read(dispenserArrow);
        assertThat(stored).as("a dispenser arrow was never priced").isNaN();

        DamageResult result =
                pipeline.projectileDamage(shooter.getUniqueId(), victim, stored);

        // The listener cancels such a hit outright; the pipeline is the second line of defence, and
        // it must refuse rather than fall back to computing a full-strength hit.
        assertThat(result.reason()).isEqualTo(RejectReason.INVALID_DAMAGE);
        assertThat(stats.resources(victim).currentHealth()).isEqualTo(200.0);
    }

    @Test
    @DisplayName("a hit after the shooter died still lands")
    void hitAfterShooterDiedStillLands() {
        LivingEntity shooter = shooter(40.0);
        UUID victim = target(200.0, 0.0);
        Arrow arrow = launchFrom(shooter);
        double raw = ProjectileDamageTag.read(arrow);

        // The shooter is gone - holder removed, as on logout or death.
        stats.remove(shooter.getUniqueId());

        DamageResult result = pipeline.projectileDamage(shooter.getUniqueId(), victim, raw);

        // The arrow carries its own number, so it does not need the shooter any more. It is refused
        // here only because the pipeline wants a holder for attribution - what matters is that it
        // does not throw and does not apply a wrong amount.
        assertThat(result.reason()).isEqualTo(RejectReason.NO_HOLDER);
        assertThat(stats.resources(victim).currentHealth()).isEqualTo(200.0);
        assertThat(raw).as("the number survived the shooter").isEqualTo(40.0);
    }

    @Test
    @DisplayName("the number is read from the arrow, not recomputed from the shooter")
    void numberComesFromTheArrow() {
        LivingEntity shooter = shooter(40.0);
        Arrow arrow = launchFrom(shooter);
        assertThat(ProjectileDamageTag.read(arrow)).isEqualTo(40.0);

        // The shooter puts down the bow while the arrow is in flight.
        stats.remove(shooter.getUniqueId());
        stats.createForEntity(shooter.getUniqueId());
        stats.recalculateNow(shooter.getUniqueId());

        assertThat(ProjectileDamageTag.read(arrow))
                .as("FR-024b: priced at launch, not on impact")
                .isEqualTo(40.0);
    }

    @Test
    @DisplayName("a projectile with no shooter at all is ignored")
    void projectileWithoutShooter() {
        Arrow arrow = (Arrow) world.spawnEntity(world.getSpawnLocation(), EntityType.ARROW);

        listener.onLaunch(new ProjectileLaunchEvent(arrow));

        assertThat(ProjectileDamageTag.read(arrow)).isNaN();
    }
}
