package rpg.persistence.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.sql.DataSource;

import rpg.core.persistence.AggregateType;
import rpg.core.persistence.AuditEntry;
import rpg.core.persistence.AuditLogRepository;
import rpg.core.persistence.DirtyMark;
import rpg.core.persistence.PersistenceException;
import rpg.core.persistence.WriteBehindCoordinator;
import rpg.core.scheduler.Scheduler;

/**
 * Append-only audit log (FR-018).
 *
 * <p>Unlike the other aggregates this one does not coalesce: every action is its own row, because
 * "the last one wins" would defeat the point of an audit trail. Entries queue up between flushes
 * and are inserted in one batch.
 *
 * <p>There is no update and no delete anywhere in this class - that absence is the guarantee.
 */
public final class JdbcAuditLogRepository implements AuditLogRepository, BatchWriter {

    private static final String INSERT =
            "INSERT INTO rpg.audit_log (occurred_at, actor, action, target_player_id, details)"
                    + " VALUES (?, ?, ?, ?, ?::jsonb)";

    private static final String SELECT_RANGE =
            "SELECT occurred_at, actor, action, target_player_id, details::text"
                    + " FROM rpg.audit_log WHERE occurred_at BETWEEN ? AND ?"
                    + " ORDER BY occurred_at DESC";

    /** A single aggregate id: the whole queue is written as one batch. */
    private static final String QUEUE_ID = "audit-log";

    private final ConcurrentLinkedQueue<AuditEntry> queued = new ConcurrentLinkedQueue<>();
    private final DataSource readPool;
    private final Scheduler scheduler;
    private final WriteBehindCoordinator coordinator;

    public JdbcAuditLogRepository(
            DataSource readPool, Scheduler scheduler, WriteBehindCoordinator coordinator) {
        this.readPool = Objects.requireNonNull(readPool, "readPool");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
    }

    @Override
    public void append(AuditEntry entry) {
        queued.add(Objects.requireNonNull(entry, "entry"));
        coordinator.markDirty(AggregateType.AUDIT_LOG, QUEUE_ID);
    }

    @Override
    public CompletableFuture<List<AuditEntry>> between(Instant from, Instant to) {
        CompletableFuture<List<AuditEntry>> future = new CompletableFuture<>();
        scheduler.runAsync(
                () -> {
                    List<AuditEntry> entries = new ArrayList<>();
                    try (Connection connection = readPool.getConnection();
                            PreparedStatement statement = connection.prepareStatement(SELECT_RANGE)) {
                        statement.setTimestamp(1, Timestamp.from(from));
                        statement.setTimestamp(2, Timestamp.from(to));
                        try (ResultSet rows = statement.executeQuery()) {
                            while (rows.next()) {
                                entries.add(
                                        new AuditEntry(
                                                rows.getTimestamp("occurred_at").toInstant(),
                                                rows.getString("actor"),
                                                rows.getString("action"),
                                                Optional.ofNullable(
                                                        rows.getObject(
                                                                "target_player_id", UUID.class)),
                                                JsonValues.fromJson(rows.getString("details"))));
                            }
                        }
                        future.complete(List.copyOf(entries));
                    } catch (SQLException failure) {
                        future.completeExceptionally(
                                new PersistenceException("audit log query failed", failure));
                    }
                });
        return future;
    }

    @Override
    public List<DirtyMark> write(DataSource dataSource, List<DirtyMark> marks) {
        List<AuditEntry> taken = new ArrayList<>();
        AuditEntry entry;
        while ((entry = queued.poll()) != null) {
            taken.add(entry);
        }
        if (taken.isEmpty()) {
            return marks;
        }

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(INSERT)) {
                for (AuditEntry queuedEntry : taken) {
                    statement.setTimestamp(1, Timestamp.from(queuedEntry.occurredAt()));
                    statement.setString(2, queuedEntry.actor());
                    statement.setString(3, queuedEntry.action());
                    statement.setObject(4, queuedEntry.targetPlayerId().orElse(null));
                    statement.setString(5, JsonValues.toJson(queuedEntry.details()));
                    statement.addBatch();
                }
                statement.executeBatch();
                connection.commit();
            } catch (SQLException failure) {
                connection.rollback();
                // Put them back rather than lose them - an audit entry that vanished on a
                // transient failure would be indistinguishable from one that never happened.
                taken.forEach(queued::add);
                throw failure;
            }
        } catch (SQLException failure) {
            throw new PersistenceException("audit log batch failed", failure);
        }
        return marks;
    }
}
