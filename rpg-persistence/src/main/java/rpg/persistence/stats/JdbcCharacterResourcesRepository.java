package rpg.persistence.stats;

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
import rpg.core.scheduler.Scheduler;
import rpg.core.stats.CharacterResources;
import rpg.core.stats.CharacterResourcesRepository;
import rpg.core.stats.ResourcePool;
import rpg.persistence.jdbc.BatchWriter;

/**
 * Reads and writes {@code rpg.character_stats} (FR-028).
 *
 * <p>Same shape as {@code JdbcCharacterRepository}: reads go through the scheduler's async path,
 * writes only ever happen inside B02's flush cycle. Nothing here is called from a game event, which
 * is what makes SC-012 - no database round trip per event - a structural property rather than a
 * discipline.
 *
 * <p>The current values are not held in a cache of their own. They live in the engine, which is the
 * authority while a player is online (Principle IV); this repository asks for them through
 * {@link #setLiveSource} when the flush needs them. Keeping a second copy here would mean two places
 * that can disagree about a player's health.
 */
public final class JdbcCharacterResourcesRepository
        implements CharacterResourcesRepository, BatchWriter {

    private static final String SELECT_ONE =
            "SELECT character_id, current_health, current_mana, data_version, revision"
                    + " FROM rpg.character_stats WHERE character_id = ?";

    private static final String UPSERT =
            "INSERT INTO rpg.character_stats"
                    + " (character_id, current_health, current_mana, data_version, revision,"
                    + "  updated_at)"
                    + " VALUES (?, ?, ?, ?, ?, ?)"
                    + " ON CONFLICT (character_id) DO UPDATE SET"
                    + "   current_health = excluded.current_health,"
                    + "   current_mana = excluded.current_mana,"
                    + "   data_version = excluded.data_version,"
                    + "   revision = rpg.character_stats.revision + 1,"
                    + "   updated_at = excluded.updated_at";

    private final DataSource readPool;
    private final Scheduler scheduler;
    private final WriteBehindCoordinator coordinator;
    private final Clock clock;

    /** Revision last read or written, per character - so an update carries a sensible value. */
    private final Map<UUID, Long> revisions = new ConcurrentHashMap<>();

    /** Where the current values come from at flush time; installed by {@link StatsModule}. */
    private volatile Function<UUID, Optional<ResourcePool>> liveSource = id -> Optional.empty();

    public JdbcCharacterResourcesRepository(
            DataSource readPool,
            Scheduler scheduler,
            WriteBehindCoordinator coordinator,
            Clock clock) {
        this.readPool = Objects.requireNonNull(readPool, "readPool");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Installs the lookup the flush uses to obtain current values.
     *
     * <p>Keyed by character id. The engine holds the truth while a player is online; this is how
     * the write path asks for it instead of keeping a second copy.
     */
    public void setLiveSource(Function<UUID, Optional<ResourcePool>> liveSource) {
        this.liveSource = Objects.requireNonNull(liveSource, "liveSource");
    }

    @Override
    public CompletableFuture<Optional<CharacterResources>> find(UUID characterId) {
        Objects.requireNonNull(characterId, "characterId");
        CompletableFuture<Optional<CharacterResources>> future = new CompletableFuture<>();
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
                                        "could not load resources of character " + characterId,
                                        failure));
                    }
                });
        return future;
    }

    @Override
    public void markDirty(UUID characterId) {
        Objects.requireNonNull(characterId, "characterId");
        coordinator.markDirty(AggregateType.CHARACTER_STATS, characterId.toString());
    }

    /** Reads one record on an existing connection - used by the bundled session load (FR-005). */
    public static Optional<CharacterResources> read(Connection connection, UUID characterId)
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
                    Optional<ResourcePool> pool = liveSource.apply(characterId);
                    if (pool.isEmpty()) {
                        // The holder went away between the mark and this flush - a logout, most
                        // likely. The unload path writes the final values itself, so there is
                        // nothing to salvage here and nothing to retry.
                        persisted.add(mark);
                        continue;
                    }
                    statement.setObject(1, characterId);
                    statement.setDouble(2, pool.get().currentHealth());
                    statement.setDouble(3, pool.get().currentMana());
                    statement.setInt(4, CharacterResources.CURRENT_DATA_VERSION);
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
            throw new PersistenceException("character stats batch failed", failure);
        }
        return persisted;
    }

    private CharacterResources map(ResultSet rows) throws SQLException {
        CharacterResources resources = mapRow(rows);
        revisions.put(resources.characterId(), resources.revision());
        return resources;
    }

    private static CharacterResources mapRow(ResultSet rows) throws SQLException {
        return new CharacterResources(
                rows.getObject("character_id", UUID.class),
                rows.getDouble("current_health"),
                rows.getDouble("current_mana"),
                rows.getInt("data_version"),
                rows.getLong("revision"));
    }
}
