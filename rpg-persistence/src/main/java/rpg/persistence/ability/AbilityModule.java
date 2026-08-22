package rpg.persistence.ability;

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

import rpg.core.ability.AbilityConfig;
import rpg.core.ability.AbilityConfigSchema;
import rpg.core.ability.AbilityRegistry;
import rpg.core.ability.AbilityState;
import rpg.core.classes.AbilityBinding;
import rpg.core.classes.ClassRegistry;
import rpg.core.config.ConfigHandle;
import rpg.core.config.ConfigValidationException;
import rpg.core.module.Module;
import rpg.core.module.ModuleContext;
import rpg.core.persistence.AggregateType;
import rpg.core.session.CharacterClass;
import rpg.core.session.PlayerCharacter;
import rpg.core.session.PlayerSession;
import rpg.core.session.SessionAttachment;
import rpg.core.session.SessionBundle;
import rpg.persistence.PersistenceModule;
import rpg.persistence.classes.ClassesModule;
import rpg.persistence.progression.ProgressionModule;
import rpg.persistence.session.SessionModule;

/**
 * B08 assembled and wired. Without this the block is inert however green its own tests are (ADR-012).
 *
 * <p>Lives in {@code rpg-persistence} like {@code ClassesModule} and for the same reason: it owns a
 * repository and a place in the flush order, and the module that owns those is the one that wires
 * them.
 *
 * <p><b>It starts after B07.</b> The cross-check between the class bindings and the ability
 * definitions needs both files loaded, and it is the promise B07 could not keep: there an ability id
 * travels as an opaque string because this block did not exist yet.
 */
public final class AbilityModule implements Module {

    public static final String ID = "abilities";
    private static final String CONFIG_FILE = "abilities.yml";

    private final PersistenceModule persistence;
    private final SessionModule sessions;
    private final ClassesModule classes;
    private final Logger logger;
    private final Clock clock;

    /** The character each player is currently playing - the quit path only ever has a player id. */
    private final Map<UUID, UUID> characterByPlayer = new ConcurrentHashMap<>();

    /**
     * What a character owned when its session ended.
     *
     * <p>Without this the last rank-up or the last cooldown of a session would be lost: the flush is
     * asynchronous and normally runs <em>after</em> the release, at which point the registry has
     * forgotten the character and the writer finds nothing to write. B04, B06 and B07 keep the same
     * stash for the same reason.
     */
    private final Map<UUID, List<AbilityState>> lastKnown = new ConcurrentHashMap<>();

    private ConfigHandle<AbilityConfig> configHandle;
    private JdbcAbilityStateRepository repository;
    private AbilityRegistry registry;

    public AbilityModule(
            PersistenceModule persistence,
            SessionModule sessions,
            ClassesModule classes,
            Logger logger,
            Clock clock) {
        this.persistence = Objects.requireNonNull(persistence, "persistence");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.classes = Objects.requireNonNull(classes, "classes");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public String id() {
        return ID;
    }

    /**
     * Classes because the loadouts name the ability ids this block resolves, and because the
     * cross-check needs both configurations. Progression because unlock follows from the level.
     *
     * <p>A constant rather than a literal inside the method, so the bootstrap test can assert the
     * ordering rule without constructing a module.
     */
    public static final List<String> DEPENDENCIES = List.of(ClassesModule.ID, ProgressionModule.ID);

    @Override
    public List<String> dependencies() {
        return DEPENDENCIES;
    }

    @Override
    public void start(ModuleContext context) throws Exception {
        configHandle = loadConfig(context);
        AbilityConfig config = configHandle.get();

        repository =
                new JdbcAbilityStateRepository(
                        persistence.pools().loginPool(),
                        context.scheduler(),
                        persistence.flushCycle(),
                        clock);
        // Registration 3 of 3 (ADR-015). The other two are the AggregateType constant and its place in
        // FlushCycle.WRITE_ORDER, after CHARACTER - a child is written after its parent.
        persistence.flushCycle().register(AggregateType.CHARACTER_ABILITIES, repository);

        ClassRegistry classRegistry = classes.registry();
        registry =
                new AbilityRegistry(
                        config,
                        this::classOf,
                        classRegistry::abilitiesOf,
                        this::unlockedFor,
                        clock);

        // V25 to V30: every id a class binding names has to be defined, and the kinds have to agree.
        // Runs here rather than in the schema because it is the one place that has seen both files.
        AbilityBindingCheck.validate(config, classRegistry, logger);

        // The live state is authoritative while the character is online, so the flush asks it rather
        // than keeping a second copy that could disagree. Not statesOf: the flush needs the stash too.
        repository.setLiveSource(this::statesForFlush);

        sessions.lifecycle().addAttachment(new AbilitySessionAttachment());
        context.registry().registerService(ID, AbilityRegistry.class, registry);

        logger.info(
                () ->
                        "[abilities] "
                                + config.size()
                                + " abilities loaded, global cooldown "
                                + config.globalCooldown().toMillis()
                                + " ms");
    }

    /** The read facade other blocks are built against - see {@code contracts/ability-api.md}. */
    public AbilityRegistry registry() {
        return registry;
    }

    public AbilityConfig config() {
        return configHandle.get();
    }

    private ConfigHandle<AbilityConfig> loadConfig(ModuleContext context) {
        try {
            return context.configLoader().register(Path.of(CONFIG_FILE), AbilityConfigSchema.schema());
        } catch (ConfigValidationException invalid) {
            logger.log(Level.SEVERE, "[abilities] configuration rejected", invalid);
            throw new IllegalStateException(
                    "ability configuration is invalid: " + invalid.getMessage(), invalid);
        }
    }

    private CharacterClass classOf(UUID characterId) {
        return classes.classOf(characterId).orElse(null);
    }

    private List<AbilityBinding> unlockedFor(UUID characterId) {
        CharacterClass id = classOf(characterId);
        return id == null ? List.of() : classes.registry().unlockedFor(id, characterId);
    }

    /**
     * What the flush writes: the live state, or the stash of a character that has just left.
     *
     * <p>The order matters. Asking the registry first and the stash second would lose the final write
     * of every session, because the release happens before the flush runs.
     */
    private List<AbilityState> statesForFlush(UUID characterId) {
        List<AbilityState> live = registry.statesOf(characterId);
        if (!live.isEmpty()) {
            return live;
        }
        return lastKnown.getOrDefault(characterId, List.of());
    }

    /** Loads the ranks when a character enters play and releases them when the session ends. */
    private final class AbilitySessionAttachment implements SessionAttachment {

        @Override
        public String id() {
            return ID;
        }

        @Override
        public void onSessionOpened(PlayerSession session, SessionBundle bundle) {
            Optional<PlayerCharacter> active = session.activeCharacter();
            // No character yet - the selection has not happened. Nothing to load and nothing to
            // invent: ability state appears when a character does (ADR-020, ADR-021).
            active.ifPresent(character -> take(session.playerId(), character, bundle));
        }

        /**
         * A character entered play, so its ranks and cooldowns come into memory.
         *
         * <p>Out of the bundle, not out of a query. The rank scales every number an ability produces,
         * so a character whose ranks arrived a moment later would briefly act at rank 1 and then
         * correct itself - the same argument B07 makes for its tiers.
         */
        @Override
        public void onCharacterActivated(
                PlayerSession session, PlayerCharacter character, SessionBundle bundle) {
            take(session.playerId(), character, bundle);
        }

        private void take(UUID playerId, PlayerCharacter character, SessionBundle bundle) {
            UUID characterId = character.characterId();
            registry.restore(characterId, bundle.abilitiesOf(characterId));
            characterByPlayer.put(playerId, characterId);
        }

        @Override
        public void onSessionClosing(UUID playerId) {
            UUID characterId = characterByPlayer.remove(playerId);
            if (characterId == null) {
                return;
            }
            // Stash before forgetting, so the asynchronous flush still finds something to write.
            List<AbilityState> states = registry.statesOf(characterId);
            if (states.isEmpty()) {
                lastKnown.remove(characterId);
            } else {
                lastKnown.put(characterId, states);
                repository.markDirty(characterId);
            }
            registry.forget(characterId);
        }
    }
}
