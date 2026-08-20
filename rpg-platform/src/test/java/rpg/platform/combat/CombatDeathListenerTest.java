package rpg.platform.combat;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.GameRules;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
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
import rpg.core.stats.DefaultStatEngine;
import rpg.core.stats.StatConfig;
import rpg.platform.scheduler.ImmediateScheduler;

/** T063-T065: what happens around a death (FR-029 to FR-030b, SC-010b, SC-010f). */
class CombatDeathListenerTest {

    private static final Logger QUIET = quiet();

    private static Logger quiet() {
        Logger logger = Logger.getLogger("combat-death-test");
        logger.setLevel(Level.OFF);
        return logger;
    }

    private ServerMock server;
    private WorldMock world;
    private CombatDeathListener listener;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");

        var eventBus = new DefaultEventBus(QUIET);
        DefaultStatEngine stats =
                new DefaultStatEngine(
                        StatConfig.defaults(), new ImmediateScheduler(), eventBus, null, QUIET);
        DefaultCombatPipeline pipeline =
                new DefaultCombatPipeline(
                        CombatConfig.defaults(), stats, eventBus, null, Clock.systemUTC(), QUIET);
        listener = new CombatDeathListener(stats, pipeline, QUIET);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("inventory is kept on death in every world")
    void keepInventoryIsOn() {
        world.setGameRule(GameRules.KEEP_INVENTORY, false);

        listener.applyTo(server);

        assertThat(server.getWorlds())
                .isNotEmpty()
                .allSatisfy(
                        w ->
                                assertThat(w.getGameRuleValue(GameRules.KEEP_INVENTORY))
                                        .as(
                                                "otherwise losing the whole inventory hides the"
                                                        + " chosen penalty, which is equipment"
                                                        + " damage")
                                        .isTrue());
    }

    @Test
    @DisplayName("a world loaded later gets the same treatment")
    void laterWorldsToo() {
        WorldMock later = server.addSimpleWorld("nether");
        later.setGameRule(GameRules.KEEP_INVENTORY, false);

        listener.onWorldLoad(new org.bukkit.event.world.WorldLoadEvent(later));

        assertThat(later.getGameRuleValue(GameRules.KEEP_INVENTORY)).isTrue();
    }

    @Test
    @DisplayName("vanilla experience and loot are suppressed when a creature dies")
    void vanillaRewardsAreSuppressed() {
        LivingEntity zombie =
                (LivingEntity) world.spawnEntity(world.getSpawnLocation(), EntityType.ZOMBIE);
        List<ItemStack> drops = new ArrayList<>();
        drops.add(new ItemStack(org.bukkit.Material.ROTTEN_FLESH, 2));

        var damageSource =
                org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.GENERIC).build();
        var event =
                new org.bukkit.event.entity.EntityDeathEvent(zombie, damageSource, drops, 15);
        listener.onEntityDeath(event);

        assertThat(event.getDroppedExp())
                .as("progress comes from B06; a second experience bar is one too many")
                .isZero();
        assertThat(event.getDrops())
                .as("loot tables are B11's; vanilla drops would lie across them")
                .isEmpty();
    }

    @Test
    @DisplayName("the game rule is only written when it is not already set")
    void idempotentGameRule() {
        world.setGameRule(GameRules.KEEP_INVENTORY, true);

        listener.applyTo(server);

        assertThat(world.getGameRuleValue(GameRules.KEEP_INVENTORY)).isTrue();
    }
}
