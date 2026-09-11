package rpg.plugin;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;

import org.bukkit.block.BlockFace;
import org.bukkit.event.HandlerList;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

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
        // Und der Rueckbau lief beim echten Start mit: item_instance ist mit V11_1 weg (ADR-039).
        // Die Zusicherung wird umgedreht statt geloescht - sie beweist jetzt, dass eine
        // RUECKBAUENDE Migration im Bootstrap genauso greift wie eine aufbauende, und das war
        // vorher an keiner Stelle gezeigt.
        assertThat(PostgresContainer.tableExists("item_instance")).isFalse();
        assertThat(PostgresContainer.tableExists("character_inventory")).isTrue();
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
        // FOUR, and every one is meant: B03 freezes a player while their session loads, B07 freezes
        // one who has not chosen a class (ADR-020), B08 hands the mage his second jump back on
        // landing and interrupts a cast on movement. Different reasons, different lifetimes - and
        // none of them is a lifecycle entry, so the invariant this test protects is the assertions
        // above.
        //
        // What matters on the busiest event the server has is that each returns on field reads before
        // doing anything: a counter for the first two, ground state and a permission flag for B08.
        //
        // B09 is the fifth, and it holds to the same bargain: two integer comparisons on the block
        // coordinates the event already carries, no Chunk object and no allocation, and only then a
        // single table access - and only in the handful of chunks a border runs through
        // (MovementGuard, research.md R4). A zone lookup on every step would have been the one
        // addition this list should have refused.
        //
        // The sixth is B08's landing watcher, and it earns its place the same way: one int read says
        // "nobody is mid-leap", which is true of every player almost all of the time. It exists
        // because a leap's impact happens where the warrior comes down, and no timer can say when
        // that is - a jump over a cliff takes as long as the cliff is deep (FR-045d).
        // Der siebte ist B12s Aktivitaetszeitstempel (R7), und er ist der billigste von allen:
        // MONITOR, eine Zuweisung in eine vorbelegte Map, keine Bedingung davor und keine
        // Allokation. Genau deshalb ist er vertretbar - und genau deshalb zaehlt dieser Test mit,
        // damit der achte es begruenden muss.
        assertThat(handlerCount(PlayerMoveEvent.getHandlerList()))
                .as(
                        "B03's safe-state hold, B07's no-character hold, B08's double jump, its cast"
                                + " interruption and its landing watcher, B09's movement guard, and"
                                + " B12's activity timestamp")
                .isEqualTo(7);
    }

    @Test
    void theDefaultConfigurationFilesAreWrittenOutOnFirstStart() {
        Path dataFolder = plugin.getDataFolder().toPath();

        assertThat(dataFolder.resolve("persistence.yml")).exists();
        assertThat(dataFolder.resolve("session.yml")).exists();
        assertThat(dataFolder.resolve("stats.yml")).exists();
        assertThat(dataFolder.resolve("combat.yml")).exists();
        assertThat(dataFolder.resolve("progression.yml")).exists();
        assertThat(dataFolder.resolve("classes.yml")).exists();
        assertThat(dataFolder.resolve("abilities.yml")).exists();
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
        // handlers here cannot distinguish owners, so this only checks how many blocks took the job.
        //
        // TWO since B08. EntityDamageByEntityEvent declares no HandlerList of its own and therefore
        // SHARES this one: B05 prices the hit, and B08 refuses it outright when the player is holding
        // an ability item (FR-054). Without the second one a left click with the goat horn would deal
        // weapon damage.
        // THREE since B08 gained cast interruption: taking damage stops a cast (FR-042).
        assertThat(handlerCount(EntityDamageEvent.getHandlerList())).isEqualTo(3);
    }

    @Test
    void abilityProjectilesSettleOnImpact() {
        // Exactly one, and it belongs to B08. A fireball carries its values from the throw, so the
        // impact has to be caught somewhere - and a second owner of this event would mean two blocks
        // deciding what an arriving projectile does.
        assertThat(handlerCount(org.bukkit.event.entity.ProjectileHitEvent.getHandlerList()))
                .isEqualTo(1);
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
    void theStatEngineStartsWithTenAttributesAndNoHolders() {
        rpg.core.stats.StatEngine engine =
                plugin.registry().getService(rpg.core.stats.StatEngine.class);

        assertThat(rpg.core.stats.Attribute.count()).isEqualTo(10);
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
                .as(
                        "B05 prices the hit, B08 refuses it for an ability item and stops a cast on"
                                + " it - B04 must not be here")
                .isEqualTo(3);
        // ProjectileLaunchEvent and CreatureSpawnEvent both extend EntitySpawnEvent and declare no
        // HandlerList of their own, so all three share one list. They cannot be counted separately -
        // what this asserts is that exactly three handlers sit on that shared list: projectile
        // pricing, mob equipping, and B10's vanilla-spawn suppressor (research.md R1), one each.
        assertThat(handlerCount(org.bukkit.event.entity.EntitySpawnEvent.getHandlerList()))
                .as("projectile pricing, mob equipping and vanilla-spawn suppression share one handler list")
                .isEqualTo(3);
        assertThat(handlerCount(org.bukkit.event.entity.EntityDeathEvent.getHandlerList()))
                .as("vanilla loot and experience are suppressed here")
                // The death listener, the mob equipment release, and B10's HordeSweep - it sets a
                // boss's respawn timer on the same event as every other creature's death, with no
                // special handling of its own (T082).
                .isEqualTo(3);
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

    // --- B07 --------------------------------------------------------------
    //
    // T127, and the same trap as B06 one layer along: the block has a session attachment, four
    // listeners and a contributor, and every one of them is dead code until the plugin registers it.
    // ADR-012 exists because that has happened before.

    @Test
    void theClassRegistryIsResolvableThroughTheRegistry() {
        assertThat(plugin.registry().findService(rpg.core.classes.ClassRegistry.class))
                .as("B08 binds abilities against this, B11 and B13 read the ladders")
                .isPresent();
    }

    @Test
    void theClassProgressTableExistsBecauseB07sMigrationRanToo() {
        assertThat(PostgresContainer.tableExists("character_class_progress")).isTrue();
    }

    // --- B08 --------------------------------------------------------------
    //
    // T032, T135: the same trap one block along. B08 owns a configuration, a repository, a place in
    // the flush order, a session attachment and a read facade - and every one of them is dead code
    // until the plugin wires it. ADR-012 exists because that has happened before.

    @Test
    void theAbilityRegistryIsResolvableThroughTheRegistry() {
        assertThat(plugin.registry().findService(rpg.core.ability.AbilityRegistry.class))
                .as("B12 counts through this and B13 draws from it")
                .isPresent();
    }

    @Test
    void theAbilityTableExistsBecauseB08sMigrationRanToo() {
        assertThat(PostgresContainer.tableExists("character_abilities")).isTrue();
    }

    @Test
    void theAbilityConfigurationWasLoadedRatherThanDefaulted() {
        rpg.core.ability.AbilityRegistry abilities =
                plugin.registry().getService(rpg.core.ability.AbilityRegistry.class);

        // Reading the two values B08 owns itself proves abilities.yml was written out, parsed and
        // validated. The rates are deliberately NOT here - they are attributes and live in
        // classes.yml (ADR-023).
        assertThat(abilities.config().globalCooldown()).isEqualTo(java.time.Duration.ofMillis(750));
        assertThat(abilities.config().healthCombatFactor()).isEqualTo(0.20);
        assertThat(abilities.config().manaCombatFactor()).isEqualTo(0.35);
    }

    @Test
    void abilitiesDeclareTheDependencyThatKeepsThemAfterTheClasses() {
        // The order is not cosmetic: resolving a class binding needs both files, and it is the promise
        // B07 could not keep - there an ability id travels as an opaque string. Asserting the declared
        // dependency rather than the resulting list tests the mechanism that enforces it; a list that
        // happens to be in the right order would still break the day somebody reshuffles it.
        assertThat(rpg.persistence.ability.AbilityModule.DEPENDENCIES)
                .as("the cross-check runs at startup and needs classes.yml already loaded")
                .contains("classes");
    }

    @Test
    void allEighteenAbilitiesAreLoadedAndBoundToTheThreeClasses() {
        rpg.core.ability.AbilityRegistry abilities =
                plugin.registry().getService(rpg.core.ability.AbilityRegistry.class);

        // The cross-check (V25 to V28) already ran at startup and would have refused the start, so
        // this is not a second validation - it is the proof that it ran with something in it. While
        // abilities.yml was empty the check skipped itself with a warning, and a green start proved
        // nothing at all.
        assertThat(abilities.config().size()).isEqualTo(18);
        for (rpg.core.session.CharacterClass id : rpg.core.session.CharacterClass.values()) {
            assertThat(abilities.abilitiesOf(id)).as("%s has six", id).hasSize(6);
        }
    }

    @Test
    void abilitiesHookIntoTheSessionLifecycle() {
        assertThat(plugin.sessionLifecycle().attachmentIds())
                .as("ranks and cooldowns are loaded on session open and released on close")
                .contains(rpg.persistence.ability.AbilityModule.ID);
    }

    @Test
    void theAbilityInterceptorsHangInTheDamagePipeline() {
        // ADR-012: a module whose own tests are green but which is not wired in has no effect. These
        // three are B08's whole passive half - evasion refuses damage at MODIFIERS, lifesteal reads
        // the mitigated amount at APPLICATION, and Second Life cancels the lethal blow before
        // changeHealth. A missing one is silent: the ability simply never happens.
        java.util.List<String> ids = new java.util.ArrayList<>();
        for (rpg.core.combat.PipelineStage stage : rpg.core.combat.PipelineStage.values()) {
            plugin.combatPipeline()
                    .interceptorsAt(stage)
                    .forEach(interceptor -> ids.add(interceptor.id()));
        }

        assertThat(ids)
                .contains(
                        "abilities.on-damage-taken",
                        "abilities.on-damage-dealt",
                        "abilities.on-death");
        assertThat(ids)
                .as(
                        "und der Schild. Er fehlte hier, und deshalb fiel ein Jahr lang nicht auf, "
                                + "dass sein Vorrat sich fuellte und ihn niemand las - Block und "
                                + "Magieschild waren acht Sekunden lang nichts")
                .contains("abilities.shield");
    }

    @Test
    void classesHookIntoTheSessionLifecycle() {
        assertThat(plugin.sessionLifecycle().attachmentIds())
                .as("the reached tiers are loaded on session open and released on close")
                .contains("classes");
    }

    @Test
    void theCalculationAttachesAfterEverySupplierOfBaseValues() {
        // The ordering bug this asserts against is silent and expensive: B04 calculates, B06 and B07
        // supply, and restoreResources clamps the stored health against whatever B04 computed. Run in
        // module start order, B04 would go first and a level 60 warrior would come back at the bare
        // value from stats.yml. See SessionAttachment.order().
        java.util.List<String> ids = plugin.sessionLifecycle().attachmentIds();

        assertThat(ids).contains("stats", "progression", "classes");
        assertThat(ids.indexOf("stats"))
                .as("B04 calculates from what B06 and B07 loaded, so it attaches after both")
                .isGreaterThan(ids.indexOf("progression"))
                .isGreaterThan(ids.indexOf("classes"));
    }

    @Test
    void theClassReplacesTheLevelGrowthRatherThanAddingToIt() {
        // FR-003, and the failure mode is the reason this is asserted at the bootstrap rather than in a
        // unit test: B06 registers a class-neutral level growth, B07 registers a per-class one, and both
        // modules start. If B07 stopped removing B06's, every character would simply be too strong -
        // nothing throws, and every unit test in both blocks stays green.
        assertThat(plugin.statEngine().baseContributorIds())
                .as("B07 supplies the growth per class")
                .contains("class")
                .as("so B06's class-neutral growth must be gone")
                .doesNotContain(rpg.core.progression.LevelStatContributor.ID);
    }

    @Test
    void theClassLoadedItsShippedConfigurationRatherThanADefault() {
        rpg.core.classes.ClassRegistry classes =
                plugin.registry().getService(rpg.core.classes.ClassRegistry.class);

        // Ladder lengths differ per class and are configuration, not code (ADR-017). Reading them back
        // proves classes.yml was written out, parsed and validated - the caps check in the module runs
        // against these very values.
        assertThat(
                        classes.ladder(
                                        rpg.core.session.CharacterClass.WARRIOR,
                                        rpg.core.classes.LadderSlot.ARMOR)
                                .length())
                .isEqualTo(6);
        assertThat(
                        classes.ladder(
                                        rpg.core.session.CharacterClass.MAGE,
                                        rpg.core.classes.LadderSlot.WEAPON)
                                .length())
                .isEqualTo(7);
    }

    @Test
    void everyClassEventHasItsHandler() {
        // Six handlers sit on InventoryClickEvent, each with its own job: the equipment lock
        // refuses to move a bound item (ADR-018), the class selection refuses everything while its
        // menu is open, and B08b's currency window refuses everything while its own is - a ledger
        // row is a fact, not an item somebody can pocket (ADR-028). B09's waypoint window is the
        // fourth, for the same reason and under the same temporary licence (ADR-032). B11's vendor
        // is the fifth: a window where stacks can be moved is a window items can be taken from
        // without paying (FR-060 to FR-064).
        //
        // Counted rather than merely "at least one": a handler that quietly disappears is how a
        // bound item becomes removable, and nobody notices until it has happened in play.
        assertThat(handlerCount(org.bukkit.event.inventory.InventoryClickEvent.getHandlerList()))
                .as(
                        "the equipment lock, the class selection, the currency, waypoint and vendor"
                                + " windows, the repair-route lock, B12's activity timestamp,"
                                + " B12's menu guard, and B13's character sheet")
                // NEUN seit B13. Die Uebersicht ist zum Lesen da (FR-050): ohne ihren Waechter
                // koennte ein Spieler die Attribute herausnehmen und behalten - ein Fenster ist in
                // Vanilla ein Inventar, und ein Inventar gibt her, was man anklickt.
                .isEqualTo(9);
        assertThat(handlerCount(org.bukkit.event.player.PlayerDropItemEvent.getHandlerList()))
                .as("dropping is off for every item, bound or not (ADR-018)")
                .isEqualTo(1);
        assertThat(handlerCount(org.bukkit.event.inventory.InventoryCloseEvent.getHandlerList()))
                .as(
                        "the class selection reopens itself; the currency, waypoint and vendor"
                                + " windows, B12's leaderboard and B13's character sheet just"
                                + " forget their state")
                // SECHS seit B13. Die Uebersicht merkt sich nur, WER sie offen hat - der
                // zwischengespeicherte Inhalt geht erst beim Sitzungsende weg, ueber den
                // SessionObserver und NICHT ueber PlayerQuitEvent: B03 besitzt den Lebenszyklus
                // und laesst dort genau einen Handler zu (FR-007).
                .isEqualTo(6);
    }

    // --- character inventory (B07 groundwork for B11) ---------------------

    @Test
    void theInventoryTableExistsBecauseItsMigrationRanToo() {
        assertThat(PostgresContainer.tableExists("character_inventory")).isTrue();
    }

    @Test
    void theInventoryHooksIntoTheSessionLifecycle() {
        // Without the attachment nothing is loaded on entry and nothing is marked on the way out - the
        // table would stay empty and every logout would look like an empty inventory.
        assertThat(plugin.sessionLifecycle().attachmentIds())
                .as("stored contents are loaded on entry and marked on close")
                .contains("inventory");
    }

    @Test
    void theInventoryRepositoryIsResolvableThroughTheRegistry() {
        assertThat(plugin.registry().findService(rpg.core.inventory.CharacterInventoryRepository.class))
                .as("B11 takes this over")
                .isPresent();
    }

    // --- B08b: currency and account ---

    @Test
    void bothCurrencyTablesExistBecauseTheirMigrationsRan() {
        assertThat(PostgresContainer.tableExists("character_balance")).isTrue();
        assertThat(PostgresContainer.tableExists("coin_ledger")).isTrue();
    }

    @Test
    void theCurrencyIsResolvableThroughTheRegistry() {
        assertThat(plugin.registry().findService(rpg.core.currency.Currency.class))
                .as("B07, B08, B11 and B12 are built against this")
                .isPresent();
        assertThat(plugin.registry().findService(rpg.core.currency.CurrencyAdmin.class))
                .as("B14 takes the command over and keeps this")
                .isPresent();
    }

    // --- B13: UI, HUD und Texte -------------------------------------------

    @Test
    void theUiModuleIsWiredAndItsConfigurationLoaded() {
        // Modultests reichen nicht: das Modul muss verdrahtet sein und der Server damit starten.
        // Genau hier faellt auf, wenn ui.yml nicht ausgeliefert wird oder die Startpruefung aus
        // FR-032 gegen eine leere Faehigkeitsliste laeuft.
        // Ueber die Startreihenfolge und nicht ueber einen Dienst: B13 REGISTRIERT KEINEN. Es
        // zeichnet nur, und ein Block, den niemand aufruft, braucht keine Schnittstelle in der
        // Registry - er ist der letzte der Kette (Blocksteckbrief: "Benoetigt von: -").
        assertThat(plugin.registry().resolveStartOrder())
                .as("UiModule ist verdrahtet und hat gestartet")
                .contains("ui");
    }

    @Test
    void theUiModuleStartsAfterTheAbilities() {
        // Seine EINZIGE Abhaengigkeit, und sie ist verdient: die Startpruefung aus FR-032 liest
        // B08s Verzeichnis. Ohne diese Reihenfolge liefe sie gegen eine leere Liste und pruefte
        // still nichts - dieselbe Falle, die StatisticsModule bei den Belohnungsvorlagen abfaengt.
        java.util.List<String> order = plugin.registry().resolveStartOrder();

        assertThat(order.indexOf("ui")).isGreaterThan(order.indexOf("abilities"));
    }

    @Test
    void theCharCommandIsDeclared() {
        // Bis B14 stand hier getCommand("char") != null - der plugin.yml-Eintrag und die
        // Verdrahtung mussten sich einig sein, und das war nachsehbar. Seit dem Umzug auf
        // Brigadier (T034) gibt es keinen Eintrag mehr, und MockBukkit bildet den
        // LifecycleEventManager nicht ab.
        //
        // WAS DIESER TEST NOCH BEWEIST: dass der Start /char angemeldet hat, mit seinem Recht und
        // mit Spielerbezug. WAS ER NICHT MEHR BEWEIST: dass Brigadier daraus einen aufrufbaren
        // Knoten macht. Das kann nur der echte Server (quickstart §5) - und genau deshalb gehoert
        // der Serverlauf in diese Story und nicht ans Ende des Blocks.
        assertThat(declared("char"))
                .isPresent()
                .get()
                .satisfies(
                        node -> {
                            assertThat(node.permissionOrNone())
                                    .contains(rpg.plugin.command.CharacterSheetCommand.PERMISSION);
                            assertThat(node.requiresPlayer()).isTrue();
                            assertThat(node.arguments()).isEmpty();
                        });
    }

    /** Der angemeldete Knoten dieses Namens, falls es ihn gibt. */
    private java.util.Optional<rpg.plugin.command.framework.RpgCommand> declared(String name) {
        return plugin.declaredCommandsForTest().stream()
                .filter(node -> node.name().equals(name))
                .findFirst();
    }

    @Test
    void thehudTickIsTheOnlyOneAndTheActionBarNoLongerRunsItsOwn() throws Exception {
        // R1: B13 ERWEITERT den Takt aus StatusActionBar.startRefresh und legt keinen zweiten an.
        // startRefresh ist deshalb ENTFALLEN - waere es noch da, liefen zwei Durchlaeufe je
        // Sekunde, und welcher zuletzt sendet, haenge an der Registrierungsreihenfolge.
        assertThat(rpg.platform.hud.StatusActionBar.class.getDeclaredMethods())
                .as("startRefresh ist zu HudTick geworden")
                .noneMatch(method -> method.getName().equals("startRefresh"));
    }

    @Test
    void theuiBlockRegisteredNoSchema() {
        // SC-011 im laufenden Bootstrap: B13 legt keine Tabelle an. UiPersistsNothingTest prueft
        // die Quellen, dieser Test den echten Start - beides ist noetig, und nur das zweite faende
        // eine Migration, die jemand ausserhalb des B13-Pakets abgelegt hat.
        assertThat(PostgresContainer.tableExists("ui_settings")).isFalse();
        assertThat(PostgresContainer.tableExists("player_hud")).isFalse();
    }

    @Test
    void theCoinsCommandIsDeclared() {
        // Der interessanteste der sechs: /coins hat Unterkommandos UND eigene Formen (T037). Die
        // Regel aus T014 - „eine Verzweigung hat keine eigene Ausfuehrung" - musste dafuer
        // nachgeben; sie war strenger als der Vertrag und strenger als Brigadier.
        assertThat(declared("coins"))
                .isPresent()
                .get()
                .satisfies(
                        node -> {
                            assertThat(node.permissionOrNone())
                                    .as("die Wurzel traegt das GRUNDrecht, nicht das Admin-Recht")
                                    .contains(rpg.plugin.command.CoinsCommand.PERMISSION_BALANCE);
                            assertThat(node.isBranch()).isTrue();
                            assertThat(node.action())
                                    .as("und hat trotzdem eigene Formen: /coins und /coins <spieler>")
                                    .isNotNull();
                            assertThat(node.children())
                                    .extracting(rpg.plugin.command.framework.RpgCommand::name)
                                    .containsExactlyInAnyOrder("set", "add", "remove");
                            assertThat(node.children())
                                    .allSatisfy(
                                            child ->
                                                    assertThat(child.permissionOrNone())
                                                            .as("jeder Eingriff braucht das Admin-Recht")
                                                            .contains(
                                                                    rpg.plugin.command.CoinsCommand
                                                                            .PERMISSION_ADMIN));
                            assertThat(node.children())
                                    .allSatisfy(
                                            child ->
                                                    assertThat(child.requiresPlayer())
                                                            .as("ein Eingriff geht von der Konsole")
                                                            .isFalse());
                        });
    }

    @Test
    void theXpCommandIsDeclared() {
        // Das letzte der sechs (T038). Eine reine Verzweigung: /xp allein tut nichts, jeder der
        // drei Zweige tut etwas - und jeder laeuft von der Konsole, weil eine Korrektur genau
        // dort gemacht wird.
        assertThat(declared("xp"))
                .isPresent()
                .get()
                .satisfies(
                        node -> {
                            assertThat(node.permissionOrNone())
                                    .contains(rpg.plugin.command.XpCommand.PERMISSION);
                            assertThat(node.isBranch()).isTrue();
                            assertThat(node.action())
                                    .as("/xp allein ist unvollstaendig, nicht wirkungslos")
                                    .isNull();
                            assertThat(node.children())
                                    .extracting(rpg.plugin.command.framework.RpgCommand::name)
                                    .containsExactlyInAnyOrder("give", "take", "set");
                            assertThat(node.children())
                                    .allSatisfy(
                                            child ->
                                                    assertThat(child.requiresPlayer()).isFalse());
                        });
    }

    @Test
    void allCommandsAreDeclared() {
        // Der Sammelbeweis fuer FR-005: die sechs bestehenden Kommandos bleiben erhalten, und die
        // neuen Admin-Gruppen haengen gemeinsam unter /rpg. Ein verlorener Knoten faellt hier auf.
        assertThat(plugin.declaredCommandsForTest())
                .extracting(rpg.plugin.command.framework.RpgCommand::name)
                .containsExactlyInAnyOrder("char", "coins", "stats", "top", "trash", "xp", "rpg");
    }

    @Test
    void everyB14CommandPathIsPresentOnTheLiveTree() {
        assertThat(commandPaths())
                .as("US3-US8: jedes Spieler- und Admin-Kommando muss verdrahtet sein")
                .containsExactlyInAnyOrder(
                        "char",
                        "coins",
                        "coins set",
                        "coins add",
                        "coins remove",
                        "stats",
                        "top",
                        "trash",
                        "xp",
                        "xp give",
                        "xp take",
                        "xp set",
                        "rpg",
                        "rpg item",
                        "rpg item give",
                        "rpg mob",
                        "rpg mob spawn",
                        "rpg set",
                        "rpg set level",
                        "rpg set xp",
                        "rpg set class",
                        "rpg inspect",
                        "rpg inspect sheet",
                        "rpg inspect statistics",
                        "rpg inspect inventory",
                        "rpg inspect session",
                        "rpg audit",
                        "rpg reload");
    }

    /**
     * T044 — <b>die Syntax der sechs ist unveraendert</b> (FR-005, SC-008).
     *
     * <p>Die Sollwerte sind die {@code usage:}-Zeilen, die bis zum Umzug in {@code plugin.yml}
     * standen; der Block ist geloescht (T040), also gaebe es sonst nichts mehr, woran sich
     * „unveraendert" messen liesse. Sie stehen deshalb hier woertlich.
     *
     * <p><b>Der Test steht in dieser Klasse und nicht in einer eigenen</b>, weil er den ECHTEN
     * Baum braucht. Ein erster Anlauf baute die Kommandos mit null-Mitarbeitern nach — die
     * Konstruktoren pruefen ihre Mitarbeiter aber mit {@code requireNonNull}, und das zu Recht.
     * Ein nachgebauter Baum haette ohnehin nur bewiesen, dass der Nachbau stimmt.
     *
     * <p>Er prueft die <em>Form</em>: welche Woerter, welche Argumente, welche davon Pflicht. Dass
     * die <em>Ausgabe</em> dieselbe ist, koennen nur die Abnahmeschritte aus B08b, B11, B12 und
     * B13 — und SC-008 sagt genau das: keiner von ihnen muss angepasst werden.
     */
    @Test
    void thesixCommandsKeptTheirSyntax() {
        // char   usage: '/char'
        assertThat(declared("char").orElseThrow().arguments()).isEmpty();

        // trash  usage: '/trash (again to confirm)'  - '(again to confirm)' war eine Erklaerung,
        //        kein Argument: die Bestaetigung ist der zweite Aufruf.
        assertThat(declared("trash").orElseThrow().arguments()).isEmpty();

        // top    usage: '/top [board] [period]'
        assertThat(argumentNames("top")).containsExactly("board", "period");
        assertThat(declared("top").orElseThrow().arguments())
                .allSatisfy(argument -> assertThat(argument.required()).isFalse());

        // stats  usage: '/stats [period]'  - schon damals unvollstaendig: B12-FR-044 laesst auch
        //        '/stats <spieler> [period]' zu, und der Code konnte es. Die Zeile beschrieb das
        //        Kommando falsch; ein weiterer Grund, warum usage: nicht mitgezogen ist.
        assertThat(argumentNames("stats")).containsExactly("target", "period");

        // xp     usage: '/xp give|take <player> <amount> | /xp set <player> <level> [xp]'
        assertThat(declared("xp").orElseThrow().arguments())
                .as("/xp allein nimmt nichts - es verzweigt nur")
                .isEmpty();
        for (String verb : java.util.List.of("give", "take")) {
            assertThat(childArgumentNames("xp", verb)).as(verb).containsExactly("player", "amount");
            assertThat(child("xp", verb).arguments())
                    .allSatisfy(argument -> assertThat(argument.required()).isTrue());
        }
        assertThat(childArgumentNames("xp", "set")).containsExactly("player", "level", "xp");
        assertThat(child("xp", "set").arguments().get(2).required())
                .as("[xp] stand in eckigen Klammern - also optional")
                .isFalse();

        // coins  usage: '/coins | /coins <player> | /coins set|add|remove <player> <class> <amount>'
        assertThat(argumentNames("coins"))
                .as("ein optionales Argument deckt /coins und /coins <spieler>")
                .containsExactly("player");
        assertThat(declared("coins").orElseThrow().arguments().get(0).required()).isFalse();
        for (String verb : java.util.List.of("set", "add", "remove")) {
            assertThat(childArgumentNames("coins", verb))
                    .as(verb)
                    .containsExactly("player", "class", "amount");
        }
    }

    /**
     * T054 — <b>jedes Recht am LEBENDEN Baum steht im Deskriptor</b> (FR-010, FR-035).
     *
     * <p>{@code DeclaredPermissionsGuardTest} prüft dasselbe über einen Quellscan. Dieser hier
     * prüft es am fertig gebauten Baum, und das ist nicht dieselbe Frage: der Quellscan findet
     * jedes Recht, das <em>irgendwo im Code steht</em>; dieser findet die, die ein Kommando
     * <em>wirklich trägt</em> — auch wenn es sie zusammensetzt, statt sie hinzuschreiben.
     *
     * <p>Die explizite US3-US8-Abdeckung steht direkt darunter: sie prüft nicht nur, dass ein Recht
     * im Deskriptor vorkommt, sondern dass jedes Werkzeug und jeder Pfad am lebenden Baum hängt.
     */
    @Test
    void everyPermissionOnTheLiveTreeIsDeclared() {
        java.util.Set<String> declared = permissionsFromDescriptor();
        java.util.List<String> demanded = new java.util.ArrayList<>();
        for (rpg.plugin.command.framework.RpgCommand root : plugin.declaredCommandsForTest()) {
            collectPermissions(root, demanded);
        }

        assertThat(demanded).as("sechs Kommandos ohne ein einziges Recht waeren verdaechtig").isNotEmpty();
        assertThat(declared)
                .as("ein Recht, das kein plugin.yml-Eintrag deckt, wirkt je nach Server anders")
                .containsAll(demanded);

        assertThat(declared)
                .as("US3-US8: alle neuen Admin-Rechte muessen auslieferbar sein")
                .contains(
                        "rpg.admin.item.give",
                        "rpg.admin.mob.spawn",
                        "rpg.admin.set.class",
                        "rpg.admin.reload",
                        "rpg.admin.inspect.sheet",
                        "rpg.admin.inspect.statistics",
                        "rpg.admin.inspect.inventory",
                        "rpg.admin.inspect.session",
                        "rpg.admin.audit");
    }

    private java.util.List<String> commandPaths() {
        java.util.List<String> paths = new java.util.ArrayList<>();
        for (rpg.plugin.command.framework.RpgCommand root : plugin.declaredCommandsForTest()) {
            collectPaths(root, root.name(), paths);
        }
        return paths;
    }

    private static void collectPaths(
            rpg.plugin.command.framework.RpgCommand node,
            String path,
            java.util.List<String> into) {
        into.add(path);
        node.children().forEach(child -> collectPaths(child, path + " " + child.name(), into));
    }

    private static void collectPermissions(
            rpg.plugin.command.framework.RpgCommand node, java.util.List<String> into) {
        node.permissionOrNone().ifPresent(into::add);
        node.children().forEach(child -> collectPermissions(child, into));
    }

    @SuppressWarnings("unchecked")
    private static java.util.Set<String> permissionsFromDescriptor() {
        try (java.io.InputStream stream = FullBootstrapTest.class.getResourceAsStream("/plugin.yml")) {
            java.util.Map<String, Object> descriptor =
                    new org.yaml.snakeyaml.Yaml()
                            .load(
                                    new String(
                                            stream.readAllBytes(),
                                            java.nio.charset.StandardCharsets.UTF_8));
            return ((java.util.Map<String, Object>) descriptor.get("permissions")).keySet();
        } catch (java.io.IOException unreadable) {
            throw new IllegalStateException(unreadable);
        }
    }

    private java.util.List<String> argumentNames(String command) {
        return declared(command).orElseThrow().arguments().stream()
                .map(rpg.plugin.command.framework.Argument::name)
                .toList();
    }

    private rpg.plugin.command.framework.RpgCommand child(String parent, String name) {
        return declared(parent).orElseThrow().children().stream()
                .filter(node -> node.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("kein Unterkommando " + parent + " " + name));
    }

    private java.util.List<String> childArgumentNames(String parent, String name) {
        return child(parent, name).arguments().stream()
                .map(rpg.plugin.command.framework.Argument::name)
                .toList();
    }

    @Test
    void theTopCommandIsDeclared() {
        // Wie bei /char und /stats: die plugin.yml-Pruefung ist mit dem Umzug weggefallen (T036).
        // Die Berechtigung steht weiter auf default: true - ein Recht, das erst vergeben werden
        // muss, waere auf jedem frisch aufgesetzten Server eine stumme Funktion.
        assertThat(declared("top"))
                .isPresent()
                .get()
                .satisfies(
                        node -> {
                            assertThat(node.permissionOrNone())
                                    .contains(rpg.plugin.command.TopCommand.PERMISSION);
                            assertThat(node.requiresPlayer()).isTrue();
                            assertThat(node.arguments())
                                    .as("/top [tafel|score] [zeitraum]")
                                    .hasSize(2);
                            assertThat(node.rateLimitOrNone())
                                    .as("FR-032: /top fragt die Datenbank")
                                    .isPresent();
                        });
    }

    @Test
    void theStatisticsCommandIsDeclared() {
        // Wie bei /char: die plugin.yml-Pruefung ist mit dem Umzug weggefallen (T035). Was hier
        // NEU dazukommt und vorher nicht pruefbar war: die beiden Argumente und die Sperrzeit.
        assertThat(declared("stats"))
                .isPresent()
                .get()
                .satisfies(
                        node -> {
                            assertThat(node.permissionOrNone())
                                    .contains(rpg.plugin.command.StatisticsCommand.PERMISSION);
                            assertThat(node.requiresPlayer()).isTrue();
                            assertThat(node.arguments())
                                    .as("/stats [zeitraum|spieler] [zeitraum] - beide optional")
                                    .hasSize(2)
                                    .allSatisfy(
                                            argument ->
                                                    assertThat(argument.required()).isFalse());
                            assertThat(node.rateLimitOrNone())
                                    .as("FR-032: /stats fragt die Datenbank")
                                    .isPresent();
                        });
    }

    @Test
    void thePickupAndWindowListenersAreRegistered() {
        // Two handlers on InventoryClickEvent belong to the currency window and the class selection;
        // what matters here is that the currency one is among them at all.
        assertThat(handlerCount(org.bukkit.event.player.PlayerAttemptPickupItemEvent.getHandlerList()))
                .as("without this a coin pile is an item a player can pocket")
                .isGreaterThanOrEqualTo(1);
    }

    @Test
    void stoppingTheServerShutsEverythingDownWithoutThrowing() {
        server.getPluginManager().disablePlugin(plugin);

        assertThat(plugin.bootstrapState().phase()).isEqualTo(BootstrapState.Phase.SHUTTING_DOWN);
        assertThat(plugin.bootstrapState().acceptsPlayers()).isFalse();
    }

    // --- B08: eine Fähigkeit, ausgelöst wie ein Spieler sie auslöst ---

    @Test
    void aRightClickInThinAirTriggersTheAbilityAndSpendsMana() {
        // DER TEST, DEN ES NICHT GAB - und dessen Fehlen drei Fehlerberichte gekostet hat.
        //
        // Jeder Baustein von B08 war einzeln geprüft und grün. Was niemand fuhr, war die KETTE:
        // Klick -> Listener -> Naht im Plugin -> Runtime -> Stat-Engine. In dieser Kette saßen zwei
        // Fehler übereinander, und jeder allein hätte gereicht, damit auf dem Server nichts passiert:
        //
        //   1. Das Plugin reichte die CHARAKTER-Id in den Runtime, der Engine ist nach HALTER
        //      verschlüsselt. Jeder Aufruf warf, der Listener fing es ab wie vorgesehen.
        //   2. Der Listener hörte mit ignoreCancelled zu. Ein Rechtsklick in die LUFT ist von Geburt
        //      an abgebrochen - ohne Block ist useInteractedBlock DENY -, also erreichte ihn genau
        //      der Klick nie, den ein Spieler im Kampf macht.
        //
        // Deshalb steht dieser Test hier und nicht in rpg-core: nur hier ist die Verdrahtung echt,
        // und nur hier ist der Klick ein echtes Bukkit-Ereignis statt eines Methodenaufrufs.
        PlayerMock player = enterWarrior();
        double before = plugin.statEngine().resources(player.getUniqueId()).currentMana();
        assertThat(before).as("ein Charakter startet mit Mana").isGreaterThan(0.0);

        ItemStack ability = firstAbilityItem(player);
        assertThat(ability).as("die Hotbar wurde beim Eintritt belegt (FR-055)").isNotNull();

        PlayerInteractEvent click =
                new PlayerInteractEvent(
                        player,
                        Action.RIGHT_CLICK_AIR,
                        ability,
                        // KEIN Block - das ist der Punkt. Genau dieser Fall kam nie an.
                        null,
                        BlockFace.SELF,
                        EquipmentSlot.HAND);
        assertThat(click.isCancelled())
                .as("Bukkit hält einen Luftklick von Anfang an für abgebrochen - hier bewiesen, "
                        + "nicht behauptet")
                .isTrue();

        server.getPluginManager().callEvent(click);

        assertThat(plugin.statEngine().resources(player.getUniqueId()).currentMana())
                .as("der Klick hat gekostet - er ist also angekommen")
                .isLessThan(before);
        assertThat(player.nextMessage())
                .as("und wurde nicht abgelehnt - eine Ablehnung würde einen Text schicken")
                .isNull();
    }

    @Test
    void aWoundedCharacterHealsWithoutDoingAnything() {
        // Die zweite Hälfte desselben Fehlers. Die Regeneration ritt auf dem richtigen Sweep, bekam
        // aber Charakter-Ids, warf bei jedem Charakter und wurde stumm geschluckt - ein Sweep, der
        // sauber lief und nichts tat.
        PlayerMock player = enterWarrior();
        rpg.core.ability.ResourceRegeneration regeneration =
                plugin.abilityRegeneration();
        UUID characterId =
                plugin.statEngine().characterIdOf(player.getUniqueId()).orElseThrow();

        plugin.statEngine().changeHealth(player.getUniqueId(), -100.0);
        double wounded = plugin.statEngine().resources(player.getUniqueId()).currentHealth();

        // Zweimal: der erste Aufruf lernt den Charakter kennen (Erstkontakt schreibt nur den
        // Zeitstempel), der zweite rechnet die verstrichene Zeit gut.
        regeneration.settleAll(java.util.List.of(characterId));
        regeneration.settleAll(java.util.List.of(characterId));

        assertThat(plugin.statEngine().resources(player.getUniqueId()).currentHealth())
                .as("ohne einen einzigen Klick")
                .isGreaterThan(wounded);
    }

    /** Ein Spieler mit einem Berserker im Spiel - der Weg, den B03 auch im Betrieb geht. */
    private PlayerMock enterWarrior() {
        PlayerMock player = server.addPlayer();
        UUID playerId = player.getUniqueId();
        // Der Beitritt selbst hat die Sitzung schon geoeffnet - B03 erlaubt genau einen Join-Handler
        // und der laeuft hier echt mit. Ein zweites beginLoad waere eine DuplicateSessionException,
        // und zwar zu Recht.
        rpg.core.session.SessionRegistry sessions =
                plugin.registry().getService(rpg.core.session.SessionRegistry.class);
        if (sessions.find(playerId).isEmpty()) {
            plugin.sessionLifecycle().beginLoad(playerId, java.time.Duration.ofSeconds(5)).join();
            // Nur dann: eine Sitzung, die der Beitritt geoeffnet hat, ist bereits READY, und
            // READY -> READY ist ein verbotener Uebergang - zu Recht, sonst liesse sich ein
            // Ladevorgang unbemerkt zweimal abschliessen.
            plugin.sessionLifecycle().markReady(playerId);
        }
        // Der ECHTE Eintritt, nicht nur die Aktivierung: er belegt auch die Hotbar, und genau die
        // Kette vom Gegenstand bis zum Mana ist hier der Prüfgegenstand.
        assertThat(
                        plugin.enterCharacter(
                                player,
                                rpg.core.session.PlayerCharacter.create(
                                        playerId,
                                        rpg.core.session.CharacterClass.WARRIOR,
                                        java.time.Instant.now())))
                .as("der Charakter ist im Spiel")
                .isTrue();
        return player;
    }

    /** Der erste belegte Fähigkeitsslot - Slot 0 gehört der Waffe aus B07. */
    private static ItemStack firstAbilityItem(PlayerMock player) {
        for (int slot = 1; slot < 9; slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (rpg.platform.ability.AbilityItemTag.isAbilityItem(item)) {
                return item;
            }
        }
        return null;
    }

    // --- B09: zones and regions ---

    @Test
    void bothZoneTablesExistBecauseTheirMigrationRan() {
        assertThat(PostgresContainer.tableExists("character_zone_state")).isTrue();
        assertThat(PostgresContainer.tableExists("character_waypoints")).isTrue();
    }

    @Test
    void theZoneConfigurationIsWrittenOutLikeEveryOther() {
        // Without this line in DEFAULT_CONFIG_FILES the block starts against a file that is not
        // there. It cost 35 red tests once; it is asserted now rather than remembered.
        assertThat(plugin.getDataFolder().toPath().resolve("zones.yml")).exists();
    }

    // --- B11: items, equipment and loot ---

    @Test
    void theItemConfigurationIsWrittenOutLikeEveryOther() {
        // Dieselbe Falle wie bei zones.yml, und sie kostet dasselbe: ohne die Zeile in
        // DEFAULT_CONFIG_FILES startet der Block gegen eine Datei, die es nicht gibt.
        assertThat(plugin.getDataFolder().toPath().resolve("items.yml")).exists();
    }

    @Test
    void theItemModuleIsWiredAndItsFacadeAnswers() {
        // Ein Modul, dessen Modultests gruen sind, ist nicht fertig. Fertig ist es, wenn es im
        // Plugin verdrahtet ist und der Start gruen bleibt - das ist die Lehre aus B10.
        rpg.core.item.Items items = plugin.items();

        assertThat(items).as("B11 haengt im Bootstrap").isNotNull();
        assertThat(items.templateKeys())
                .as("die ausgelieferten Vorlagen sind geladen")
                .isNotEmpty();
        assertThat(items.wear().perDeath())
                .as("die Verschleisskurve steht - und der Tod wiegt schwerer als der Alltag")
                .isGreaterThan(items.wear().perDamageTaken());
    }

    @Test
    void anUnknownItemTemplateAnswersEmptyRatherThanThrowing() {
        // Die Zusage aus contracts/item-api.md: nichts wirft, nichts antwortet mit null. Eine
        // Vorlage kann zwischen zwei Reloads verschwinden, waehrend ein Exemplar davon noch in
        // einem Inventar liegt (FR-007).
        assertThat(plugin.items().template("potion.does-not-exist")).isEmpty();
        assertThat(plugin.items().sellPriceOf("potion.does-not-exist")).isEmpty();
    }

    @Test
    void everyItemListenerAndTableIsThere() {
        // T139: ein Modul, dessen Modultests gruen sind, ist nicht fertig. Was hier geprueft wird,
        // faellt in keinem Modultest auf - ein Zuhoerer, der nie registriert wurde, und eine
        // Migration, die nie lief, sehen von innen aus wie ein Block, der einfach nichts tut.
        assertThat(PostgresContainer.tableExists("character_gear_condition"))
                .as("US5: ohne die Tabelle waere jeder Charakter nach jedem Neustart wieder neu")
                .isTrue();
        assertThat(PostgresContainer.tableExists("character_cosmetic"))
                .as("US6: dito fuer die gekauften Trimfarben")
                .isTrue();

        assertThat(handlerCount(org.bukkit.event.player.PlayerInteractEntityEvent.getHandlerList()))
                .as("US4: der Rechtsklick auf den Haendler - ohne ihn oeffnet sein Fenster nie")
                .isEqualTo(1);
    }

    @Test
    void theItemAggregatesAreRegisteredWithTheFlush() {
        // ADR-015 Punkt 7, die dritte Registrierung. Sie zu vergessen faellt NICHT laut auf: die
        // Markierungen zaehlen bei jedem Durchlauf als gescheitert, geschrieben wird nie, und das
        // sieht aus wie ein Datenbankfehler. Genau das ist bei CHARACTER_COSMETIC einmal passiert -
        // gefunden von NoDatabaseAccessPerGameEventTest, nicht von einem Menschen.
        assertThat(plugin.registry().findService(rpg.core.item.Items.class))
                .as("die Fassade haengt an der Registry, wie jede andere auch")
                .isNotNull();
    }

    @Test
    void thewearSeamIsClosedWithSomethingOtherThanNone() {
        // Die Abnahmebedingung des Eingriffs aus dem Complexity Tracking. B07 verhaelt sich mit
        // GearConditionFactor.NONE exakt wie vorher - und genau deshalb waere eine vergessene
        // Verdrahtung unsichtbar: alles bliebe gruen, und der Verschleiss erreichte nie einen Wert.
        java.util.UUID unknown = java.util.UUID.randomUUID();

        assertThat(plugin.items()).isNotNull();
        // Ein unbekannter Charakter traegt volle Werte - das ist die sichere Richtung (FR-047).
        // Geprueft wird hier, dass ueberhaupt jemand antwortet, statt dass NONE stehengeblieben ist.
        assertThat(plugin.gearConditions())
                .as("ohne diese Naht waere der ganze Verschleiss gebaut und wirkungslos")
                .isNotNull();
        assertThat(plugin.gearConditions().factorOf(unknown, rpg.core.classes.LadderSlot.ARMOR))
                .isEqualTo(1.0);
    }

    @Test
    void everyZoneListenerIsRegistered() {
        // FOUR listeners, not the six the task list expected. Two of them - the join and the quit -
        // do not exist: B03 owns the session lifecycle and permits exactly one handler on each
        // (FR-007), so B09 hangs on its SessionObserver instead. NoCompetingSessionListenersTest
        // said so, and the right answer was to move rather than to argue.
        //
        // The movement guard is counted with the other four movement handlers further up.
        assertThat(handlerCount(org.bukkit.event.player.PlayerTeleportEvent.getHandlerList()))
                .as("a teleport is a zone change like any other (FR-017)")
                .isEqualTo(1);
        assertThat(handlerCount(org.bukkit.event.player.PlayerRespawnEvent.getHandlerList()))
                .as("B05 refills at MONITOR, B09 sets the place at NORMAL - two, and the order matters")
                .isEqualTo(2);
        assertThat(handlerCount(PlayerInteractEvent.getHandlerList()))
                .as(
                        "B08s Faehigkeitsausloeser, B09s Kristall, seit B11 der Trank - und seit"
                                + " B12 der Aktivitaetszeitstempel. Vier Bloecke auf einem"
                                + " Ereignis, jeder auf seiner Prioritaet: der Trank sitzt auf"
                                + " HIGH und bricht ab, sobald der Gegenstand einen B11-Vermerk"
                                + " traegt, damit Vanilla ihn nicht auch noch trinkt; B12 sitzt"
                                + " auf MONITOR und entscheidet nichts")
                .isEqualTo(4);
    }

    @Test
    void nothingHurtsAPlayerStandingInASafeCore() {
        // This test was written to prove setPermission had run, and it proved something else: the
        // permission is never consulted on the environment path at all. Lava, fire, drowning and a
        // fall went through a safe core untouched while ZoneDamagePermission claimed otherwise
        // (FR-028, SC-002). SafeCoreDamageGuard now closes that path, and this is the assertion that
        // would have caught it on day one.
        PlayerMock player = enterWarrior();

        // A fresh character is placed at the start region's respawn point, which lies in its safe
        // core - so this is the ordinary state of somebody who just logged in for the first time.
        assertThat(plugin.zoneTracker().inSafeCore(player.getUniqueId()))
                .as("the placement chain ran: session, character, teleport, tracker")
                .isTrue();
        rpg.core.combat.DamageResult result =
                plugin.combatPipeline()
                        .environmentDamage(
                                player.getUniqueId(), rpg.core.combat.EnvironmentSource.LAVA);

        assertThat(result.applied())
                .as("standing in the safe core, nothing may hurt them")
                .isFalse();
        assertThat(result.reason())
                .as("cancelled by the guard, not refused by the permission - two rules, two reasons")
                .isEqualTo(rpg.core.combat.RejectReason.CANCELLED);
    }

    @Test
    void aReloadRebuildsTheZoneIndex() {
        // applyReloadedConfig has to be called from the plugin's reload path, next to B04's. Without
        // it a changed zones.yml is read, validated, accepted - and ignored, which is the worst of
        // the three possible outcomes because it looks like success (FR-057a).
        assertThat(plugin.reloadConfiguration())
                .as("the shipped configuration reloads cleanly")
                .isTrue();

        PlayerMock player = enterWarrior();
        assertThat(
                        plugin.combatPipeline()
                                .environmentDamage(
                                        player.getUniqueId(),
                                        rpg.core.combat.EnvironmentSource.LAVA)
                                .applied())
                .as("and the rule still holds afterwards - the swapped index is wired up again")
                .isFalse();
    }

    // --- fixtures ---

    private static int handlerCount(HandlerList handlers) {
        return (int)
                Arrays.stream(handlers.getRegisteredListeners())
                        .filter(listener -> listener.getPlugin() instanceof RpgPlugin)
                        .count();
    }

}
