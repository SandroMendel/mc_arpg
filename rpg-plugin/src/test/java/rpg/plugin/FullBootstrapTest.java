package rpg.plugin;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Arrays;

import org.bukkit.event.HandlerList;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import com.destroystokyo.paper.event.player.PlayerConnectionCloseEvent;

import rpg.core.module.BootstrapState;
import rpg.persistence.support.PostgresContainer;

/**
 * The whole plugin, started the way a server starts it, against a real database.
 *
 * <p>This is the only test that can catch a class of problem the module tests cannot: a module that
 * is written but never registered, a listener that is never subscribed, a configuration file that
 * ships with no default, a message key nothing declares. Every one of those passes every unit test
 * in the project and produces a plugin that does nothing on a real server.
 *
 * <p>It is also what proves the start order: B03 declares a dependency on B02, and if that order
 * were wrong the session module would build its repositories against pools that do not exist yet.
 *
 * <p>MockBukkit reports unimplemented operations as <em>aborted</em>, not failed, so a green run
 * that skipped everything is indistinguishable from a real one at a glance - the skipped count is
 * checked on every run of this module.
 */
class FullBootstrapTest {

    private ServerMock server;
    private RpgPlugin plugin;

    @BeforeEach
    void setUp() throws Exception {
        PostgresContainer.resetSchema();
        server = MockBukkit.mock();
        // A world has to exist before the plugin enables: B04's regeneration guard writes a game
        // rule to every loaded world, and with no worlds the assertion about it would pass
        // vacuously.
        server.addSimpleWorld("world");
        TestServerSetup.useTestDatabase();
        plugin = MockBukkit.load(RpgPlugin.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void theWholeStackStartsAndTheServerAcceptsPlayers() {
        assertThat(plugin.bootstrapState().phase()).isEqualTo(BootstrapState.Phase.READY);
        assertThat(plugin.bootstrapState().acceptsPlayers()).isTrue();
    }

    @Test
    void bothBlocksTablesExistBecauseTheMigrationsRanDuringStartup() {
        // B02's tables and B03's, in one schema, applied by the plugin itself rather than by a test.
        assertThat(PostgresContainer.tableExists("player_state")).isTrue();
        assertThat(PostgresContainer.tableExists("character")).isTrue();
        assertThat(PostgresContainer.tableExists("item_instance")).isTrue();
    }

    @Test
    void theSessionServicesAreResolvableThroughTheRegistry() {
        // How B04, B07, B12 and B14 will reach them - by interface, through B01's registry.
        assertThat(plugin.registry().findService(rpg.core.session.SessionRegistry.class)).isPresent();
        assertThat(plugin.registry().findService(rpg.core.session.CharacterRepository.class)).isPresent();
        assertThat(plugin.registry().findService(rpg.core.session.OfflinePlayerReader.class)).isPresent();
        assertThat(plugin.registry().findService(rpg.core.persistence.PlayerStateRepository.class))
                .isPresent();
    }

    @Test
    void theLifecycleItselfIsNotPublished() {
        // Deliberate: a block that could open a session could open a second one for the same player
        // (FR-014). Reading sessions is a service; driving them is not.
        assertThat(plugin.registry().findService(rpg.core.session.SessionLifecycle.class)).isEmpty();
    }

    @Test
    void everyEventTheLifecycleNeedsHasExactlyOneHandler() {
        // One entry and one exit. A second handler on any of these is how a duplicate load or a
        // duplicate unload gets introduced without anything looking wrong.
        assertThat(handlerCount(AsyncPlayerPreLoginEvent.getHandlerList()))
                .as("the bootstrap guard and the session loader")
                .isEqualTo(2);
        assertThat(handlerCount(PlayerJoinEvent.getHandlerList())).isEqualTo(1);
        assertThat(handlerCount(PlayerQuitEvent.getHandlerList())).isEqualTo(1);
        assertThat(handlerCount(PlayerConnectionCloseEvent.getHandlerList())).isEqualTo(1);
        assertThat(handlerCount(PlayerMoveEvent.getHandlerList())).isEqualTo(1);
    }

    @Test
    void theDefaultConfigurationFilesAreWrittenOutOnFirstStart() {
        Path dataFolder = plugin.getDataFolder().toPath();

        assertThat(dataFolder.resolve("persistence.yml")).exists();
        assertThat(dataFolder.resolve("session.yml")).exists();
        assertThat(dataFolder.resolve("stats.yml")).exists();
        assertThat(dataFolder.resolve("combat.yml")).exists();
        assertThat(dataFolder.resolve("messages.yml")).exists();
    }

    // --- B04 --------------------------------------------------------------
    //
    // ADR-012: a module that is not wired into the plugin is inert on a real server, however green
    // its own tests are. B02 and B03 were both fully tested and both unregistered. These four
    // assertions are the cheapest thing that would have caught it.

    @Test
    void theStatEngineIsResolvableThroughTheRegistry() {
        assertThat(plugin.registry().findService(rpg.core.stats.StatEngine.class)).isPresent();
    }

    @Test
    void theStatsTableExistsBecauseB04sMigrationRanToo() {
        assertThat(PostgresContainer.tableExists("character_stats")).isTrue();
    }

    @Test
    void theRegenerationGuardIsRegisteredSoNothingElseWritesTheHealthBar() {
        assertThat(handlerCount(EntityRegainHealthEvent.getHandlerList())).isEqualTo(1);
        assertThat(handlerCount(FoodLevelChangeEvent.getHandlerList())).isEqualTo(1);
        // Damage is handled - by B05, since it exists. That B04 is not the one doing it is asserted
        // where it can actually be told apart: NoDamageInterceptionTest scans the sources of
        // rpg/platform/stats and fails if a damage handler appears there (FR-030b). Counting
        // handlers here cannot distinguish owners, so this only checks that exactly one block took
        // the job.
        assertThat(handlerCount(EntityDamageEvent.getHandlerList())).isEqualTo(1);
    }

    @Test
    void naturalRegenerationIsOffInEveryWorld() {
        assertThat(server.getWorlds())
                .isNotEmpty()
                .allSatisfy(
                        world ->
                                assertThat(
                                                world.getGameRuleValue(
                                                        org.bukkit.GameRules.NATURAL_HEALTH_REGENERATION))
                                        .isFalse());
    }

    @Test
    void theStatEngineStartsWithEightAttributesAndNoHolders() {
        rpg.core.stats.StatEngine engine =
                plugin.registry().getService(rpg.core.stats.StatEngine.class);

        assertThat(rpg.core.stats.Attribute.count()).isEqualTo(8);
        assertThat(engine.holderCount()).isZero();
    }

    // --- B05 --------------------------------------------------------------
    //
    // Same reasoning as the B04 block above: a pipeline that is not wired in is inert, however
    // green its own tests are. For B05 there is a second trap on top - without the mob equipping,
    // the whole pipeline applies to nothing but players, which no unit test would notice.

    @Test
    void theCombatPipelineIsResolvableThroughTheRegistry() {
        assertThat(plugin.registry().findService(rpg.core.combat.CombatPipeline.class)).isPresent();
    }

    // B06. The same reason the block boundary is tested at all: B02 and B03 were once fully
    // implemented, fully unit-tested, and RpgPlugin.modules() returned an empty list (ADR-012). For
    // B06 the equivalent trap is the session attachment - without it load() and release() are dead
    // code and no character ever has a level.

    @Test
    void progressionIsResolvableThroughTheRegistry() {
        assertThat(plugin.registry().findService(rpg.core.progression.Progression.class))
                .as("B07 to B14 develop against this")
                .isPresent();
        assertThat(plugin.registry().findService(rpg.core.progression.PartyRegistry.class))
                .as("B14 builds its party commands on this")
                .isPresent();
    }

    @Test
    void progressionHooksIntoTheSessionLifecycle() {
        // The single most consequential wiring in B06. Without an attachment, load() and release()
        // are dead code: no character would ever have a level, and the promise against leaks would
        // be unproven. Every unit test in the block would still be green - which is exactly the
        // failure class ADR-012 exists for.
        assertThat(plugin.sessionLifecycle().attachmentIds())
                .as("progress is loaded on session open and released on close")
                .contains("progression")
                .as("and the party drops the player when the session ends")
                .contains("progression-party");
    }

    @Test
    void progressionIsWiredIntoTheStatEngineAndTheSession() {
        rpg.core.progression.Progression progression =
                plugin.registry().getService(rpg.core.progression.Progression.class);

        // The maximum level comes from the shipped curve, which proves the configuration was written
        // and read rather than defaulted somewhere.
        assertThat(progression.maxLevel()).isEqualTo(60);

        // A query on a character nobody loaded must answer rather than throw - five blocks depend on
        // that (FR-027).
        assertThat(progression.meetsLevel(java.util.UUID.randomUUID(), 1)).isFalse();
    }

    @Test
    void everyCombatEventHasExactlyOneHandler() {
        assertThat(handlerCount(EntityDamageEvent.getHandlerList()))
                .as("B05 intercepts damage; B04 must not")
                .isEqualTo(1);
        // ProjectileLaunchEvent extends EntitySpawnEvent and declares no HandlerList of its own, so
        // it SHARES one with CreatureSpawnEvent. The two cannot be counted separately - what this
        // asserts is that exactly two handlers sit on that shared list: projectile pricing and mob
        // equipping, one each.
        assertThat(handlerCount(org.bukkit.event.entity.EntitySpawnEvent.getHandlerList()))
                .as("projectile pricing and mob equipping share one handler list")
                .isEqualTo(2);
        assertThat(handlerCount(org.bukkit.event.entity.EntityDeathEvent.getHandlerList()))
                .as("vanilla loot and experience are suppressed here")
                .isEqualTo(2); // the death listener plus the mob equipment release
    }

    @Test
    void inventoryIsKeptOnDeathInEveryWorld() {
        assertThat(server.getWorlds())
                .isNotEmpty()
                .allSatisfy(
                        world ->
                                assertThat(
                                                world.getGameRuleValue(
                                                        org.bukkit.GameRules.KEEP_INVENTORY))
                                        .as("otherwise the equipment-damage penalty is meaningless")
                                        .isTrue());
    }

    @Test
    void everyVanillaDamageCauseHasADecision() {
        // The count is asserted in the platform test; here it is only about the mapping existing at
        // all once the plugin is up.
        var mapping = new rpg.platform.combat.VanillaDamageMapping(plugin.getLogger());
        for (var cause : EntityDamageEvent.DamageCause.values()) {
            assertThat(mapping.resolve(cause)).as(cause.name()).isNotNull();
        }
    }

    @Test
    void stoppingTheServerShutsEverythingDownWithoutThrowing() {
        server.getPluginManager().disablePlugin(plugin);

        assertThat(plugin.bootstrapState().phase()).isEqualTo(BootstrapState.Phase.SHUTTING_DOWN);
        assertThat(plugin.bootstrapState().acceptsPlayers()).isFalse();
    }

    // --- fixtures ---

    private static int handlerCount(HandlerList handlers) {
        return (int)
                Arrays.stream(handlers.getRegisteredListeners())
                        .filter(listener -> listener.getPlugin() instanceof RpgPlugin)
                        .count();
    }

}
