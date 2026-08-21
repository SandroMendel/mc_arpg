package rpg.persistence.stats;

import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import rpg.core.config.ConfigHandle;
import rpg.core.config.ConfigValidationException;
import rpg.core.module.Module;
import rpg.core.module.ModuleContext;
import rpg.core.persistence.AggregateType;
import rpg.core.persistence.PersistenceStartupException;
import rpg.core.session.PlayerCharacter;
import rpg.core.session.PlayerSession;
import rpg.core.session.SessionAttachment;
import rpg.core.session.SessionBundle;
import rpg.core.stats.CharacterResources;
import rpg.core.stats.DefaultStatEngine;
import rpg.core.stats.ResourcePool;
import rpg.core.stats.StatConfig;
import rpg.core.stats.StatEngine;
import rpg.core.stats.StatSnapshot;
import rpg.core.stats.VanillaAttributeBridge;
import rpg.persistence.PersistenceModule;
import rpg.persistence.session.SessionModule;

/**
 * Wires B04 into the server (B01's module contract).
 *
 * <p>Lives in {@code rpg-persistence} for the same reason {@link SessionModule} does: it has to
 * build a repository, and {@code rpg-platform} must not depend on this module. The Paper side - the
 * vanilla mirror and the regeneration guard - is constructed by the plugin and handed in through
 * {@link #installVanillaBridge}, exactly as B03 does with its listeners.
 *
 * <p>Depends on {@code session} rather than {@code persistence}: the readiness rule from FR-037
 * needs the session registry, and the session module already depends on persistence.
 */
public final class StatsModule implements Module {

    /** Stable identifier, independent of this class's name (B01/FR-001a). */
    public static final String ID = "stats";

    /**
     * The attachment order of this block: after every supplier of base values.
     *
     * <p>A number rather than a position in a list, so a later block declares "before stats" by leaving
     * {@link SessionAttachment#order()} at its default and nobody has to edit this file.
     */
    private static final int LATE = 100;

    private static final String CONFIG_FILE = "stats.yml";

    private final PersistenceModule persistence;
    private final SessionModule sessions;
    private final Logger logger;
    private final Clock clock;

    /** Which character each online holder belongs to; the write path is keyed by character. */
    private final Map<UUID, UUID> characterOfHolder = new ConcurrentHashMap<>();

    /** Values kept just long enough for the flush that follows a logout. */
    private final Map<UUID, ResourcePool> lastKnown = new ConcurrentHashMap<>();

    private DefaultStatEngine engine;
    private JdbcCharacterResourcesRepository resources;
    private ConfigHandle<StatConfig> configHandle;

    public StatsModule(
            PersistenceModule persistence, SessionModule sessions, Logger logger, Clock clock) {
        this.persistence = persistence;
        this.sessions = sessions;
        this.logger = logger;
        this.clock = clock;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<String> dependencies() {
        return List.of(SessionModule.ID);
    }

    @Override
    public void start(ModuleContext context) throws Exception {
        configHandle = loadConfig(context);
        StatConfig config = configHandle.get();

        engine =
                new DefaultStatEngine(
                        config,
                        context.scheduler(),
                        context.eventBus(),
                        sessions.registry(),
                        logger);

        resources =
                new JdbcCharacterResourcesRepository(
                        persistence.pools().loginPool(),
                        context.scheduler(),
                        persistence.flushCycle(),
                        clock);
        persistence.flushCycle().register(AggregateType.CHARACTER_STATS, resources);

        // The engine is the authority while a player is online (Principle IV); the flush asks it
        // for values rather than keeping a second copy that could disagree.
        resources.setLiveSource(this::poolOfCharacter);

        // Every resource change marks the character. No game event ever reaches the database
        // directly (FR-028, SC-012).
        engine.setResourceWriteMark(resources::markDirty);

        sessions.lifecycle().addAttachment(new StatSessionAttachment());

        context.registry().registerService(ID, StatEngine.class, engine);

        logger.info(
                "[stats] engine ready - "
                        + config.definitions().size()
                        + " attributes, recalculation is event-driven only");
    }

    @Override
    public void stop() throws Exception {
        engine = null;
        resources = null;
        characterOfHolder.clear();
    }

    /**
     * Builds and tears down a stat holder alongside its session (FR-019b, FR-027, FR-036).
     *
     * <p>The load half runs inside B03's load, while the player is still held: create the holder,
     * calculate it once, then restore the stored resources. That order matters - restoring first
     * would clamp against maxima that do not exist yet, and a player with a 2000 health build would
     * come back at 100.
     */
    private final class StatSessionAttachment implements SessionAttachment {

        @Override
        public String id() {
            return ID;
        }

        /**
         * Last of all, because this is the one that calculates.
         *
         * <p>B06 supplies the level and B07 the class and the equipment tiers, and both attach with the
         * default order - but their modules start <em>after</em> this one, so registration order alone
         * would run the calculation before its inputs were loaded. {@code restoreResources} clamps
         * against that snapshot, so a level 60 warrior would come back at the bare value from
         * {@code stats.yml}. See {@link SessionAttachment#order()}.
         */
        @Override
        public int order() {
            return LATE;
        }

        @Override
        public void onSessionOpened(PlayerSession session, SessionBundle bundle) {
            Optional<PlayerCharacter> active = session.activeCharacter();
            if (active.isEmpty()) {
                // No character yet - nothing to compute. The holder appears when one is created.
                return;
            }
            UUID characterId = active.get().characterId();
            UUID playerId = session.playerId();

            StatSnapshot snapshot = build(playerId, characterId);

            ResourcePool restored =
                    bundle.resourcesOf(characterId)
                            .map(CharacterResources::toPool)
                            // No stored row means a new character: it starts full (FR-027).
                            .orElseGet(
                                    () ->
                                            ResourcePool.full(
                                                    snapshot.get(rpg.core.stats.Attribute.HEALTH),
                                                    snapshot.get(rpg.core.stats.Attribute.MANA)));
            engine.restoreResources(playerId, restored);
        }

        /**
         * A character entered play, so its holder is built now (FR-027).
         *
         * <p>The same work {@code onSessionOpened} used to do, at the moment that now has a character.
         * Stored resources come out of the bundle; a character created moments ago has none and starts
         * full.
         *
         * <p>Runs before B07 lets the player into the world, which is the same guarantee the open path
         * gives before the release: nobody is ever in play without values.
         */
        @Override
        public void onCharacterActivated(
                PlayerSession session, PlayerCharacter character, SessionBundle bundle) {
            UUID playerId = session.playerId();
            UUID characterId = character.characterId();
            StatSnapshot snapshot = build(playerId, characterId);
            ResourcePool restored =
                    bundle.resourcesOf(characterId)
                            .map(CharacterResources::toPool)
                            .orElseGet(
                                    () ->
                                            ResourcePool.full(
                                                    snapshot.get(rpg.core.stats.Attribute.HEALTH),
                                                    snapshot.get(rpg.core.stats.Attribute.MANA)));
            engine.restoreResources(playerId, restored);
        }

        /**
         * Creates the holder and calculates it once.
         *
         * <p>The recalculation is immediate rather than bundled: on the open path the player is
         * released right afterwards, and FR-019b forbids releasing anyone with an outstanding mark.
         */
        private StatSnapshot build(UUID playerId, UUID characterId) {
            engine.createForCharacter(playerId, characterId, new ResourcePool(0.0, 0.0));
            characterOfHolder.put(playerId, characterId);
            return engine.recalculateNow(playerId);
        }

        @Override
        public void onSessionClosing(UUID playerId) {
            UUID characterId = characterOfHolder.remove(playerId);
            if (characterId == null) {
                return;
            }
            // Mark before dropping the holder: the flush reads the value out of the engine, so the
            // order is not cosmetic.
            engine.resourcePool(playerId).ifPresent(pool -> lastKnown.put(characterId, pool));
            resources.markDirty(characterId);
            engine.remove(playerId);
        }
    }

    /**
     * The live value for one character, for the flush.
     *
     * <p>Falls back to the last value seen at logout: a session that ended between the mark and the
     * flush no longer has a holder, and dropping the write there would lose exactly the change that
     * logging out was supposed to save.
     */
    private Optional<ResourcePool> poolOfCharacter(UUID characterId) {
        for (Map.Entry<UUID, UUID> entry : characterOfHolder.entrySet()) {
            if (entry.getValue().equals(characterId)) {
                Optional<ResourcePool> live = engine.resourcePool(entry.getKey());
                if (live.isPresent()) {
                    return live;
                }
            }
        }
        return Optional.ofNullable(lastKnown.remove(characterId));
    }

    /** The engine, for the plugin's wiring. Other blocks use the registered {@link StatEngine}. */
    public DefaultStatEngine engine() {
        return engine;
    }

    /**
     * The validated configuration, for blocks that need to check their own values against the caps.
     *
     * <p>B07 does: its top equipment tier must not push an attribute past the cap, because everything
     * above it would be unreachable and nothing at runtime would report it. Reading the definitions
     * through {@link StatEngine} is not possible on purpose - the engine exposes calculated values, not
     * the ranges they are calculated within.
     */
    public StatConfig config() {
        return configHandle.get();
    }

    /**
     * Picks up a reloaded {@code stats.yml} (User Story 7, scenario 4).
     *
     * <p>Called by the plugin after B01's loader accepted a new set of documents. A rejected reload
     * never gets here, so the previously valid configuration simply stays in force - which is the
     * behaviour B01's loader already provides and this block does not need to reinvent.
     */
    public void applyReloadedConfig() {
        if (engine != null && configHandle != null) {
            engine.reload(configHandle.get());
            logger.info("[stats] configuration reloaded - every holder will recalculate");
        }
    }

    private ConfigHandle<StatConfig> loadConfig(ModuleContext context) {
        try {
            return context.configLoader().register(Path.of(CONFIG_FILE), StatConfig.schema());
        } catch (ConfigValidationException invalid) {
            logger.log(Level.SEVERE, "[stats] configuration rejected", invalid);
            throw new PersistenceStartupException(
                    "stats configuration is invalid: " + invalid.getMessage(), invalid);
        }
    }

    /**
     * Installs the Paper-side mirror (FR-032, FR-034).
     *
     * <p>Called by the plugin after the bootstrap, because the bridge needs a running server and
     * this module must not know about one.
     */
    public void installVanillaBridge(VanillaAttributeBridge bridge) {
        if (engine != null) {
            engine.registerVanillaBridge(bridge);
        }
    }
}
