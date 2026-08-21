package rpg.persistence.inventory;

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

import rpg.core.inventory.CharacterInventory;
import rpg.core.inventory.CharacterInventoryRepository;
import rpg.core.persistence.AggregateType;
import rpg.core.persistence.DirtyMark;
import rpg.core.persistence.PersistenceException;
import rpg.core.persistence.WriteBehindCoordinator;
import rpg.core.scheduler.Scheduler;
import rpg.persistence.jdbc.BatchWriter;

/**
 * Registration 3 of 3 for {@link AggregateType#CHARACTER_INVENTORY} (ADR-015).
 *
 * <p>Structured like {@code JdbcClassProgressRepository}, live source included: the buffer holds a
 * mark, not a value, so the contents are fetched at flush time from wherever the session keeps them.
 *
 * <p>One difference worth naming: the live source here cannot read the player's inventory itself. That
 * is a Bukkit call and the flush runs off the tick. What it reads is the last snapshot the platform
 * captured <em>on</em> the tick - see {@code InventoryModule}.
 */
public final class JdbcCharacterInventoryRepository
        implements CharacterInventoryRepository, BatchWriter {

    private static final String SELECT_ONE =
            "SELECT character_id, contents, ender_chest, data_version, revision"
                    + " FROM rpg.character_inventory WHERE character_id = ?";

    private static final String UPSERT =
            "INSERT INTO rpg.character_inventory"
                    + " (character_id, contents, ender_chest, data_version, revision, updated_at)"
                    + " VALUES (?, ?, ?, ?, ?, ?)"
                    + " ON CONFLICT (character_id) DO UPDATE SET"
                    + "   contents = excluded.contents,"
                    + "   ender_chest = excluded.ender_chest,"
                    + "   data_version = excluded.data_version,"
                    + "   revision = rpg.character_inventory.revision + 1,"
                    + "   updated_at = excluded.updated_at";

    private final DataSource readPool;
    private final Scheduler scheduler;
    private final WriteBehindCoordinator coordinator;
    private final Clock clock;
    private final Map<UUID, Long> revisions = new ConcurrentHashMap<>();

    private volatile Function<UUID, Optional<CharacterInventory>> liveSource = id -> Optional.empty();

    public JdbcCharacterInventoryRepository(
            DataSource readPool,
            Scheduler scheduler,
            WriteBehindCoordinator coordinator,
            Clock clock) {
        this.readPool = Objects.requireNonNull(readPool, "readPool");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Where the flush reads the current contents from - the last snapshot taken on the tick. */
    public void setLiveSource(Function<UUID, Optional<CharacterInventory>> liveSource) {
        this.liveSource = Objects.requireNonNull(liveSource, "liveSource");
    }

    @Override
    public CompletableFuture<Optional<CharacterInventory>> find(UUID characterId) {
        Objects.requireNonNull(characterId, "characterId");
        CompletableFuture<Optional<CharacterInventory>> future = new CompletableFuture<>();
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
                                        "could not load the inventory of character " + characterId,
                                        failure));
                    }
                });
        return future;
    }

    @Override
    public void markDirty(UUID characterId) {
        Objects.requireNonNull(characterId, "characterId");
        coordinator.markDirty(AggregateType.CHARACTER_INVENTORY, characterId.toString());
    }

    /** Reads on an existing connection, for the session load that batches its queries. */
    public static Optional<CharacterInventory> read(Connection connection, UUID characterId)
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
                    Optional<CharacterInventory> inventory = liveSource.apply(characterId);
                    if (inventory.isEmpty()) {
                        // No snapshot to write. The character was released between the mark and this
                        // flush; its final contents were captured and marked on the way out.
                        persisted.add(mark);
                        continue;
                    }
                    statement.setObject(1, characterId);
                    statement.setBytes(2, inventory.get().contents());
                    statement.setBytes(3, inventory.get().enderChest());
                    statement.setInt(4, CharacterInventory.CURRENT_DATA_VERSION);
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
            throw new PersistenceException("character inventory batch failed", failure);
        }
        return persisted;
    }

    private CharacterInventory map(ResultSet rows) throws SQLException {
        CharacterInventory inventory = mapRow(rows);
        revisions.put(inventory.characterId(), inventory.revision());
        return inventory;
    }

    private static CharacterInventory mapRow(ResultSet rows) throws SQLException {
        return new CharacterInventory(
                rows.getObject("character_id", UUID.class),
                bytesOrEmpty(rows, "contents"),
                bytesOrEmpty(rows, "ender_chest"),
                rows.getInt("data_version"),
                rows.getLong("revision"));
    }

    /** The columns are NOT NULL, but a null here would be an NPE two frames later rather than empty. */
    private static byte[] bytesOrEmpty(ResultSet rows, String column) throws SQLException {
        byte[] value = rows.getBytes(column);
        return value == null ? new byte[0] : value;
    }
}
