package rpg.persistence.classes;

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

import rpg.core.classes.BoundEquipment;
import rpg.core.classes.CharacterClassDefinition;
import rpg.core.classes.ClassConfig;
import rpg.core.classes.ClassConfigSchema;
import rpg.core.classes.ClassProgress;
import rpg.core.classes.ClassRegistry;
import rpg.core.classes.ClassSelection;
import rpg.core.classes.ClassSlot;
import rpg.core.classes.ClassStatContributor;
import rpg.core.classes.LadderSlot;
import rpg.core.classes.TierAdvance;
import rpg.core.classes.TierAdvancedEvent;
import rpg.core.config.ConfigHandle;
import rpg.core.config.ConfigValidationException;
import rpg.core.module.Module;
import rpg.core.module.ModuleContext;
import rpg.core.persistence.AggregateType;
import rpg.core.progression.CharacterProgress;
import rpg.core.progression.LevelStatContributor;
import rpg.core.progression.ProgressState;
import rpg.core.session.CharacterClass;
import rpg.core.session.PlayerCharacter;
import rpg.core.session.PlayerSession;
import rpg.core.session.SessionAttachment;
import rpg.core.session.SessionBundle;
import rpg.core.stats.StatEngine;
import rpg.persistence.PersistenceModule;
import rpg.persistence.progression.ProgressionModule;
import rpg.persistence.session.SessionModule;
import rpg.persistence.stats.StatsModule;

/**
 * Wires B07 into the plugin (ADR-012).
 *
 * <p>Lives in {@code rpg-persistence} for the same reason B06 does: the block has a database, and the
 * module belongs where its repository is.
 *
 * <p>Depends on B06, not only on B04. The level decides which tier a character may advance to and
 * which abilities are unlocked, and both come from {@code Progression}.
 *
 * <p><b>This module takes B06's level growth away.</b> Every character has a class -
 * {@code character_class} is {@code NOT NULL} since B03 - so once {@link ClassStatContributor} is in
 * place, B06's class-neutral {@code LevelStatContributor} has no remaining subject, and leaving both
 * registered would apply the growth twice with nothing looking wrong. B06 still registers it, for a
 * server assembled without this module; {@link #start} removes it again, and fails the start if it was
 * not there to remove.
 */
public final class ClassesModule implements Module {

    public static final String ID = "classes";
    private static final String CONFIG_FILE = "classes.yml";

    private final PersistenceModule persistence;
    private final SessionModule sessions;
    private final StatsModule stats;
    private final ProgressionModule progression;
    private final Logger logger;
    private final Clock clock;

    /**
     * The tier state of every character currently online, keyed by character (Constitution IV).
     *
     * <p>Carries the player and the class alongside the tiers because both are needed by callers that
     * only have a character id: the recalculation needs the player - the holder id of a character
     * <em>is</em> the player id - and every value lookup needs the class. Reading them out of the
     * session on each call would mean two lookups in a path called on every recalculation.
     */
    private final Map<UUID, Live> live = new ConcurrentHashMap<>();

    /** The reverse direction, for the quit path, which only ever has a player id. */
    private final Map<UUID, UUID> characterByPlayer = new ConcurrentHashMap<>();

    /**
     * The tiers of a character whose session just ended.
     *
     * <p>Without this the last advance of a session would be lost. The flush is asynchronous and
     * normally runs <em>after</em> the release, at which point the live entry is gone, the writer finds
     * nothing to write and drops the mark. B04 and B06 keep the same stash for the same reason.
     */
    private final Map<UUID, ClassProgress> lastKnown = new ConcurrentHashMap<>();

    private ConfigHandle<ClassConfig> configHandle;
    private JdbcClassProgressRepository repository;
    private ClassRegistry registry;
    private ClassSelection selection;
    private TierAdvance tierAdvance;
    private BoundEquipment boundEquipment;

    public ClassesModule(
            PersistenceModule persistence,
            SessionModule sessions,
            StatsModule stats,
            ProgressionModule progression,
            Logger logger,
            Clock clock) {
        this.persistence = Objects.requireNonNull(persistence, "persistence");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.stats = Objects.requireNonNull(stats, "stats");
        this.progression = Objects.requireNonNull(progression, "progression");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<String> dependencies() {
        // Stats because the class contributes base values; progression because the level gates both
        // tier advances and ability unlocks.
        return List.of(StatsModule.ID, ProgressionModule.ID);
    }

    @Override
    public void start(ModuleContext context) throws Exception {
        configHandle = loadConfig(context);
        ClassConfig config = configHandle.get();

        // Before anything reads a value: a configuration whose top tier exceeds the caps from ADR-008
        // would leave part of the end power unreachable, and nothing at runtime would say so (V13).
        config.validateAgainstCaps(
                stats.config()::definition, progression.config().maxLevel());

        StatEngine engine = context.registry().getService(StatEngine.class);

        repository =
                new JdbcClassProgressRepository(
                        persistence.pools().loginPool(),
                        context.scheduler(),
                        persistence.flushCycle(),
                        clock);
        // Registration 3 of 3 (ADR-015). The other two are the AggregateType constant and its place in
        // FlushCycle.WRITE_ORDER, after CHARACTER - a child is written after its parent.
        persistence.flushCycle().register(AggregateType.CHARACTER_CLASS_PROGRESS, repository);

        // V19, and the only check here that needs the database: a ladder shortened below what a
        // character has already reached (FR-024). Refusing to start is the point - demoting them
        // silently would take away something they earned, on the quietest possible path, namely a
        // balancing edit. Runs before anything can be loaded, so no session ever sees the mismatch.
        JdbcClassProgressRepository.StoredTiers stored =
                repository.readAll(persistence.pools().loginPool());
        config.validateAgainstStoredTiers(stored.tiers(), stored.classOf());
        logger.fine(
                () ->
                        "[classes] "
                                + stored.tiers().size()
                                + " stored tier row(s) checked against the configured ladders");
        // The live state is authoritative while the character is online, so the flush asks it rather
        // than keeping a second copy that could disagree. Not progressOf: the flush needs the stash
        // too, and the rules must never see a consuming read.
        repository.setLiveSource(this::progressForFlush);

        registry = new ClassRegistry(config, progression.progression()::levelOrZero);
        boundEquipment = new BoundEquipment(config, this::classOf, this::progressOf);
        selection = new ClassSelection(sessions.characters(), context.eventBus(), logger);
        tierAdvance =
                new TierAdvance(
                        config,
                        this::classOf,
                        progression.progression()::levelOrZero,
                        this::progressOf,
                        this::store,
                        repository,
                        context.eventBus());

        // B06's class-neutral growth stops here, because B07 supplies the growth per class. Both
        // registered would apply it twice, and nothing at runtime would look wrong - the numbers would
        // just be too high (FR-003). B06 keeps its contributor for a server assembled without this
        // module; which of the two runs is decided by the wiring, which is this line.
        if (!engine.unregisterBaseStatContributor(LevelStatContributor.ID)) {
            throw new IllegalStateException(
                    "expected B06's '"
                            + LevelStatContributor.ID
                            + "' contributor to be registered before the class module takes over the "
                            + "level growth, but nothing was removed - the growth would be applied twice");
        }

        // Base values, not modifiers (research.md R1, same reasoning as ADR-013 for the level). The
        // modifier band is laid around the effective base, and with the ladder carrying most of the end
        // power a band anchored to tier 1 would be far too narrow at the top.
        engine.registerBaseStatContributor(
                new ClassStatContributor(
                        config, this::classOf, progression.progression()::levelOrZero, this::progressOf));

        // One advance, one recalculation (SC-009). TierAdvance knows characters, not holders, so the
        // module is what turns its event into the recalculation.
        context.eventBus()
                .subscribe(TierAdvancedEvent.class, event -> recalculate(engine, event.characterId()));

        sessions.lifecycle().addAttachment(new ClassSessionAttachment());
        context.registry().registerService(ID, ClassRegistry.class, registry);

        logger.info(
                "[classes] ready - "
                        + config.definitions().size()
                        + " classes, ladders (armor/weapon) "
                        + ladderSummary(config)
                        + ", level growth taken over from progression");
    }

    @Override
    public void stop() throws Exception {
        live.clear();
        characterByPlayer.clear();
        lastKnown.clear();
        repository = null;
        registry = null;
        selection = null;
        tierAdvance = null;
        boundEquipment = null;
    }

    public ClassRegistry registry() {
        return registry;
    }

    public ClassSelection selection() {
        return selection;
    }

    public TierAdvance tierAdvance() {
        return tierAdvance;
    }

    public BoundEquipment boundEquipment() {
        return boundEquipment;
    }

    public JdbcClassProgressRepository repository() {
        return repository;
    }

    /** The validated configuration, for the plugin layer that builds the menu and the items. */
    public ClassConfig config() {
        return configHandle.get();
    }

    /** The character a player is currently playing, if any. */
    public Optional<UUID> characterOf(UUID playerId) {
        return Optional.ofNullable(characterByPlayer.get(playerId));
    }

    /**
     * What the selection menu shows: every class, with what this account has reached in it.
     *
     * <p>Answered from the rows the login read, held by the lifecycle until the session ends. None of
     * these characters is loaded - only the one that gets chosen will be - so their level and tiers
     * cannot come from the live state, and a query per menu build is out of the question on the tick.
     */
    public List<ClassSlot> slotsFor(PlayerSession session) {
        Objects.requireNonNull(session, "session");
        SessionBundle bundle =
                sessions.lifecycle()
                        .loadedBundle(session.playerId())
                        .orElseGet(() -> SessionBundle.empty(session.playerId()));
        return selection.slots(
                session,
                characterId ->
                        bundle.progressOf(characterId)
                                .map(CharacterProgress::toState)
                                .map(ProgressState::level)
                                // No row means a character that has never gained anything: level 1,
                                // the same answer B06 gives for a fresh one (FR-058).
                                .orElse(1),
                bundle::classProgressOf);
    }

    // --- what the rules read -----------------------------------------------------------------

    /**
     * The class of a character currently online, or empty.
     *
     * <p>Public since B08: the ability registry needs it to resolve a loadout, and it sits in another
     * package. Still a read of live session state, not a query.
     */
    public Optional<CharacterClass> classOf(UUID characterId) {
        return Optional.ofNullable(live.get(characterId)).map(Live::characterClass);
    }

    Optional<ClassProgress> progressOf(UUID characterId) {
        return Optional.ofNullable(live.get(characterId)).map(Live::progress);
    }

    /**
     * What the flush should write: the live tiers while the character is loaded, otherwise the value
     * stashed when the session closed.
     *
     * <p>The stash entry is consumed on read. It exists for exactly one final write, and keeping it
     * would leak an entry for every character that was ever played.
     */
    Optional<ClassProgress> progressForFlush(UUID characterId) {
        Optional<ClassProgress> current = progressOf(characterId);
        if (current.isPresent()) {
            return current;
        }
        return Optional.ofNullable(lastKnown.remove(characterId));
    }

    /**
     * Takes an advanced tier back.
     *
     * <p>Keeps the player and the class of the entry it replaces. A blind {@code put} would need both
     * again and would silently create a half-filled entry for a character that is not online.
     */
    void store(ClassProgress progress) {
        live.computeIfPresent(
                progress.characterId(), (id, previous) -> previous.withProgress(progress));
    }

    private void recalculate(StatEngine engine, UUID characterId) {
        Live entry = live.get(characterId);
        if (entry == null) {
            // The character went offline between the advance and this event. Nothing to recalculate -
            // the tier is persisted, and the next login computes with it.
            return;
        }
        // The holder id of a character is its player id (B04's createForCharacter).
        engine.recalculateNow(entry.playerId());
    }

    private static String ladderSummary(ClassConfig config) {
        StringBuilder summary = new StringBuilder();
        for (Map.Entry<CharacterClass, CharacterClassDefinition> entry :
                config.definitions().entrySet()) {
            if (summary.length() > 0) {
                summary.append(", ");
            }
            summary.append(entry.getKey())
                    .append(' ')
                    .append(entry.getValue().ladder(LadderSlot.ARMOR).length())
                    .append('/')
                    .append(entry.getValue().ladder(LadderSlot.WEAPON).length());
        }
        return summary.toString();
    }

    private ConfigHandle<ClassConfig> loadConfig(ModuleContext context) {
        try {
            return context.configLoader().register(Path.of(CONFIG_FILE), ClassConfigSchema.schema());
        } catch (ConfigValidationException invalid) {
            logger.log(Level.SEVERE, "[classes] configuration rejected", invalid);
            throw new IllegalStateException(
                    "class configuration is invalid: " + invalid.getMessage(), invalid);
        }
    }

    /** Player, class and tiers of one character while it is online. */
    private record Live(UUID playerId, CharacterClass characterClass, ClassProgress progress) {

        Live withProgress(ClassProgress replacement) {
            return new Live(playerId, characterClass, replacement);
        }
    }

    /**
     * Loads the tier state when the session opens and releases it when it closes.
     *
     * <p>The tiers come out of the bundle rather than from a query of their own: the class contributes
     * tier values to the base stats, and a tier that arrived after the session was declared ready would
     * mean the first snapshot computes with tier 1 and then visibly corrects itself.
     */
    private final class ClassSessionAttachment implements SessionAttachment {

        @Override
        public String id() {
            return ID;
        }

        @Override
        public void onSessionOpened(PlayerSession session, SessionBundle bundle) {
            Optional<PlayerCharacter> active = session.activeCharacter();
            if (active.isEmpty()) {
                // No character yet - the selection has not happened. Nothing to load and nothing to
                // invent: tier state appears when a character does (ADR-020).
                return;
            }
            PlayerCharacter character = active.get();
            UUID characterId = character.characterId();
            ClassProgress progress =
                    bundle.classProgressOf(characterId)
                            // No stored row means a fresh character: tier 1 of both ladders (US3.1).
                            .orElseGet(() -> ClassProgress.initial(characterId));
            take(session.playerId(), character, progress);
        }

        /**
         * A character entered play, so its tiers come into memory.
         *
         * <p>Out of the bundle, not out of a query: a character that already existed has its tiers in
         * the rows the login read, and one created a moment ago has none and starts at tier 1 of both
         * ladders (US3.1). Either way nothing is asked of the database on the tick.
         */
        @Override
        public void onCharacterActivated(
                PlayerSession session, PlayerCharacter character, SessionBundle bundle) {
            UUID characterId = character.characterId();
            take(
                    session.playerId(),
                    character,
                    bundle.classProgressOf(characterId)
                            .orElseGet(() -> ClassProgress.initial(characterId)));
        }

        private void take(UUID playerId, PlayerCharacter character, ClassProgress progress) {
            live.put(
                    character.characterId(),
                    new Live(playerId, character.characterClass(), progress));
            characterByPlayer.put(playerId, character.characterId());
        }

        @Override
        public void onSessionClosing(UUID playerId) {
            UUID characterId = characterByPlayer.remove(playerId);
            if (characterId == null) {
                return;
            }
            // Stash, mark, release - in that order. The flush reads through the live source, and
            // after the release there is nothing live left to read.
            progressOf(characterId).ifPresent(progress -> lastKnown.put(characterId, progress));
            repository.markDirty(characterId);
            live.remove(characterId);
        }
    }
}
