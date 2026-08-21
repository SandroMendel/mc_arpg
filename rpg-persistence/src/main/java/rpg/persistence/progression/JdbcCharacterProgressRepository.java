package rpg.persistence.progression;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import javax.sql.DataSource;

import rpg.core.persistence.AggregateType;
import rpg.core.persistence.DirtyMark;
import rpg.core.persistence.PersistenceException;
import rpg.core.persistence.WriteBehindCoordinator;
import rpg.core.progression.CharacterProgress;
import rpg.core.progression.CharacterProgressRepository;
import rpg.core.progression.ProgressState;
import rpg.core.scheduler.Scheduler;
import rpg.persistence.jdbc.BatchWriter;

/**
 * Reads and writes {@code rpg.character_progress} (FR-054, FR-056).
 *
 * <p>Built exactly like {@code JdbcCharacterResourcesRepository} in B04, including the live source:
 * while a session lasts the in-memory state is authoritative (Principle IV), so the flush asks the
 * rules for the current value instead of keeping a second copy that could disagree.
 */
public final class JdbcCharacterProgressRepository
        implements CharacterProgressRepository, BatchWriter {

    private static final String SELECT_ONE =
            "SELECT character_id, level, xp_in_level, data_version, revision"
                    + " FROM rpg.character_progress WHERE character_id = ?";

    private static final String UPSERT =
            "INSERT INTO rpg.character_progress"
                    + " (character_id, level, xp_in_level, data_version, revision, updated_at)"
                    + " VALUES (?, ?, ?, ?, ?, ?)"
                    + " ON CONFLICT (character_id) DO UPDATE SET"
                    + "   level = excluded.level,"
                    + "   xp_in_level = excluded.xp_in_level,"
                    + "   data_version = excluded.data_version,"
                    + "   revision = rpg.character_progress.revision + 1,"
                    + "   updated_at = excluded.updated_at";

    private final DataSource readPool;
    private final Scheduler scheduler;
    private final WriteBehindCoordinator coordinator;
    private final Clock clock;
    private final Map<UUID, Long> revisions = new ConcurrentHashMap<>();

    private volatile Function<UUID, Optional<ProgressState>> liveSource = id -> Optional.empty();

    public JdbcCharacterProgressRepository(
            DataSource readPool,
            Scheduler scheduler,
            WriteBehindCoordinator coordinator,
            Clock clock) {
        this.readPool = Objects.requireNonNull(readPool, "readPool");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Where the flush reads the current value from while the character is online. */
    public void setLiveSource(Function<UUID, Optional<ProgressState>> liveSource) {
        this.liveSource = Objects.requireNonNull(liveSource, "liveSource");
    }

    @Override
    public CompletableFuture<Optional<CharacterProgress>> find(UUID characterId) {
        Objects.requireNonNull(characterId, "characterId");
        CompletableFuture<Optional<CharacterProgress>> future = new CompletableFuture<>();
        scheduler.runAsync(
                () -> {
                    try (Connection connection = readPool.getConnection();
                            PreparedStatement statement = connection.prepareStatement(SELECT_ONE)) {
                        statement.setObject(1, characterId);
                        try (ResultSet rows = statement.executeQuery()) {
                            future.complete(rows.next() ? Optional.of(map(rows)) : Optional.empty());
                        }
                    } catch (SQLException failure) {
                        future.completeExceptionally(
                                new PersistenceException(
                                        "could not load progress of character " + characterId,
                                        failure));
                    }
                });
        return future;
    }

    @Override
    public void markDirty(UUID characterId) {
        Objects.requireNonNull(characterId, "characterId");
        coordinator.markDirty(AggregateType.CHARACTER_PROGRESS, characterId.toString());
    }

    /** Synchronous read on a caller-provided connection, for the login bundle. */
    public static Optional<CharacterProgress> read(Connection connection, UUID characterId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_ONE)) {
            statement.setObject(1, characterId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(mapRow(rows)) : Optional.empty();
            }
        }
    }

    @Override
    public List<DirtyMark> write(DataSource dataSource, List<DirtyMark> marks) {
        List<DirtyMark> persisted = new ArrayList<>();
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(UPSERT)) {
                for (DirtyMark mark : marks) {
                    UUID characterId = UUID.fromString(mark.aggregateId());
                    Optional<ProgressState> state = liveSource.apply(characterId);
                    if (state.isEmpty()) {
                        // Released between the mark and this flush - a logout, most likely. The
                        // unload path writes the final value itself, so there is nothing to salvage
                        // here and nothing to retry.
                        persisted.add(mark);
                        continue;
                    }
                    statement.setObject(1, characterId);
                    statement.setInt(2, state.get().level());
                    statement.setLong(3, state.get().xpInLevel());
                    statement.setInt(4, CharacterProgress.CURRENT_DATA_VERSION);
                    statement.setLong(5, revisions.getOrDefault(characterId, 0L) + 1);
                    statement.setTimestamp(6, Timestamp.from(clock.instant()));
                    statement.addBatch();
                    revisions.merge(characterId, 1L, Long::sum);
                    persisted.add(mark);
                }
                statement.executeBatch();
                connection.commit();
            } catch (SQLException failure) {
                connection.rollback();
                throw failure;
            }
        } catch (SQLException failure) {
            throw new PersistenceException("character progress batch failed", failure);
        }
        return persisted;
    }

    private CharacterProgress map(ResultSet rows) throws SQLException {
        CharacterProgress progress = mapRow(rows);
        revisions.put(progress.characterId(), progress.revision());
        return progress;
    }

    private static CharacterProgress mapRow(ResultSet rows) throws SQLException {
        return new CharacterProgress(
                rows.getObject("character_id", UUID.class),
                rows.getInt("level"),
                rows.getLong("xp_in_level"),
                rows.getInt("data_version"),
                rows.getLong("revision"));
    }
}
