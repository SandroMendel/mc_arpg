package rpg.persistence.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import javax.sql.DataSource;

import rpg.core.persistence.AggregateType;
import rpg.core.persistence.DirtyMark;
import rpg.core.persistence.ItemInstance;
import rpg.core.persistence.ItemInstanceRepository;
import rpg.core.persistence.PersistenceException;
import rpg.core.persistence.WriteBehindCoordinator;
import rpg.core.scheduler.Scheduler;

/**
 * Item instances on top of plain JDBC.
 *
 * <p>Stores template id and rolled values only - never a computed final value and never rendered
 * lore (ADR-004, Constitution IV). That single rule is what allows a balancing rework later to
 * change what a template means without migrating or touching a single existing player item.
 */
public final class JdbcItemInstanceRepository implements ItemInstanceRepository, BatchWriter {

    private static final String SELECT_ONE =
            "SELECT instance_id, owner_character_id, template_id, rolled_values::text, revision"
                    + " FROM rpg.item_instance WHERE instance_id = ?";

    private static final String SELECT_BY_OWNER =
            "SELECT instance_id, owner_character_id, template_id, rolled_values::text, revision"
                    + " FROM rpg.item_instance WHERE owner_character_id = ?";

    private static final String UPSERT =
            "INSERT INTO rpg.item_instance"
                    + " (instance_id, owner_character_id, template_id, rolled_values, revision, updated_at)"
                    + " VALUES (?, ?, ?, ?::jsonb, ?, now())"
                    + " ON CONFLICT (instance_id) DO UPDATE SET"
                    + "   owner_character_id = excluded.owner_character_id,"
                    + "   template_id = excluded.template_id,"
                    + "   rolled_values = excluded.rolled_values,"
                    + "   revision = excluded.revision,"
                    + "   updated_at = now()";

    private final DataSource readPool;
    private final Scheduler scheduler;
    private final WriteBehindCoordinator coordinator;
    private final Map<UUID, ItemInstance> cache = new ConcurrentHashMap<>();

    public JdbcItemInstanceRepository(
            DataSource readPool, Scheduler scheduler, WriteBehindCoordinator coordinator) {
        this.readPool = Objects.requireNonNull(readPool, "readPool");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
    }

    @Override
    public AggregateType aggregateType() {
        return AggregateType.ITEM_INSTANCE;
    }

    @Override
    public CompletableFuture<Optional<ItemInstance>> load(UUID instanceId) {
        CompletableFuture<Optional<ItemInstance>> future = new CompletableFuture<>();
        scheduler.runAsync(
                () -> {
                    try (Connection connection = readPool.getConnection();
                            PreparedStatement statement = connection.prepareStatement(SELECT_ONE)) {
                        statement.setObject(1, instanceId);
                        try (ResultSet rows = statement.executeQuery()) {
                            if (!rows.next()) {
                                future.complete(Optional.empty());
                                return;
                            }
                            ItemInstance instance = read(rows);
                            cache.put(instance.instanceId(), instance);
                            future.complete(Optional.of(instance));
                        }
                    } catch (SQLException failure) {
                        future.completeExceptionally(
                                new PersistenceException("could not load item " + instanceId, failure));
                    }
                });
        return future;
    }

    @Override
    public CompletableFuture<List<ItemInstance>> loadByOwner(UUID ownerCharacterId) {
        CompletableFuture<List<ItemInstance>> future = new CompletableFuture<>();
        scheduler.runAsync(
                () -> {
                    List<ItemInstance> items = new ArrayList<>();
                    try (Connection connection = readPool.getConnection();
                            PreparedStatement statement =
                                    connection.prepareStatement(SELECT_BY_OWNER)) {
                        statement.setObject(1, ownerCharacterId);
                        try (ResultSet rows = statement.executeQuery()) {
                            while (rows.next()) {
                                ItemInstance instance = read(rows);
                                cache.put(instance.instanceId(), instance);
                                items.add(instance);
                            }
                        }
                        future.complete(List.copyOf(items));
                    } catch (SQLException failure) {
                        future.completeExceptionally(
                                new PersistenceException(
                                        "could not load items of " + ownerCharacterId, failure));
                    }
                });
        return future;
    }

    @Override
    public void create(ItemInstance instance) {
        cache.put(instance.instanceId(), instance);
        markDirty(instance.instanceId());
    }

    @Override
    public void markDirty(UUID instanceId) {
        coordinator.markDirty(AggregateType.ITEM_INSTANCE, instanceId.toString());
    }

    @Override
    public List<DirtyMark> write(DataSource dataSource, List<DirtyMark> marks) {
        List<DirtyMark> persisted = new ArrayList<>();
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(UPSERT)) {
                for (DirtyMark mark : marks) {
                    ItemInstance instance = cache.get(UUID.fromString(mark.aggregateId()));
                    if (instance == null) {
                        persisted.add(mark);
                        continue;
                    }
                    statement.setObject(1, instance.instanceId());
                    statement.setObject(2, instance.ownerCharacterId());
                    statement.setString(3, instance.templateId());
                    statement.setString(4, JsonValues.toJson(instance.rolledValues()));
                    statement.setLong(5, instance.revision() + 1);
                    statement.addBatch();
                    persisted.add(mark);
                }
                statement.executeBatch();
                connection.commit();
            } catch (SQLException failure) {
                connection.rollback();
                throw failure;
            }
        } catch (SQLException failure) {
            throw new PersistenceException("item instance batch failed", failure);
        }
        return persisted;
    }

    private static ItemInstance read(ResultSet rows) throws SQLException {
        return new ItemInstance(
                rows.getObject("instance_id", UUID.class),
                rows.getObject("owner_character_id", UUID.class),
                rows.getString("template_id"),
                JsonValues.fromJson(rows.getString("rolled_values")),
                rows.getLong("revision"));
    }
}
