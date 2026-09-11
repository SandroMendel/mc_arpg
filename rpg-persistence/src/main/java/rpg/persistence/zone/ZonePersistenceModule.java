package rpg.persistence.zone;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

import rpg.core.module.Module;
import rpg.core.module.ModuleContext;
import rpg.core.persistence.AggregateType;
import rpg.core.session.PlayerCharacter;
import rpg.core.session.PlayerSession;
import rpg.core.session.SessionAttachment;
import rpg.core.session.SessionBundle;
import rpg.core.zone.ZoneCharacterState;
import rpg.core.zone.ZoneStateStore;
import rpg.persistence.PersistenceModule;
import rpg.persistence.session.SessionModule;

/**
 * Wires B09's persistence: the discovered crystals and the pending respawn.
 *
 * <p><b>Separate from {@code ZoneModule}, which lives in {@code rpg-core}.</b> The zone logic itself
 * has no database - geometry, the index and the rules are arithmetic. Only this part does, and it
 * lives here for the same reason B02, B03 and B04 put their modules in this package: the repository
 * is built where the JDBC lives.
 *
 * <p><b>The three registrations of ADR-015 point 7 are deliberately visible.</b> The constant is in
 * {@link AggregateType}, the position is in {@code FlushCycle.WRITE_ORDER}, and the third is the line
 * in {@link #start}. Missing the third does not fail loudly: the marks are counted as failed on every
 * flush and nothing is ever written, which looks like a database fault and is none. B06 learned that
 * the hard way, and {@code ZoneAggregateRegistrationTest} exists so B09 does not.
 */
public final class ZonePersistenceModule implements Module {

    /** Stable identifier, independent of this class's name (B01/FR-001a). */
    public static final String ID = "zone-persistence";

    private final PersistenceModule persistence;
    private final SessionModule sessions;
    private final Logger logger;
    private final Clock clock;

    private JdbcZoneStateRepository repository;
    private ZoneStateStore store;

    public ZonePersistenceModule(
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
        // The session only, like B08b. The zone rules themselves are not needed to store a row.
        return List.of(SessionModule.ID);
    }

    @Override
    public void start(ModuleContext context) {
        repository =
                new JdbcZoneStateRepository(
                        persistence.pools().loginPool(),
                        context.scheduler(),
                        persistence.flushCycle(),
                        clock);
        store = new ZoneStateStore(repository);
        // Where the flush reads the current state from: the in-memory copy, which is authoritative
        // while the character is online (Constitution IV.2).
        repository.setLiveSource(this::snapshotOf);
        // Registration 3 of 3 (ADR-015 point 7). The other two are the AggregateType constant and
        // its place in FlushCycle.WRITE_ORDER.
        persistence.flushCycle().register(AggregateType.CHARACTER_ZONE_STATE, repository);
        sessions.lifecycle().addAttachment(new ZoneSessionAttachment());
        logger.info("[zone-persistence] phase=START state=READY");
    }

    /**
     * Takes the stored state into memory when a character enters play, and releases it at the end.
     *
     * <p><b>Without this the block would be dead:</b> nothing would ever be loaded, every character
     * would look new, and every unlock would be forgotten at the end of the session. Modelled on
     * {@code CurrencySessionAttachment}, which exists for the same reason.
     *
     * <p>The state comes out of the bundle the login already read - no query here, which matters
     * because this runs on the tick (FR-063).
     */
    private final class ZoneSessionAttachment implements SessionAttachment {

        @Override
        public String id() {
            return ID;
        }

        @Override
        public void onSessionOpened(PlayerSession session, SessionBundle bundle) {
            // A session opens without a character and stays that way until the selection decides
            // (ADR-020). Nothing to load yet.
        }

        @Override
        public void onCharacterActivated(
                PlayerSession session, PlayerCharacter character, SessionBundle bundle) {
            store.load(character.characterId(), bundle.zoneStateOf(character.characterId()));
        }

        @Override
        public void onSessionClosing(UUID playerId) {
            sessions.registry()
                    .find(playerId)
                    .flatMap(PlayerSession::activeCharacter)
                    .ifPresent(character -> store.unload(character.characterId()));
        }
    }

    /** The in-memory authority for what a character carries out of this block. */
    public ZoneStateStore store() {
        return store;
    }

    /** For the session load, which reads the row before handing it to the store. */
    public JdbcZoneStateRepository repository() {
        return repository;
    }

    /**
     * What the flush should write for this character.
     *
     * <p>Empty when the character is not loaded - they left, and there is nothing in memory that
     * could be newer than the row.
     */
    private Optional<ZoneCharacterState> snapshotOf(UUID characterId) {
        if (store == null) {
            return Optional.empty();
        }
        if (!store.isLoaded(characterId)) {
            return Optional.empty();
        }
        return Optional.of(
                new ZoneCharacterState(
                        characterId,
                        store.unlockedBy(characterId),
                        store.pendingRespawnOf(characterId)));
    }
}
