package rpg.platform.combat;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import rpg.core.combat.CombatConfig;
import rpg.core.combat.DefaultCombatPipeline;
import rpg.core.event.DefaultEventBus;
import rpg.core.stats.Attribute;
import rpg.core.stats.DefaultStatEngine;
import rpg.core.stats.StatConfig;
import rpg.platform.scheduler.ImmediateScheduler;

/**
 * T097-T099: creatures get values, and give them back (FR-019a, FR-019d, FR-019e, SC-010c,
 * SC-010d).
 *
 * <p>The last test is the one that has to pass before the load test is worth running at all: if
 * holders outlive their creatures, the load test measures the leak instead of the pipeline.
 *
 * <p>MockBukkit note, as everywhere in this module: an unimplemented operation is reported as an
 * <em>aborted</em> test, not a failing one, so the skipped count is checked separately after every
 * run.
 */
class MobEquipmentListenerTest {

    private static final Logger QUIET = quiet();

    private static Logger quiet() {
        Logger logger = Logger.getLogger("mob-equipment-test");
        logger.setLevel(Level.OFF);
        return logger;
    }

    private ServerMock server;
    private WorldMock world;
    private DefaultStatEngine stats;
    private DefaultCombatPipeline pipeline;
    private MobEquipmentListener listener;

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
                        CombatConfig.defaults(),
                        stats,
                        eventBus,
                        null,
                        Clock.systemUTC(),
                        QUIET);
        listener =
                new MobEquipmentListener(
                        stats,
                        pipeline,
                        new PaperMobStatProvider(CombatConfig.defaults(), StatConfig.defaults()),
                        QUIET);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("a hostile creature has its configured values immediately after spawning")
    void hostileCreatureIsEquipped() {
        var zombie = world.spawnEntity(world.getSpawnLocation(), EntityType.ZOMBIE);
        listener.equip((org.bukkit.entity.LivingEntity) zombie);

        assertThat(stats.findSnapshot(zombie.getUniqueId())).isPresent();
        assertThat(stats.value(zombie.getUniqueId(), Attribute.HEALTH)).isEqualTo(80.0);
        assertThat(stats.value(zombie.getUniqueId(), Attribute.DEFENSE)).isEqualTo(10.0);
        assertThat(stats.value(zombie.getUniqueId(), Attribute.PHYSICAL_DAMAGE)).isEqualTo(10.0);
    }

    @Test
    @DisplayName("it starts at full health, not at the pre-modifier maximum")
    void startsFull() {
        var zombie = world.spawnEntity(world.getSpawnLocation(), EntityType.ZOMBIE);
        listener.equip((org.bukkit.entity.LivingEntity) zombie);

        var view = stats.resources(zombie.getUniqueId());
        assertThat(view.maxHealth()).isEqualTo(80.0);
        assertThat(view.currentHealth())
                .as("equipping first and filling afterwards is the whole point of the order")
                .isEqualTo(80.0);
    }

    @Test
    @DisplayName("a type without its own entry gets the default set")
    void unknownTypeGetsDefaults() {
        var creeper = world.spawnEntity(world.getSpawnLocation(), EntityType.CREEPER);
        listener.equip((org.bukkit.entity.LivingEntity) creeper);
        assertThat(stats.value(creeper.getUniqueId(), Attribute.HEALTH)).isEqualTo(50.0);

        var blaze = world.spawnEntity(world.getSpawnLocation(), EntityType.BLAZE);
        listener.equip((org.bukkit.entity.LivingEntity) blaze);
        assertThat(stats.value(blaze.getUniqueId(), Attribute.HEALTH))
                .as("no entry of its own - the default set applies")
                .isEqualTo(60.0);
    }

    @Test
    @DisplayName("a peaceful creature is left outside the combat system entirely")
    void peacefulCreatureIsIgnored() {
        var cow = world.spawnEntity(world.getSpawnLocation(), EntityType.COW);

        assertThat(listener.wouldEquip(cow)).isFalse();
        listener.equip((org.bukkit.entity.LivingEntity) cow);
        assertThat(stats.findSnapshot(cow.getUniqueId())).isEmpty();
    }

    @Test
    @DisplayName("800 creatures that come and go leave no holder behind")
    void noHolderLeak() {
        for (int i = 0; i < 800; i++) {
            var zombie =
                    (org.bukkit.entity.LivingEntity)
                            world.spawnEntity(world.getSpawnLocation(), EntityType.ZOMBIE);
            listener.equip(zombie);
            listener.onRemove(new org.bukkit.event.entity.EntityRemoveEvent(
                    zombie, org.bukkit.event.entity.EntityRemoveEvent.Cause.DESPAWN));
        }

        assertThat(stats.holderCount())
                .as("a holder that outlives its creature makes the load test measure the leak")
                .isZero();
        assertThat(pipeline.trackedCounts()).containsExactly(0, 0, 0, 0);
    }

    @Test
    @DisplayName("equipping twice does not create a second holder")
    void equippingIsIdempotent() {
        var zombie =
                (org.bukkit.entity.LivingEntity)
                        world.spawnEntity(world.getSpawnLocation(), EntityType.ZOMBIE);

        listener.equip(zombie);
        listener.equip(zombie);

        assertThat(stats.holderCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("a player is never equipped here - that is B03's and B04's job")
    void playersAreNotEquipped() {
        var player = server.addPlayer();

        assertThat(listener.wouldEquip(player)).isFalse();
        assertThat(stats.findSnapshot(player.getUniqueId())).isEmpty();
    }

    @Test
    @DisplayName("the provider decides; an empty answer means no holder at all")
    void providerDecides() {
        MobEquipmentListener refusing =
                new MobEquipmentListener(
                        stats, pipeline, mobTypeKey -> java.util.Optional.empty(), QUIET);

        var zombie =
                (org.bukkit.entity.LivingEntity)
                        world.spawnEntity(world.getSpawnLocation(), EntityType.ZOMBIE);
        refusing.equip(zombie);

        assertThat(stats.findSnapshot(zombie.getUniqueId())).isEmpty();
        assertThat(UUID.randomUUID()).isNotNull();
    }
}
