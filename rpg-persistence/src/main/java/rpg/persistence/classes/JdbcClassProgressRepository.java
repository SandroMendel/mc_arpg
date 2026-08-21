package rpg.persistence.classes;

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

import rpg.core.classes.ClassProgress;
import rpg.core.classes.ClassProgressRepository;
import rpg.core.persistence.AggregateType;
import rpg.core.persistence.DirtyMark;
import rpg.core.persistence.PersistenceException;
import rpg.core.persistence.WriteBehindCoordinator;
import rpg.core.scheduler.Scheduler;
import rpg.persistence.jdbc.BatchWriter;

/**
 * Registration 3 of 3 for the new aggregate type (ADR-015).
 *
 * <p>Structured exactly like {@code JdbcCharacterProgressRepository} from B06, down to the live
 * source: the buffer holds a mark, not a value, so at flush time the current tier has to be fetched
 * from wherever the session keeps it. A value captured at mark time would be stale by the time the
 * batch runs, and dropping the row for a character who logged out in between is correct - the unload
 * path writes the final value itself.
 */
public final class JdbcClassProgressRepository implements ClassProgressRepository, BatchWriter {

    private static final String SELECT_ONE =
            "SELECT character_id, armor_tier, weapon_tier, data_version, revision"
                    + " FROM rpg.character_class_progress WHERE character_id = ?";

    private static final String UPSERT =
            "INSERT INTO rpg.character_class_progress"
                    + " (character_id, armor_tier, weapon_tier, data_version, revision, updated_at)"
                    + " VALUES (?, ?, ?, ?, ?, ?)"
                    + " ON CONFLICT (character_id) DO UPDATE SET"
                    + "   armor_tier = excluded.armor_tier,"
                    + "   weapon_tier = excluded.weapon_tier,"
                    + "   data_version = excluded.data_version,"
                    + "   revision = rpg.character_class_progress.revision + 1,"
                    + "   updated_at = excluded.updated_at";

    private final DataSource readPool;
    private final Scheduler scheduler;
    private final WriteBehindCoordinator coordinator;
    private final Clock clock;
    private final Map<UUID, Long> revisions = new ConcurrentHashMap<>();

    private volatile Function<UUID, Optional<ClassProgress>> liveSource = id -> Optional.empty();

    public JdbcClassProgressRepository(
            DataSource readPool,
            Scheduler scheduler,
            WriteBehindCoordinator coordinator,
            Clock clock) {
        this.readPool = Objects.requireNonNull(readPool, "readPool");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Where the flush reads the current tier from - the session cache, which is authoritative. */
    public void setLiveSource(Function<UUID, Optional<ClassProgress>> liveSource) {
        this.liveSource = Objects.requireNonNull(liveSource, "liveSource");
    }

    @Override
    public CompletableFuture<Optional<ClassProgress>> find(UUID characterId) {
        Objects.requireNonNull(characterId, "characterId");
        CompletableFuture<Optional<ClassProgress>> future = new CompletableFuture<>();
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
                                        "could not load class progress of character " + characterId,
                                        failure));
                    }
                });
        return future;
    }

    @Override
    public void markDirty(UUID characterId) {
        Objects.requireNonNull(characterId, "characterId");
        coordinator.markDirty(AggregateType.CHARACTER_CLASS_PROGRESS, characterId.toString());
    }

    /** Reads on an existing connection, for the session load that batches its queries. */
    public static Optional<ClassProgress> read(Connection connection, UUID characterId)
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
                    Optional<ClassProgress> progress = liveSource.apply(characterId);
                    if (progress.isEmpty()) {
                        // Released between the mark and this flush - a logout, most likely. The
                        // unload path writes the final value itself, so there is nothing to salvage
                        // here and nothing to retry.
                        persisted.add(mark);
                        continue;
                    }
                    statement.setObject(1, characterId);
                    statement.setInt(2, progress.get().armorTier());
                    statement.setInt(3, progress.get().weaponTier());
                    statement.setInt(4, ClassProgress.CURRENT_DATA_VERSION);
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
            throw new PersistenceException("class progress batch failed", failure);
        }
        return persisted;
    }

    private ClassProgress map(ResultSet rows) throws SQLException {
        ClassProgress progress = mapRow(rows);
        revisions.put(progress.characterId(), progress.revision());
        return progress;
    }

    private static ClassProgress mapRow(ResultSet rows) throws SQLException {
        return new ClassProgress(
                rows.getObject("character_id", UUID.class),
                rows.getInt("armor_tier"),
                rows.getInt("weapon_tier"),
                rows.getInt("data_version"),
                rows.getLong("revision"));
    }
}
