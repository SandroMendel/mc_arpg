package rpg.plugin;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import org.bukkit.plugin.java.JavaPlugin;

import rpg.core.classes.ClassMessageKeys;
import rpg.core.classes.ClassRegistry;
import rpg.core.combat.CombatMessageKeys;
import rpg.core.combat.CombatModule;
import rpg.core.combat.CombatPipeline;
import rpg.core.config.ConfigLoader;
import rpg.core.config.ConfigValidationException;
import rpg.core.event.DefaultEventBus;
import rpg.core.event.EventBus;
import rpg.core.message.MapMessages;
import rpg.core.message.MessageKey;
import rpg.core.message.MessageKeyValidator;
import rpg.core.message.Messages;
import rpg.core.module.BootstrapState;
import rpg.core.module.DefaultModuleRegistry;
import rpg.core.module.Module;
import rpg.core.module.ModuleBootstrap;
import rpg.core.progression.DefaultProgression;
import rpg.core.progression.PartyRegistry;
import rpg.core.progression.ProgressionMessageKeys;
import rpg.core.progression.XpDistributor;
import rpg.core.scheduler.Scheduler;
import rpg.core.session.SessionMessageKeys;
import rpg.core.stats.StatConfig;
import rpg.core.stats.StatEngine;
import rpg.persistence.PersistenceMessageKeys;
import rpg.persistence.PersistenceModule;
import rpg.persistence.ability.AbilityModule;
import rpg.persistence.classes.ClassesModule;
import rpg.persistence.inventory.InventoryModule;
import rpg.persistence.progression.ProgressionModule;
import rpg.persistence.session.SessionModule;
import rpg.persistence.stats.StatsModule;
import rpg.platform.PlatformMessageKeys;
import rpg.platform.PreJoinGuard;
import rpg.platform.classes.BoundItemFactory;
import rpg.platform.classes.ClassEquipmentApplier;
import rpg.platform.classes.ClassSelectionListener;
import rpg.platform.classes.ClassSelectionMenu;
import rpg.platform.classes.EquipmentLockListener;
import rpg.platform.classes.InventoryFullNoticeListener;
import rpg.platform.classes.NoCharacterGuardListener;
import rpg.platform.classes.PaperClassNotice;
import rpg.platform.classes.SelectionTimeout;
import rpg.platform.combat.CombatDeathListener;
import rpg.platform.combat.MobEquipmentListener;
import rpg.platform.combat.PaperDamageFeedback;
import rpg.platform.combat.PaperMobStatProvider;
import rpg.platform.combat.ProjectileCombatListener;
import rpg.platform.combat.ProjectileDamageTag;
import rpg.platform.combat.VanillaDamageListener;
import rpg.platform.combat.VanillaDamageMapping;
import rpg.platform.config.YamlConfigLoader;
import rpg.platform.hud.StatusActionBar;
import rpg.platform.hud.TargetReport;
import rpg.platform.progression.ExperienceBar;
import rpg.platform.progression.PaperProximityCheck;
import rpg.platform.progression.ProgressionDeathListener;
import rpg.platform.scheduler.PaperSchedulerAdapter;
import rpg.platform.session.PendingSessionStash;
import rpg.platform.session.SafeStateGuard;
import rpg.platform.session.SessionConnectionCloseListener;
import rpg.platform.session.SessionJoinListener;
import rpg.platform.session.SessionObserver;
import rpg.platform.session.SessionPreLoadListener;
import rpg.platform.session.SessionQuitListener;
import rpg.platform.stats.PaperVanillaAttributeBridge;
import rpg.platform.stats.VanillaRegenerationGuard;

/**
 * Plugin entry point: wires the five modules together and hands control to
 * {@link ModuleBootstrap}.
 *
 * <p>This class stays deliberately thin. Every rule worth testing - start order, fail-fast, the 10
 * second shutdown budget, config validation and rollback - lives in {@code rpg-core} and is covered
 * by server-free unit tests (Constitution VII.1). What is left here is exactly the part that needs a
 * running server: obtaining the Paper schedulers, registering the listener and reporting failure to
 * the server.
 */
public class RpgPlugin extends JavaPlugin {

    /** Bootstrap budget from plugin enable to readiness for the first join (SC-001, FR-013). */
    private static final Duration BOOTSTRAP_BUDGET = Duration.ofSeconds(30);

    private static final String MESSAGES_FILE = "messages.yml";

    /**
     * How often every online player's inventory is written down.
     *
     * <p>Bounds what a crash costs. Deliberately the same order as B02's autosave: a tighter interval
     * would serialise 41 slots per player more often for a smaller window than everything else already
     * accepts, and a wider one would make the inventory the weakest link.
     */
    private static final Duration INVENTORY_SWEEP = Duration.ofSeconds(45);

    /**
     * Configuration files written out on first start.
     *
     * <p>A module whose file is missing refuses to start, which is the right behaviour for a running
     * server and the wrong first impression for an operator who just dropped the jar in. Shipping a
     * default of each means the first start works and the file is there to be edited.
     */
    private static final List<String> DEFAULT_CONFIG_FILES =
            List.of(
                    "persistence.yml",
                    "session.yml",
                    "stats.yml",
                    "combat.yml",
                    "progression.yml",
                    "classes.yml",
                    "abilities.yml");

    private final BootstrapState bootstrapState = new BootstrapState();

    private DefaultModuleRegistry registry;
    private EventBus eventBus;
    private Scheduler scheduler;
    private ConfigLoader configLoader;
    private Messages messages;
    private ModuleBootstrap bootstrap;
    private PersistenceModule persistenceModule;
    private SessionModule sessionModule;
    private StatsModule statsModule;
    private CombatModule combatModule;
    private ProgressionModule progressionModule;
    private ClassesModule classesModule;
    private AbilityModule abilityModule;
    private rpg.core.ability.AbilityRuntime abilityRuntime;
    private rpg.platform.ability.AbilityHotbar abilityHotbar;
    private InventoryModule inventoryModule;
    private ExperienceBar experienceBar;

    @Override
    public void onEnable() {
        long startedAt = System.nanoTime();

        registry = new DefaultModuleRegistry();
        eventBus = new DefaultEventBus(getLogger());
        scheduler = new PaperSchedulerAdapter(this, getServer(), getLogger());
        YamlConfigLoader yamlLoader = new YamlConfigLoader(getDataFolder().toPath());
        configLoader = yamlLoader;

        // Defaults before anything reads them; saveResource leaves an existing file alone, so an
        // operator's edits survive every restart and every update.
        for (String file : DEFAULT_CONFIG_FILES) {
            if (!Files.exists(getDataFolder().toPath().resolve(file))) {
                saveResource(file, false);
            }
        }

        // Messages before anything else: the pre-login guard needs them, and a missing text must
        // stop the start rather than surface later as a blank kick screen (FR-023a).
        try {
            messages = loadMessages(yamlLoader);
        } catch (RuntimeException | ConfigValidationException failure) {
            getLogger().log(Level.SEVERE, "RPG bootstrap failed - messages.yml is unusable", failure);
            bootstrapState.markFailed("messages.yml is unusable: " + failure.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        bootstrap =
                new ModuleBootstrap(
                        registry, eventBus, scheduler, configLoader, bootstrapState, getLogger());

        // Refuse joins from the very first moment: the guard has to be live *before* the modules
        // start, otherwise the race it exists to close is still open during bootstrap (FR-013).
        getServer()
                .getPluginManager()
                .registerEvents(new PreJoinGuard(bootstrapState, messages), this);

        for (Module module : modules()) {
            bootstrap.add(module);
        }

        try {
            bootstrap.start();
        } catch (RuntimeException failure) {
            // Fail-fast: never leave the server half-initialised and misbehaving later (FR-013).
            getLogger().log(Level.SEVERE, "RPG bootstrap failed - disabling the plugin", failure);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Before the session listeners, because it produces the observer they carry: B07 has to hear
        // about a ready session, and B03 allows exactly one join handler (FR-007).
        SessionObserver classes = assembleClassLayer();
        registerSessionListeners(classes);
        assembleStatLayer();
        assembleCombatLayer();
        assembleProgressionLayer();

        // Same cadence as B02's autosave, and for the same reason: a crash should cost one interval,
        // not a whole session's loot. The quit path captures on its own; this is only for the case
        // where there is no quit path.
        startInventorySweep(INVENTORY_SWEEP);

        Duration took = Duration.ofNanos(System.nanoTime() - startedAt);
        if (took.compareTo(BOOTSTRAP_BUDGET) > 0) {
            getLogger()
                    .warning(
                            "[bootstrap] took "
                                    + took.toMillis()
                                    + "ms, exceeding the "
                                    + BOOTSTRAP_BUDGET.toSeconds()
                                    + "s budget (SC-001)");
        } else {
            getLogger()
                    .info(
                            "[bootstrap] ready for players after "
                                    + took.toMillis()
                                    + "ms (budget "
                                    + BOOTSTRAP_BUDGET.toSeconds()
                                    + "s)");
        }
    }

    @Override
    public void onDisable() {
        if (bootstrap == null) {
            return; // enable never got far enough to build one
        }
        // Bounded by 10s per module inside ModuleBootstrap (FR-012, SC-007): a module that hangs is
        // abandoned on a daemon thread instead of blocking the server's shutdown indefinitely.
        bootstrap.shutdown();
    }

    /**
     * Internal reload entry point (FR-003).
     *
     * <p>Global by design (clarification 2026-08-19): every module's configuration is reloaded at
     * once, and a rejected document leaves the previously valid configuration active for all of them
     * (FR-004). Exposed as a method rather than a command because the operator-facing
     * {@code /rpg reload} command belongs to B14; this is what that command will call.
     *
     * @return {@code true} if the new configuration was applied, {@code false} if it was rejected and
     *     the previous one stays active
     */
    public boolean reloadConfiguration() {
        try {
            configLoader.reloadAll();
            // Modules that keep derived state from their configuration have to be told. B04 does:
            // its engine holds the attribute definitions and has to mark every holder so the new
            // numbers actually take effect (User Story 7, scenario 4).
            if (statsModule != null) {
                statsModule.applyReloadedConfig();
            }
            getLogger().info("[config] phase=RELOAD state=APPLIED - all modules reloaded");
            return true;
        } catch (ConfigValidationException rejected) {
            getLogger()
                    .log(
                            Level.SEVERE,
                            "[config] phase=RELOAD state=REJECTED - keeping the previously valid"
                                    + " configuration: "
                                    + rejected.getMessage(),
                            rejected);
            return false;
        }
    }

    /**
     * Loads {@code messages.yml} and verifies every declared key has a text.
     *
     * <p>The default file is written out on first start so an operator has something to edit
     * instead of having to guess the keys.
     */
    private Messages loadMessages(YamlConfigLoader loader) throws ConfigValidationException {
        Path file = getDataFolder().toPath().resolve(MESSAGES_FILE);
        if (!Files.exists(file)) {
            saveResource(MESSAGES_FILE, false);
        }
        Messages loaded = MapMessages.fromNested(loader.readDocument(Path.of(MESSAGES_FILE)));

        // Collect the keys every module can ask for. A block that adds player-facing text adds its
        // keys here, and the check below then covers it too.
        List<MessageKey> declared = new ArrayList<>(PlatformMessageKeys.all());
        declared.addAll(PersistenceMessageKeys.all());
        declared.addAll(SessionMessageKeys.all());
        declared.addAll(ProgressionMessageKeys.all());
        declared.addAll(ClassMessageKeys.all());
        declared.addAll(CombatMessageKeys.all());
        MessageKeyValidator.verifyAllPresent(loaded, declared);

        getLogger().info("[messages] " + declared.size() + " declared key(s) resolved");
        return loaded;
    }

    /**
     * The modules making up this server.
     *
     * <p>B01 owns no module of its own - it is the foundation everything else is built against.
     * B02 and B03 add theirs here, and nowhere else, which is what keeps the wiring in one
     * reviewable place instead of spread across static initialisers. The start order follows from
     * the declared dependencies, not from this list.
     */
    private List<Module> modules() {
        persistenceModule = new PersistenceModule(getLogger(), Clock.systemUTC());
        sessionModule = new SessionModule(persistenceModule, getLogger(), Clock.systemUTC());
        statsModule = new StatsModule(persistenceModule, sessionModule, getLogger(), Clock.systemUTC());
        combatModule =
                new CombatModule(sessionModule.registry(), getLogger(), Clock.systemUTC());
        progressionModule =
                new ProgressionModule(
                        persistenceModule, sessionModule, getLogger(), Clock.systemUTC());
        classesModule =
                new ClassesModule(
                        persistenceModule,
                        sessionModule,
                        statsModule,
                        progressionModule,
                        getLogger(),
                        Clock.systemUTC());
        inventoryModule =
                new InventoryModule(
                        persistenceModule, sessionModule, getLogger(), Clock.systemUTC());
        // After the classes: the cross-check between the loadouts and the ability definitions needs
        // both configurations, and it is the promise B07 could not keep - there an ability id travels
        // as an opaque string because this block did not exist yet.
        abilityModule =
                new AbilityModule(
                        persistenceModule,
                        sessionModule,
                        classesModule,
                        getLogger(),
                        Clock.systemUTC());
        return List.of(
                persistenceModule,
                sessionModule,
                statsModule,
                combatModule,
                progressionModule,
                classesModule,
                inventoryModule,
                abilityModule);
    }

    /**
     * Assembles the Paper-facing half of B03.
     *
     * <p>This is the one place that sees both sides: the listeners live in {@code rpg-platform} and
     * know only {@code rpg-core} interfaces, the lifecycle they drive lives in
     * {@code rpg-persistence}. Neither module may depend on the other (Constitution III.2), so the
     * plugin does the introduction.
     *
     * <p>Registered <strong>after</strong> {@code bootstrap.start()} on purpose. A pre-login event
     * arriving while the modules are still coming up would find a lifecycle that is not there yet;
     * until this call, B01's {@code PreJoinGuard} is what answers those connections.
     */
    private void registerSessionListeners(SessionObserver observer) {
        SafeStateGuard safeState = new SafeStateGuard(getLogger());
        PendingSessionStash stash =
                new PendingSessionStash(
                        sessionModule.config().pendingExpiry(), Clock.systemUTC(), getLogger());

        getServer()
                .getPluginManager()
                .registerEvents(
                        new SessionPreLoadListener(
                                sessionModule.lifecycle(),
                                stash,
                                messages,
                                sessionModule.config().loadTimeout(),
                                persistenceModule::loginRefusalReason,
                                getLogger()),
                        this);
        getServer()
                .getPluginManager()
                .registerEvents(
                        new SessionJoinListener(
                                sessionModule.lifecycle(),
                                stash,
                                safeState,
                                observer,
                                getLogger()),
                        this);
        getServer()
                .getPluginManager()
                .registerEvents(
                        new SessionQuitListener(
                                sessionModule.lifecycle(), safeState, observer, getLogger()),
                        this);
        getServer()
                .getPluginManager()
                .registerEvents(
                        new SessionConnectionCloseListener(sessionModule.lifecycle(), stash), this);
        getServer().getPluginManager().registerEvents(safeState, this);

        // The sweep needs to know who is actually connected, which only the server can answer.
        sessionModule.startReconciliation(
                () ->
                        getServer().getOnlinePlayers().stream()
                                .map(org.bukkit.entity.Player::getUniqueId)
                                .toList(),
                stash::expireStale);
    }

    /**
     * Assembles the Paper-facing half of B04.
     *
     * <p>Same introduction the session layer needs, for the same reason: the mirror lives in
     * {@code rpg-platform} and knows only {@code rpg-core} interfaces, the engine lives in
     * {@code rpg-persistence}, and neither module may depend on the other (Constitution III.2).
     *
     * <p>The regeneration guard runs after the bootstrap because it writes a game rule, which needs
     * the worlds to exist. It handles regeneration and food and nothing else - vanilla damage
     * sources belong to B05, and a test enforces that (FR-030b).
     */
    private void assembleStatLayer() {
        statsModule.installVanillaBridge(
                new PaperVanillaAttributeBridge(getServer(), scheduler, getLogger()));

        VanillaRegenerationGuard regenerationGuard = new VanillaRegenerationGuard(getLogger());
        regenerationGuard.applyTo(getServer());
        getServer().getPluginManager().registerEvents(regenerationGuard, this);
    }

    /**
     * Assembles the Paper-facing half of B05.
     *
     * <p>Five listeners, each with one job. The mob equipping is the one that would be easy to
     * forget and impossible to notice missing: without it nothing has a stat holder, so the entire
     * combat pipeline would apply to nothing but players - working, tested, and invisible.
     *
     * <p>Creatures that were already loaded when the plugin started are equipped here too. On a
     * reload the world is full of mobs that will never fire a spawn event again.
     */
    private void assembleCombatLayer() {
        CombatPipeline pipeline = registry.getService(CombatPipeline.class);
        StatEngine stats = registry.getService(StatEngine.class);

        ProjectileDamageTag.initialise(this);
        pipeline.registerFeedback(new PaperDamageFeedback(getServer(), scheduler, getLogger()));

        PaperMobStatProvider mobStats =
                new PaperMobStatProvider(combatModule.config(), StatConfig.defaults());
        pipeline.setMobStatProvider(mobStats);

        MobEquipmentListener mobEquipment =
                new MobEquipmentListener(stats, pipeline, mobStats, getLogger());
        CombatDeathListener deaths = new CombatDeathListener(stats, pipeline, getLogger());
        deaths.applyTo(getServer());

        getServer()
                .getPluginManager()
                .registerEvents(
                        new VanillaDamageListener(
                                pipeline, new VanillaDamageMapping(getLogger()), getLogger()),
                        this);
        getServer().getPluginManager().registerEvents(new ProjectileCombatListener(stats), this);
        getServer().getPluginManager().registerEvents(mobEquipment, this);
        getServer().getPluginManager().registerEvents(deaths, this);

        int equipped = 0;
        for (org.bukkit.World world : getServer().getWorlds()) {
            for (org.bukkit.entity.LivingEntity entity :
                    world.getLivingEntities()) {
                if (mobEquipment.wouldEquip(entity)) {
                    mobEquipment.equip(entity);
                    equipped++;
                }
            }
        }
        getLogger()
                .info("[combat] listeners registered; " + equipped + " already-loaded creature(s) equipped");

        // The two readouts. Deliberately not called a HUD: that name and its layout belong to B13,
        // which will take both over. Until then these are one action bar line and one chat line.
        // B04 adapted to the three numbers a readout needs. The displays get a reading, not the
        // engine - twenty of its twenty-one methods are none of their business.
        rpg.platform.hud.CombatStatusSource statusSource =
                holderId ->
                        stats.findSnapshot(holderId)
                                .map(
                                        snapshot -> {
                                            var resources = stats.resources(holderId);
                                            return new rpg.platform.hud.CombatStatusSource.Status(
                                                    resources.currentHealth(),
                                                    resources.maxHealth(),
                                                    snapshot.get(rpg.core.stats.Attribute.DEFENSE));
                                        });

        StatusActionBar actionBar =
                new StatusActionBar(getServer(), statusSource, scheduler, messages, getLogger());
        actionBar.subscribeTo(eventBus);
        // The list comes from B03's registry, which is the authority on who is playing - not from
        // whichever module happens to keep a map of them.
        actionBar.startRefresh(this::playersInPlay);

        TargetReport targets =
                new TargetReport(getServer(), statusSource, scheduler, messages, getLogger());
        targets.subscribeTo(eventBus);

        startDamageWindowSweep(combatModule.config().aggregationWindow(), combatModule.pipeline());
    }

    /** Everyone currently playing a character, from the registry that decides it. */
    private List<java.util.UUID> playersInPlay() {
        return sessionModule.registry().all().stream()
                .filter(session -> session.activeCharacter().isPresent())
                .map(rpg.core.session.PlayerSession::playerId)
                .toList();
    }

    /**
     * Closes the damage windows whose time is up.
     *
     * <p>Without this nothing ever closes an idle window: B05 only closes one when the <em>next</em>
     * hit arrives after it expired, so hitting a mob and stopping published no event at all. Everything
     * that listens - the target line, and later statistics and quests - simply never heard about those
     * hits.
     *
     * <p>Runs at the aggregation window from {@code combat.yml}, because that is the delay the design
     * already accepts between a hit and the report of it. One pass over the open windows; with none
     * open it is a single empty map scan.
     */
    private void startDamageWindowSweep(
            Duration interval, rpg.core.combat.DefaultCombatPipeline pipeline) {
        scheduler.runAsyncDelayed(
                interval,
                () -> {
                    pipeline.publishExpiredDamageWindows();
                    if (isEnabled()) {
                        startDamageWindowSweep(interval, pipeline);
                    }
                });
    }

    /**
     * The one sweep that drives every timed ability effect (FR-010b).
     *
     * <p>Interval effects, expiring buffs and lost projectiles share it, because they share the reason for existing:
     * both are "something that ends later", and the alternative - a task per running effect - is the
     * shape Constitution II rules out. At two hundred poisons this is one pass; with none running it
     * is two empty map scans.
     *
     * <p>Half a second, not a tick. An effect configured at one second is late by at most half of one,
     * and nothing in this game is fine-grained enough for that to be visible.
     */
    private void startAbilitySweep(
            rpg.core.ability.effect.IntervalEffectRunner intervals,
            rpg.core.ability.effect.BuffEffect buffs,
            rpg.platform.ability.AbilityProjectile projectiles) {
        scheduler.runAsyncDelayed(
                Duration.ofMillis(500),
                () -> {
                    intervals.sweep();
                    buffs.expire();
                    projectiles.sweep();
                    if (isEnabled()) {
                        startAbilitySweep(intervals, buffs, projectiles);
                    }
                });
    }

    /**
     * Assembles the Paper-facing half of B07, and hands back the seam B03 drives it through.
     *
     * <p>Four listeners and one observer. The observer is why this runs before
     * {@link #registerSessionListeners}: B07 has to act the moment a session is ready - open the
     * selection, or put the class equipment back on - and B03 permits exactly one join handler
     * (FR-007). So the class layer does not listen for joins; it is told about them.
     *
     * <p>{@link ClassSelectionListener} gets a {@link rpg.platform.classes.CharacterEntry} that is the
     * only path from "class chosen" to "in the game state": activating the character on the session runs
     * every attachment - B04's holder, B06's level, B07's tiers - and the equipment goes on afterwards,
     * because it is built from those tiers.
     */
    /**
     * Assembles the Paper-facing half of B08 (T056).
     *
     * <p>Runs from inside the class layer, because it needs what that one built: the ability items are
     * placed <em>after</em> B07's bound weapon in slot 0, and the trigger path asks B05 whether a
     * target may be attacked rather than deciding that itself.
     *
     * <p>Without this the whole block is inert however green its own tests are (ADR-012).
     */
    private void assembleAbilityLayer() {
        rpg.core.ability.AbilityRegistry abilities = abilityModule.registry();
        rpg.core.combat.CombatPipeline pipeline = combatModule.pipeline();

        // The rules live in rpg-core, the lookup here - the same split as MobStatProvider in B05.
        //
        // The permission predicate lets everything through for now, and that is a stated gap rather
        // than an oversight: B05 owns the rule but exposes no "may A attack B" read, only enforcement
        // inside the pipeline. Damage is therefore still refused correctly - abilityDamage checks it -
        // but a cone can currently NAME a target it may not hit. The pre-filter goes in when B05
        // exposes the query, which B09 needs anyway to make the rule per-zone (FR-023).
        rpg.platform.ability.PaperTargetResolver resolver =
                new rpg.platform.ability.PaperTargetResolver(getServer(), (caster, target) -> true);

        rpg.core.ability.effect.EffectDispatcher effects =
                new rpg.core.ability.effect.EffectDispatcher(getLogger());
        effects.register(
                rpg.core.ability.EffectType.DAMAGE,
                new rpg.core.ability.effect.DamageEffect(pipeline));
        effects.register(
                rpg.core.ability.EffectType.LIFESTEAL,
                new rpg.core.ability.effect.LifestealEffect(statsModule.engine()));
        effects.register(
                rpg.core.ability.EffectType.HEAL,
                new rpg.core.ability.effect.HealEffect(statsModule.engine()));
        effects.register(
                rpg.core.ability.EffectType.MANA_RESTORE,
                new rpg.core.ability.effect.ManaRestoreEffect(statsModule.engine()));
        effects.register(
                rpg.core.ability.EffectType.EVADE, new rpg.core.ability.effect.EvadeEffect());
        // The shield keeps the absorption pool itself, so the instance is held rather than discarded -
        // the pipeline has to be able to ask it what it can take.
        rpg.core.ability.effect.ShieldEffect shields =
                new rpg.core.ability.effect.ShieldEffect(Clock.systemUTC());
        effects.register(rpg.core.ability.EffectType.SHIELD, shields);

        // Buff and debuff differ only in who they land on, and the targeting decided that already.
        rpg.core.ability.effect.BuffEffect buffs =
                new rpg.core.ability.effect.BuffEffect(statsModule.engine(), Clock.systemUTC());
        effects.register(rpg.core.ability.EffectType.BUFF, buffs);
        effects.register(rpg.core.ability.EffectType.DEBUFF, buffs);
        rpg.core.ability.effect.MeterEffect meter =
                new rpg.core.ability.effect.MeterEffect(statsModule.engine(), Clock.systemUTC());
        effects.register(rpg.core.ability.EffectType.METER, meter);

        // The three that need the world. The two B10 gaps - mob aggression towards a clone, mobs
        // losing interest in someone who vanished - are named in the primitives' javadoc rather than
        // silently absent.
        rpg.platform.ability.PaperSummons summons =
                new rpg.platform.ability.PaperSummons(getServer(), scheduler, getLogger());
        effects.register(
                rpg.core.ability.EffectType.SUMMON,
                new rpg.core.ability.effect.SummonEffect(summons));
        effects.register(
                rpg.core.ability.EffectType.INVISIBILITY,
                new rpg.core.ability.effect.InvisibilityEffect(summons));

        rpg.platform.ability.AbilityProjectile projectiles =
                new rpg.platform.ability.AbilityProjectile(getServer(), pipeline, getLogger());
        effects.register(
                rpg.core.ability.EffectType.PROJECTILE,
                new rpg.core.ability.effect.ProjectileEffect(projectiles));
        getServer().getPluginManager().registerEvents(projectiles, this);

        rpg.platform.ability.PaperMovementEffects movement =
                new rpg.platform.ability.PaperMovementEffects(getServer(), getLogger());
        effects.register(rpg.core.ability.EffectType.DASH, movement.dash());
        effects.register(rpg.core.ability.EffectType.KNOCKBACK, movement.knockback());
        effects.register(rpg.core.ability.EffectType.TELEPORT, movement.teleport());

        // ONE sweep for every interval effect in the game, and one for expiring buffs. Per target
        // would be a recurring task per entity - the shape that made damage over time unacceptable
        // the first time round (FR-010b).
        rpg.core.ability.effect.IntervalEffectRunner intervals =
                new rpg.core.ability.effect.IntervalEffectRunner(effects, Clock.systemUTC());
        // Both directions: the dispatcher hands periodic effects TO the runner, and the runner hands
        // each due application back THROUGH the dispatcher, so it stays behind the same error barrier.
        effects.setIntervalRunner(intervals);
        startAbilitySweep(intervals, buffs, projectiles);

        abilityRuntime =
                new rpg.core.ability.AbilityRuntime(
                        abilities,
                        statsModule.engine(),
                        resolver,
                        effects,
                        abilityModule.repository(),
                        Clock.systemUTC());

        // Settled before every mana check (FR-037) - and never on a timer. This is also where a
        // wounded player finally heals at all: ADR-013 switched vanilla regeneration off and left the
        // gap open until ADR-023 made the two rates attributes.
        abilityRuntime.setRegeneration(abilityModule.regeneration());

        // The one place in this block that schedules anything: entity-bound, single-shot (ADR-024).
        // A character with nothing running has no task, which is what SC-005 asserts.
        abilityRuntime.setScheduling(
                (characterId, delay, task) ->
                        scheduler.runSyncOnEntityDelayed(
                                new rpg.core.scheduler.EntityRef(characterId), delay, task));

        abilityHotbar = new rpg.platform.ability.AbilityHotbar(messages, getLogger());

        // The passive triggers, hung on the three hooks B05 already has (research.md R6). Which stage
        // each one uses is not interchangeable - see PassiveInterceptors.
        rpg.core.ability.PassiveDispatcher passives =
                new rpg.core.ability.PassiveDispatcher(
                        abilities,
                        effects,
                        statsModule.engine(),
                        abilityModule.repository(),
                        Clock.systemUTC(),
                        Math::random);
        // The three things the passive rules describe but cannot do themselves.
        rpg.platform.ability.PaperPassiveHooks hooks =
                new rpg.platform.ability.PaperPassiveHooks(getServer(), messages, getLogger());
        passives.setBehindTargetCheck(hooks.behindTarget());
        // No setWorldCondition: B09 owns that distinction and does not exist. The default lets
        // everything through, which makes Second Life work inside an instance too - wrong, visible,
        // and better than the opposite default, where the unique would silently do nothing (ADR-025).
        effects.register(
                rpg.core.ability.EffectType.STATUS_EFFECT,
                new rpg.core.ability.effect.StatusEffectEffect(hooks.statusEffects()));

        // ON_KILL is the one trigger that is not an interceptor: killing is not a stage of the damage
        // pipeline, it is what the pipeline concludes, and B05 announces it.
        new rpg.core.ability.OnKillSubscriber(passives).subscribeTo(eventBus);

        pipeline.registerInterceptor(rpg.core.ability.PassiveInterceptors.damageTaken(passives));
        pipeline.registerInterceptor(rpg.core.ability.PassiveInterceptors.damageDealt(passives));
        pipeline.registerInterceptor(
                rpg.core.ability.PassiveInterceptors.lethalBlow(
                        passives,
                        statsModule.engine(),
                        hooks.secondLife()));

        getServer()
                .getPluginManager()
                .registerEvents(
                        new rpg.platform.ability.AbilityTriggerListener(
                                (player, abilityId) ->
                                        abilityRuntime.trigger(
                                                characterIdOf(player).orElse(player.getUniqueId()),
                                                abilityId),
                                (player, key) -> player.sendMessage(messages.get(key)),
                                getLogger()),
                        this);

        // The double jump asks the registry which ability grants it, rather than naming one in code -
        // otherwise a piece of content would live in the source (EffectType.DOUBLE_JUMP).
        getServer()
                .getPluginManager()
                .registerEvents(
                        new rpg.platform.ability.DoubleJumpListener(
                                player -> doubleJumpOf(abilities, player).isPresent(),
                                player ->
                                        doubleJumpOf(abilities, player)
                                                .map(
                                                        ability ->
                                                                abilities.toggleOf(
                                                                                characterIdOf(player)
                                                                                        .orElse(
                                                                                                player.getUniqueId()),
                                                                                ability.id())
                                                                        != rpg.core.ability.ToggleState
                                                                                .PARTIAL)
                                        .orElse(false),
                                () -> 0.8,
                                () -> 60),
                        this);

        getServer()
                .getPluginManager()
                .registerEvents(
                        new rpg.platform.ability.CastInterruptListener(
                                player -> characterIdOf(player).orElse(null),
                                characterId ->
                                        abilityRuntime
                                                .running(characterId)
                                                .map(
                                                        running ->
                                                                abilities.find(running.abilityId())
                                                                        .map(
                                                                                rpg.core.ability
                                                                                                .Ability
                                                                                        ::interruptOnMove)
                                                                        .orElse(false))
                                                .orElse(false),
                                abilityRuntime::end),
                        this);

        // The session end is B03's to announce, not ours to listen for (FR-007, FR-014). The module
        // hears about it through its attachment and stops whatever was running.
        // Everything a character can leave behind: the running ability, its interval effects, its
        // timed buffs and its meter. A missed one is a leak that only shows up after hours of
        // players coming and going, which is the worst kind of leak to look for.
        abilityModule.setRunningEnder(
                characterId -> {
                    abilityRuntime.end(characterId, rpg.core.ability.EndCause.DISCONNECTED);
                    intervals.forget(characterId);
                    buffs.forget(characterId);
                    meter.forget(characterId);
                });

        getLogger()
                .info(
                        "[abilities] listeners registered - trigger, left-click guard, double jump,"
                                + " cast interruption");
    }

    /** The ability granting this player a double jump, if any is unlocked and switched on. */
    private java.util.Optional<rpg.core.ability.Ability> doubleJumpOf(
            rpg.core.ability.AbilityRegistry abilities, org.bukkit.entity.Player player) {
        return characterIdOf(player)
                .flatMap(
                        characterId ->
                                abilities.capability(
                                        characterId, rpg.core.ability.EffectType.DOUBLE_JUMP));
    }

    /** The character a player is currently playing, for the trigger path. */
    private java.util.Optional<java.util.UUID> characterIdOf(org.bukkit.entity.Player player) {
        return sessionModule
                .registry()
                .find(player.getUniqueId())
                .flatMap(rpg.core.session.PlayerSession::activeCharacter)
                .map(rpg.core.session.PlayerCharacter::characterId);
    }

    private SessionObserver assembleClassLayer() {
        ClassRegistry classes = classesModule.registry();
        NoCharacterGuardListener guard = new NoCharacterGuardListener(getLogger());
        // The vanilla bar shows B06's level and experience; it stores nothing of its own. Subscribed
        // here so every gain reaches it, and read once on entry for the character's starting value.
        experienceBar = new ExperienceBar(getServer(), scheduler, getLogger());
        experienceBar.subscribeTo(eventBus);

        // A level-up may unlock an ability, and then the hotbar has to grow by one slot (T123,
        // FR-059). The whole layout is redone rather than one slot appended: it costs nothing at this
        // frequency, and it cannot get out of step the way a patch can.
        eventBus.subscribe(
                rpg.core.progression.LevelUpEvent.class,
                event -> {
                    org.bukkit.entity.Player player = getServer().getPlayer(event.playerId());
                    if (player != null) {
                        scheduler.runSyncOnEntity(
                                new rpg.core.scheduler.EntityRef(event.playerId()),
                                () -> {
                                    layOutAbilities(player, event.characterId());
                                    announceUnlocks(player, event);
                                });
                    }
                });
        ClassEquipmentApplier equipment =
                new ClassEquipmentApplier(
                        classesModule.boundEquipment(), new BoundItemFactory(messages), getLogger());

        ClassSelectionListener selection =
                new ClassSelectionListener(
                        classesModule.selection(),
                        new ClassSelectionMenu(classes, messages),
                        sessionModule.registry(),
                        guard,
                        (player, character) -> enterGameState(player, character, equipment),
                        // The one place that sees all three blocks: B03 owns the characters, B06 the
                        // levels, B07 the tiers, and a menu entry needs all of it.
                        classesModule::slotsFor,
                        new SelectionTimeout(getServer(), scheduler, messages),
                        scheduler,
                        getLogger());

        getServer().getPluginManager().registerEvents(guard, this);
        getServer().getPluginManager().registerEvents(selection, this);
        getServer().getPluginManager().registerEvents(new EquipmentLockListener(getLogger()), this);
        getServer()
                .getPluginManager()
                .registerEvents(
                        new InventoryFullNoticeListener(
                                new PaperClassNotice(getServer(), messages), Clock.systemUTC()),
                        this);

        getLogger()
                .info(
                        "[classes] listeners registered - selection, guard, equipment lock, "
                                + "inventory notice");

        assembleAbilityLayer();

        return new SessionObserver() {
            @Override
            public void onSessionReady(org.bukkit.entity.Player player) {
                // Nothing of the previous character while choosing: no items, no hearts, no level. What
                // the client shows here is whatever vanilla saved for the *player*, and the real copies
                // are in the database, waiting for the character they belong to.
                resetToNeutralState(player, experienceBar);
                // Opens the selection for a player without a character, and releases the guard for one
                // who has. Either way the equipment is applied afterwards - and does nothing when there
                // is no character to build it from.
                selection.openIfNeeded(player);
                classesModule
                        .characterOf(player.getUniqueId())
                        .ifPresent(characterId -> equipment.apply(player, characterId));
            }

            @Override
            public void onSessionEnded(java.util.UUID playerId) {
                selection.onSessionEnded(playerId);
                // Before B03 starts the unload: the player is still here, so their inventory can still
                // be read - and this is the last moment that is true. The observer runs on the quit
                // event, which is the player's own tick.
                org.bukkit.entity.Player leaving = getServer().getPlayer(playerId);
                if (leaving != null) {
                    captureInventory(leaving);
                }
            }
        };
    }

    /**
     * Takes a freshly chosen character into play.
     *
     * <p>Activation first, equipment second, and the order is the whole point: the items are built from
     * the tiers, and the tiers only exist once the session activated the character.
     *
     * <p>A failure to put the equipment on is <b>not</b> a failure to enter. The character exists, has
     * stats and a level, and can play; the applier logs what it could not place, and the next login
     * applies it again. Refusing the entry over it would leave a stored character no session can reach.
     */
    /**
     * Puts this character's ability items into the hotbar (T123, T124).
     *
     * <p><b>Laid out from the reached level, never patched from an event.</b> Called on entry and
     * again on every level-up, and both calls do the same complete thing - so a level-up that was
     * missed, or one that happened while the ability layer was still starting, cannot leave a slot
     * empty for the rest of the session. There is no state here to get out of step.
     */
    /**
     * Tells the player which abilities the new level opened (FR-060).
     *
     * <p>Every level in the gap, not just the one reached: an admin command or a large kill can move
     * a character several levels at once, and a player who never hears about the ability they just
     * got will not use it.
     */
    private void announceUnlocks(
            org.bukkit.entity.Player player, rpg.core.progression.LevelUpEvent event) {
        if (abilityModule == null) {
            return;
        }
        abilityModule
                .registry()
                .classOf(event.characterId())
                .ifPresent(
                        characterClass -> {
                            for (rpg.core.classes.AbilityBinding binding :
                                    classesModule.config().definition(characterClass).abilities()) {
                                if (binding.unlockLevel() > event.previousLevel()
                                        && binding.unlockLevel() <= event.newLevel()) {
                                    announceUnlock(player, binding.abilityId());
                                }
                            }
                        });
    }

    private void announceUnlock(org.bukkit.entity.Player player, String abilityId) {
        abilityModule
                .registry()
                .find(abilityId)
                .ifPresent(
                        ability ->
                                player.sendMessage(
                                        net.kyori.adventure.text.Component.text(
                                                messages.get(
                                                        rpg.core.ability.AbilityMessageKeys.UNLOCKED,
                                                        java.util.Map.of(
                                                                "ability",
                                                                messages.get(
                                                                        ability.displayNameKey()))))));
    }

    private void layOutAbilities(org.bukkit.entity.Player player, java.util.UUID characterId) {
        if (abilityHotbar == null || abilityModule == null) {
            return;
        }
        abilityHotbar.layOut(player, abilityModule.registry().unlockedFor(characterId));
    }

    private boolean enterGameState(
            org.bukkit.entity.Player player,
            rpg.core.session.PlayerCharacter character,
            ClassEquipmentApplier equipment) {
        if (!sessionModule.lifecycle().activateCharacter(player.getUniqueId(), character)) {
            return false;
        }
        java.util.UUID characterId = character.characterId();

        // Three steps, and the order is the whole of it.
        //
        // Emptied first, every time: in Minecraft both containers belong to the *player*, and the
        // selection is how someone switches between their characters - keeping them would carry the
        // warrior's loot into the mage. In practice they are already empty, because they are cleared
        // when the selection opens; this is the guarantee rather than the mechanism.
        clearCarriedItems(player);
        // Then what this character was carrying and storing when it was last put down. Bound items are
        // not in there; they are rebuilt below from the reached tier.
        inventoryModule
                .contentsOf(characterId)
                .ifPresent(
                        stored -> {
                            rpg.platform.inventory.PlayerInventoryContents.restore(
                                    player, stored.contents(), getLogger());
                            rpg.platform.inventory.PlayerInventoryContents.restoreEnderChest(
                                    player, stored.enderChest(), getLogger());
                        });
        // Class equipment last, so it always wins the slots it owns.
        equipment.apply(player, characterId);
        // And the ability items on top of it, because they sit in the hotbar slots the weapon does not
        // own. Laid out from the reached level rather than patched from events (T124): a missed
        // level-up would otherwise leave a slot empty for the rest of the session, and nothing would
        // ever notice.
        layOutAbilities(player, characterId);

        // The bar, now that B06 has loaded this character's progress. From here on the subscription
        // keeps it current; this is only the starting value.
        registry.getService(rpg.core.progression.Progression.class)
                .progressOf(characterId)
                .ifPresent(
                        view ->
                                experienceBar.show(
                                        player.getUniqueId(), view.level(), view.fraction()));
        return true;
    }

    /**
     * Empties both containers that belong to the player rather than to a character.
     *
     * <p>Called when the selection opens and again on entry. The first is what the player sees: nobody
     * stands in the menu looking at the last character's backpack, and nothing from it can be reached
     * while choosing. The second is the guarantee that entry starts from a known state.
     *
     * <p>Safe to do at the menu because the contents were written down on the way out of the previous
     * session and are read back on entry. The one exception is the very first start with this build:
     * whatever players were carrying then belongs to no character - there was no character-level store
     * to attribute it to - and it is cleared.
     */
    private void clearCarriedItems(org.bukkit.entity.Player player) {
        player.getInventory().clear();
        player.getEnderChest().clear();
    }

    /**
     * Wipes everything the client shows that belongs to the player rather than to a character.
     *
     * <p>Items, hearts and the experience bar are all saved by vanilla per player, so at the moment a
     * session becomes ready they still show the character that was last played. Someone choosing a
     * character must not be looking at another one's health and level.
     *
     * <p>None of it is lost: the items come out of the database on entry, the hearts out of B04's
     * stored resources, and the bar out of B06's stored progress. This only clears the display until
     * the character that owns those values is in play.
     */
    private void resetToNeutralState(org.bukkit.entity.Player player, ExperienceBar bar) {
        clearCarriedItems(player);
        bar.reset(player);
        // Full hearts, because a half-empty bar belongs to the character that emptied it. The real
        // value arrives with the stat holder, which only exists once a character is chosen.
        org.bukkit.attribute.AttributeInstance maxHealth =
                player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            player.setHealth(maxHealth.getValue());
        }
    }

    /**
     * Writes down what a player is carrying, for the character they are playing.
     *
     * <p>Must run on the player's tick - reading an inventory is a tick-only call - and must run while
     * they are still there. Both are why this is called from the quit observer and from a periodic
     * sweep rather than from the module that stores the result.
     */
    private void captureInventory(org.bukkit.entity.Player player) {
        inventoryModule
                .characterOf(player.getUniqueId())
                .ifPresent(
                        characterId ->
                                inventoryModule.store(
                                        characterId,
                                        rpg.platform.inventory.PlayerInventoryContents.capture(
                                                player, getLogger()),
                                        rpg.platform.inventory.PlayerInventoryContents
                                                .captureEnderChest(player, getLogger())));
    }

    /**
     * Snapshots every online player's inventory, again and again.
     *
     * <p>Without this a crash costs the whole session's loot: the quit path captures, but a crash has no
     * quit path. With it the loss is bounded by the interval, which is the same promise B02 makes for
     * everything else it writes.
     *
     * <p>Re-schedules itself instead of using a repeating task, because the scheduler has none - and
     * deliberately so (ADR-007). Each capture hops onto the owning player's tick; the waiting happens
     * off it.
     */
    private void startInventorySweep(Duration interval) {
        scheduler.runAsyncDelayed(
                interval,
                () -> {
                    // The module's list, not the server's: asking the server for its online players
                    // from off the tick is not safe, and this is the more precise question anyway -
                    // someone still sitting in the selection has no character to capture for.
                    for (java.util.UUID playerId : inventoryModule.playersInPlay()) {
                        scheduler.runSyncOnEntity(
                                new rpg.core.scheduler.EntityRef(playerId),
                                () -> {
                                    org.bukkit.entity.Player player = getServer().getPlayer(playerId);
                                    if (player != null) {
                                        captureInventory(player);
                                    }
                                });
                    }
                    if (isEnabled()) {
                        startInventorySweep(interval);
                    }
                });
    }

    /**
     * Assembles the Paper-facing half of B06.
     *
     * <p>Two extension points and one subscriber. The proximity check is the only part of this block
     * that needs Bukkit at all; the death listener hangs off the <b>core</b> event bus, because that
     * is where B05 publishes - and it does so while Bukkit's death handling is still running, which
     * is what makes reading the creature's location safe.
     */
    private void assembleProgressionLayer() {
        DefaultProgression progression = progressionModule.progression();
        StatEngine stats = registry.getService(StatEngine.class);

        progression.setProximityCheck(new PaperProximityCheck(getServer()));

        PartyRegistry parties =
                new PartyRegistry(
                        sessionModule.registry(),
                        eventBus,
                        Clock.systemUTC(),
                        progressionModule.config().partyMaxSize(),
                        progressionModule.config().inviteTimeout());
        registry.registerService(ProgressionModule.ID, PartyRegistry.class, parties);
        sessionModule.lifecycle().addAttachment(new PartySessionAttachment(parties));

        XpDistributor distributor =
                new XpDistributor(
                        progression,
                        parties,
                        stats,
                        progressionModule.config(),
                        getLogger());
        ProgressionDeathListener deaths =
                new ProgressionDeathListener(getServer(), distributor, getLogger());
        deaths.subscribeTo(eventBus);

        getLogger()
                .info(
                        "[progression] listeners registered - max level "
                                + progression.maxLevel()
                                + ", party range "
                                + progressionModule.config().partyRange()
                                + " blocks");
    }

    /**
     * Removes a player from their party when their session ends (FR-034).
     *
     * <p>Separate from the attachment inside {@code ProgressionModule}, which handles the progress
     * state: the party lives in the plugin layer because it is assembled here, and one attachment
     * reaching across both would tie two lifetimes together that have nothing to do with each other.
     */
    private record PartySessionAttachment(PartyRegistry parties)
            implements rpg.core.session.SessionAttachment {

        @Override
        public String id() {
            return ProgressionModule.ID + "-party";
        }

        @Override
        public void onSessionOpened(
                rpg.core.session.PlayerSession session, rpg.core.session.SessionBundle bundle) {
            // Nothing to restore - a party is never persisted (FR-029).
        }

        @Override
        public void onSessionClosing(java.util.UUID playerId) {
            parties.onSessionEnded(playerId);
        }
    }

    /** The configuration loader; also the entry point B14's reload command will use. */
    public ConfigLoader configLoader() {
        return configLoader;
    }

    /** The registry other modules resolve services through. */
    public DefaultModuleRegistry registry() {
        return registry;
    }

    /** The internal event bus. */
    public EventBus eventBus() {
        return eventBus;
    }

    /** The scheduler abstraction; the only sanctioned way to schedule work (ADR-007). */
    public Scheduler scheduler() {
        return scheduler;
    }

    /**
     * The session lifecycle, so a bootstrap test can assert which blocks hooked into it.
     *
     * <p>Deliberately not published as a service - only the plugin assembles attachments, and a
     * block reaching for the lifecycle through the registry would be able to add one from anywhere.
     */
    public rpg.core.session.DefaultSessionLifecycle sessionLifecycle() {
        return sessionModule == null ? null : sessionModule.lifecycle();
    }

    /**
     * The stat engine as it was assembled, for the bootstrap test.
     *
     * <p>Not the {@link StatEngine} from the registry: what needs asserting is which base-value
     * suppliers a fully wired server ends up with, and that is not part of the interface other blocks
     * use.
     */
    public rpg.core.stats.DefaultStatEngine statEngine() {
        return statsModule.engine();
    }

    /**
     * The damage pipeline as it was assembled, for the bootstrap test.
     *
     * <p>Same reason as {@link #statEngine()}: what needs asserting is which interceptors a fully
     * wired server ends up with, and that is not part of the {@code CombatPipeline} interface other
     * blocks use.
     */
    public rpg.core.combat.DefaultCombatPipeline combatPipeline() {
        return combatModule == null ? null : combatModule.pipeline();
    }

    /** The bootstrap phase, which decides whether the server accepts player sessions (FR-013). */
    public BootstrapState bootstrapState() {
        return bootstrapState;
    }
}
