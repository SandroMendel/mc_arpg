package rpg.persistence.progression;

import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
import rpg.core.persistence.AuditLogRepository;
import rpg.core.progression.CharacterProgress;
import rpg.core.progression.DefaultProgression;
import rpg.core.progression.LevelStatContributor;
import rpg.core.progression.ProgressState;
import rpg.core.progression.Progression;
import rpg.core.progression.ProgressionConfig;
import rpg.core.progression.ProgressionConfigSchema;
import rpg.core.session.PlayerCharacter;
import rpg.core.session.PlayerSession;
import rpg.core.session.SessionAttachment;
import rpg.core.session.SessionBundle;
import rpg.core.stats.StatEngine;
import rpg.persistence.PersistenceModule;
import rpg.persistence.session.SessionModule;
import rpg.persistence.stats.StatsModule;

/**
 * Wires B06 into the plugin (ADR-012).
 *
 * <p>Lives in {@code rpg-persistence} like {@code SessionModule} and {@code StatsModule}, and unlike
 * {@code CombatModule} in {@code rpg-core}. The difference has a reason: B05 has no database, B06
 * has one. Putting the module where its repository is makes the dependency visible instead of
 * hiding it.
 *
 * <p>{@link ProgressSessionAttachment} is the piece without which the whole block would be dead
 * code: it loads a character's progress when the session opens and releases it when the session
 * closes. Everything else here could be perfect and no character would ever have a level - exactly
 * the failure class ADR-012 was written about.
 */
public final class ProgressionModule implements Module {

    public static final String ID = "progression";
    private static final String CONFIG_FILE = "progression.yml";

    private final PersistenceModule persistence;
    private final SessionModule sessions;
    private final Logger logger;
    private final Clock clock;

    private DefaultProgression progression;
    private JdbcCharacterProgressRepository repository;
    private ConfigHandle<ProgressionConfig> configHandle;

    /**
     * The last state of a character whose session just ended.
     *
     * <p>Without this the final progress of every session would be lost. The flush is asynchronous
     * and normally runs <em>after</em> {@code release}, at which point the live state is gone and the
     * writer has nothing to write - so it drops the mark and the last gains vanish. Stashing the
     * value before releasing closes that window. B04 keeps exactly the same map for exactly the same
     * reason.
     */
    private final Map<UUID, ProgressState> lastKnown = new ConcurrentHashMap<>();

    public ProgressionModule(
            PersistenceModule persistence, SessionModule sessions, Logger logger, Clock clock) {
        this.persistence = Objects.requireNonNull(persistence, "persistence");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<String> dependencies() {
        // Stats, because the level contributes to base values through B04; session, because every
        // grant is bound to a ready session.
        return List.of(StatsModule.ID);
    }

    @Override
    public void start(ModuleContext context) throws Exception {
        configHandle = loadConfig(context);
        ProgressionConfig config = configHandle.get();

        repository =
                new JdbcCharacterProgressRepository(
                        persistence.pools().loginPool(),
                        context.scheduler(),
                        persistence.flushCycle(),
                        clock);
        persistence.flushCycle().register(AggregateType.CHARACTER_PROGRESS, repository);

        StatEngine stats = context.registry().getService(StatEngine.class);
        // Through the registry, not through PersistenceModule: B02 publishes the audit log as a
        // service, and reaching for a field of another module instead would be exactly the access
        // to internals Principle III rules out.
        AuditLogRepository auditLog = context.registry().getService(AuditLogRepository.class);
        progression =
                new DefaultProgression(
                        config,
                        stats,
                        context.eventBus(),
                        sessions.registry(),
                        repository,
                        auditLog,
                        clock,
                        logger);

        // The rules are authoritative while a character is online (Principle IV); the flush asks
        // them rather than keeping a second copy that could disagree. Once the character is released
        // the stash answers, so the last gains of a session are not lost to a flush that arrives a
        // moment too late (FR-056).
        repository.setLiveSource(this::stateOfCharacter);

        // The level feeds B04 as a BASE contribution, per ADR-013 - not as a modifier under
        // SourceKind.LEVEL. A flat modifier would sit inside a band anchored to the level-1 base,
        // and B11's equipment would be mis-clamped at level 60.
        stats.registerBaseStatContributor(
                new LevelStatContributor(config.growth(), progression::levelOrZero));

        sessions.lifecycle().addAttachment(new ProgressSessionAttachment());

        context.registry().registerService(ID, Progression.class, progression);

        logger.info(
                "[progression] ready - max level "
                        + config.maxLevel()
                        + " from the curve, party up to "
                        + config.partyMaxSize()
                        + " within "
                        + config.partyRange()
                        + " blocks, no scheduled work at idle");
    }

    @Override
    public void stop() throws Exception {
        progression = null;
        repository = null;
    }

    public DefaultProgression progression() {
        return progression;
    }

    public JdbcCharacterProgressRepository repository() {
        return repository;
    }

    /** The validated configuration, for the plugin layer that assembles the party and the split. */
    public ProgressionConfig config() {
        return configHandle.get();
    }

    /**
     * What the flush should write for this character: the live state while it is loaded, otherwise
     * the value stashed when the session closed.
     *
     * <p>The stash entry is consumed on read - it exists for exactly one final write, and keeping it
     * would leak an entry for every player who ever connected.
     */
    Optional<ProgressState> stateOfCharacter(UUID characterId) {
        Optional<ProgressState> live = progression.stateOf(characterId);
        if (live.isPresent()) {
            return live;
        }
        return Optional.ofNullable(lastKnown.remove(characterId));
    }

    private ConfigHandle<ProgressionConfig> loadConfig(ModuleContext context) {
        try {
            return context.configLoader()
                    .register(Path.of(CONFIG_FILE), ProgressionConfigSchema.schema());
        } catch (ConfigValidationException invalid) {
            logger.log(Level.SEVERE, "[progression] configuration rejected", invalid);
            throw new IllegalStateException(
                    "progression configuration is invalid: " + invalid.getMessage(), invalid);
        }
    }

    /**
     * Loads progress on session open and releases it on close (FR-034, FR-058).
     *
     * <p>Modelled on {@code StatSessionAttachment} in B04. Without this class {@code load} and
     * {@code release} would never be called: no character would get a level, and the promise against
     * leaks would be unproven.
     */
    private final class ProgressSessionAttachment implements SessionAttachment {

        @Override
        public String id() {
            return ID;
        }

        @Override
        public void onSessionOpened(PlayerSession session, SessionBundle bundle) {
            Optional<PlayerCharacter> active = session.activeCharacter();
            if (active.isEmpty()) {
                // No character yet - nothing to load. Progress appears when one is created.
                return;
            }
            UUID characterId = active.get().characterId();
            ProgressState state =
                    bundle.progressOf(characterId)
                            .map(CharacterProgress::toState)
                            // No stored row means a new character: level 1, no experience (FR-058).
                            .orElse(ProgressState.INITIAL);
            progression.load(characterId, session.playerId(), state);
        }

        /**
         * A character entered play, so its progress comes into memory.
         *
         * <p>Out of the bundle, not out of a query: an existing character has its row among what the
         * login read, and one created a moment ago has none and starts at level 1 (FR-058). Asking the
         * database here would be a query on the player's tick for rows already in hand.
         */
        @Override
        public void onCharacterActivated(
                PlayerSession session, PlayerCharacter character, SessionBundle bundle) {
            UUID characterId = character.characterId();
            ProgressState state =
                    bundle.progressOf(characterId)
                            .map(CharacterProgress::toState)
                            .orElse(ProgressState.INITIAL);
            progression.load(characterId, session.playerId(), state);
        }

        @Override
        public void onSessionClosing(UUID playerId) {
            progression.characterOf(playerId)
                    .ifPresent(
                            characterId -> {
                                // Stash, then mark, then release. Every step of that order matters:
                                // the flush reads through the live source, and after the release
                                // there is nothing live left to read (FR-056).
                                progression
                                        .stateOf(characterId)
                                        .ifPresent(state -> lastKnown.put(characterId, state));
                                repository.markDirty(characterId);
                                progression.release(characterId);
                            });
        }
    }
}
