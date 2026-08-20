package rpg.plugin;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import org.bukkit.plugin.java.JavaPlugin;

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
import rpg.core.scheduler.Scheduler;
import rpg.core.session.SessionMessageKeys;
import rpg.core.stats.StatConfig;
import rpg.core.stats.StatEngine;
import rpg.persistence.PersistenceMessageKeys;
import rpg.persistence.PersistenceModule;
import rpg.persistence.session.SessionModule;
import rpg.persistence.stats.StatsModule;
import rpg.platform.PlatformMessageKeys;
import rpg.platform.PreJoinGuard;
import rpg.platform.combat.CombatDeathListener;
import rpg.platform.combat.MobEquipmentListener;
import rpg.platform.combat.PaperDamageFeedback;
import rpg.platform.combat.PaperMobStatProvider;
import rpg.platform.combat.ProjectileCombatListener;
import rpg.platform.combat.ProjectileDamageTag;
import rpg.platform.combat.VanillaDamageListener;
import rpg.platform.combat.VanillaDamageMapping;
import rpg.platform.config.YamlConfigLoader;
import rpg.platform.scheduler.PaperSchedulerAdapter;
import rpg.platform.session.PendingSessionStash;
import rpg.platform.session.SafeStateGuard;
import rpg.platform.session.SessionConnectionCloseListener;
import rpg.platform.session.SessionJoinListener;
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
     * Configuration files written out on first start.
     *
     * <p>A module whose file is missing refuses to start, which is the right behaviour for a running
     * server and the wrong first impression for an operator who just dropped the jar in. Shipping a
     * default of each means the first start works and the file is there to be edited.
     */
    private static final List<String> DEFAULT_CONFIG_FILES =
            List.of("persistence.yml", "session.yml", "stats.yml", "combat.yml");

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

        registerSessionListeners();
        assembleStatLayer();
        assembleCombatLayer();

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
        return List.of(persistenceModule, sessionModule, statsModule, combatModule);
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
    private void registerSessionListeners() {
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
                                sessionModule.lifecycle(), stash, safeState, getLogger()),
                        this);
        getServer()
                .getPluginManager()
                .registerEvents(
                        new SessionQuitListener(sessionModule.lifecycle(), safeState, getLogger()),
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

    /** Whether the server currently accepts player sessions (FR-013). */
    public BootstrapState bootstrapState() {
        return bootstrapState;
    }
}
