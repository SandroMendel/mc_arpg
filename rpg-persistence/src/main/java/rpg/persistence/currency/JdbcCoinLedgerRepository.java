package rpg.persistence.currency;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.sql.DataSource;

import rpg.core.currency.BookingReason;
import rpg.core.currency.CoinLedger;
import rpg.core.currency.LedgerEntry;
import rpg.core.currency.LedgerWriter;
import rpg.core.persistence.AggregateType;
import rpg.core.persistence.DirtyMark;
import rpg.core.persistence.PersistenceException;
import rpg.core.persistence.WriteBehindCoordinator;
import rpg.core.scheduler.Scheduler;
import rpg.persistence.jdbc.BatchWriter;

/**
 * Reads and writes {@code rpg.coin_ledger} (FR-034, FR-037, FR-038).
 *
 * <p><b>The append path follows {@code JdbcAuditLogRepository}, not the character aggregates.</b>
 * A dirty mark per entry would be wrong twice over: an entry is never updated, so there is nothing
 * to re-read, and at 800 mobs it would put one mark per kill through the buffer. Instead the whole
 * queue hangs behind <b>one</b> synthetic id - marked once per flush cycle, drained in one batch.
 *
 * <p><b>Retention runs with the flush, not as a task of its own</b> (Constitution II). Rows an
 * operator caused are never pruned - they are the ones somebody asks about a year later, and the
 * {@code actor IS NULL} predicate that spares them is the same one the partial index is built on.
 */
public final class JdbcCoinLedgerRepository implements CoinLedger, LedgerWriter, BatchWriter {

    private static final String INSERT =
            "INSERT INTO rpg.coin_ledger"
                    + " (character_id, occurred_at, amount, direction, reason,"
                    + "  balance_before, balance_after, actor)"
                    + " VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String SELECT_PAGE =
            "SELECT id, character_id, occurred_at, amount, direction, reason,"
                    + " balance_before, balance_after, actor"
                    + " FROM rpg.coin_ledger WHERE character_id = ?"
                    + " ORDER BY occurred_at DESC, id DESC"
                    + " LIMIT ? OFFSET ?";

    private static final String SELECT_RANGE =
            "SELECT id, character_id, occurred_at, amount, direction, reason,"
                    + " balance_before, balance_after, actor"
                    + " FROM rpg.coin_ledger"
                    + " WHERE character_id = ? AND occurred_at BETWEEN ? AND ?"
                    + " ORDER BY occurred_at DESC, id DESC"
                    + " LIMIT ?";

    private static final String COUNT =
            "SELECT count(*) FROM rpg.coin_ledger WHERE character_id = ?";

    private static final String PRUNE =
            "DELETE FROM rpg.coin_ledger WHERE actor IS NULL AND occurred_at < ?";

    /** A single aggregate id: the whole queue is written as one batch. */
    private static final String QUEUE_ID = "coin-ledger";

    private final ConcurrentLinkedQueue<LedgerEntry> queued = new ConcurrentLinkedQueue<>();
    private final DataSource readPool;
    private final Scheduler scheduler;
    private final WriteBehindCoordinator coordinator;
    private final Duration retention;
    private final Clock clock;

    private volatile Instant lastPruneAt = Instant.EPOCH;

    public JdbcCoinLedgerRepository(
            DataSource readPool,
            Scheduler scheduler,
            WriteBehindCoordinator coordinator,
            Duration retention,
            Clock clock) {
        this.readPool = Objects.requireNonNull(readPool, "readPool");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
        this.retention = Objects.requireNonNull(retention, "retention");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    // --- writing -----------------------------------------------------------------------------

    @Override
    public void append(LedgerEntry entry) {
        queued.add(Objects.requireNonNull(entry, "entry"));
        coordinator.markDirty(AggregateType.COIN_LEDGER, QUEUE_ID);
    }

    @Override
    public List<DirtyMark> write(DataSource dataSource, List<DirtyMark> marks) {
        if (queued.isEmpty()) {
            // Every mark for this type points at the same queue, so an empty queue means they are
            // all already satisfied - by an earlier flush that drained more than its own marks.
            return marks;
        }
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(INSERT)) {
                LedgerEntry entry;
                int batched = 0;
                while ((entry = queued.poll()) != null) {
                    statement.setObject(1, entry.characterId());
                    statement.setTimestamp(2, Timestamp.from(entry.occurredAt()));
                    statement.setLong(3, entry.amount());
                    statement.setString(4, entry.direction().name());
                    statement.setString(5, entry.reason().name());
                    statement.setLong(6, entry.balanceBefore());
                    statement.setLong(7, entry.balanceAfter());
                    if (entry.actor().isPresent()) {
                        statement.setString(8, entry.actor().get());
                    } else {
                        statement.setNull(8, Types.VARCHAR);
                    }
                    statement.addBatch();
                    batched++;
                }
                if (batched > 0) {
                    statement.executeBatch();
                }
            }
            pruneIfDue(connection);
            connection.commit();
        } catch (SQLException failure) {
            throw new PersistenceException("coin ledger batch failed", failure);
        }
        return marks;
    }

    /**
     * Deletes bookings from play that are past the retention window.
     *
     * <p>At most once per retention window rather than on every flush: this is housekeeping on the
     * largest table in the project, and running it every few seconds would be a scan nobody asked
     * for. Rows with an actor are never touched (FR-038).
     */
    private void pruneIfDue(Connection connection) throws SQLException {
        Instant now = clock.instant();
        if (now.isBefore(lastPruneAt.plus(retention))) {
            return;
        }
        lastPruneAt = now;
        try (PreparedStatement statement = connection.prepareStatement(PRUNE)) {
            statement.setTimestamp(1, Timestamp.from(now.minus(retention)));
            statement.executeUpdate();
        }
    }

    /**
     * Runs the retention sweep now, whatever the schedule says. For tests and for B14.
     *
     * <p><b>Commits explicitly.</b> The pool hands out connections with auto-commit off, so a
     * statement that is merely executed and then returned to the pool is rolled back - it reports
     * the rows it would have deleted and deletes none. That failure is invisible from the return
     * value, which is exactly how it got written the first time.
     */
    public int pruneNow(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(PRUNE)) {
                statement.setTimestamp(1, Timestamp.from(clock.instant().minus(retention)));
                int deleted = statement.executeUpdate();
                connection.commit();
                return deleted;
            } catch (SQLException failure) {
                connection.rollback();
                throw failure;
            }
        } catch (SQLException failure) {
            throw new PersistenceException("coin ledger retention sweep failed", failure);
        }
    }

    // --- reading -----------------------------------------------------------------------------

    @Override
    public CompletableFuture<List<LedgerEntry>> historyOf(UUID characterId, int offset, int limit) {
        Objects.requireNonNull(characterId, "characterId");
        requirePositive(limit);
        if (offset < 0) {
            throw new IllegalArgumentException("offset must not be negative, but was " + offset);
        }
        return async(
                () -> {
                    try (Connection connection = readPool.getConnection();
                            PreparedStatement statement =
                                    connection.prepareStatement(SELECT_PAGE)) {
                        statement.setObject(1, characterId);
                        statement.setInt(2, limit);
                        statement.setInt(3, offset);
                        return readAll(statement);
                    }
                },
                "could not read the ledger page of character " + characterId);
    }

    @Override
    public CompletableFuture<Long> historyCount(UUID characterId) {
        Objects.requireNonNull(characterId, "characterId");
        CompletableFuture<Long> future = new CompletableFuture<>();
        scheduler.runAsync(
                () -> {
                    try (Connection connection = readPool.getConnection();
                            PreparedStatement statement = connection.prepareStatement(COUNT)) {
                        statement.setObject(1, characterId);
                        try (ResultSet rows = statement.executeQuery()) {
                            rows.next();
                            future.complete(rows.getLong(1));
                        }
                    } catch (SQLException failure) {
                        future.completeExceptionally(
                                new PersistenceException(
                                        "could not count ledger entries of character "
                                                + characterId,
                                        failure));
                    }
                });
        return future;
    }

    @Override
    public CompletableFuture<List<LedgerEntry>> historyOf(
            UUID characterId, Instant from, Instant to, int limit) {
        Objects.requireNonNull(characterId, "characterId");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        requirePositive(limit);
        return async(
                () -> {
                    try (Connection connection = readPool.getConnection();
                            PreparedStatement statement =
                                    connection.prepareStatement(SELECT_RANGE)) {
                        statement.setObject(1, characterId);
                        statement.setTimestamp(2, Timestamp.from(from));
                        statement.setTimestamp(3, Timestamp.from(to));
                        statement.setInt(4, limit);
                        return readAll(statement);
                    }
                },
                "could not read the ledger of character " + characterId);
    }

    /** Synchronous read of one page, for a caller that already holds a connection. */
    public static List<LedgerEntry> readPage(
            Connection connection, UUID characterId, int offset, int limit) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_PAGE)) {
            statement.setObject(1, characterId);
            statement.setInt(2, limit);
            statement.setInt(3, offset);
            return readAll(statement);
        }
    }

    private static void requirePositive(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException(
                    "limit must be positive, but was "
                            + limit
                            + " - there is no unbounded read of this table");
        }
    }

    private CompletableFuture<List<LedgerEntry>> async(SqlRead read, String failureMessage) {
        CompletableFuture<List<LedgerEntry>> future = new CompletableFuture<>();
        scheduler.runAsync(
                () -> {
                    try {
                        future.complete(read.run());
                    } catch (SQLException failure) {
                        future.completeExceptionally(
                                new PersistenceException(failureMessage, failure));
                    }
                });
        return future;
    }

    @FunctionalInterface
    private interface SqlRead {
        List<LedgerEntry> run() throws SQLException;
    }

    private static List<LedgerEntry> readAll(PreparedStatement statement) throws SQLException {
        List<LedgerEntry> entries = new ArrayList<>();
        try (ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                entries.add(map(rows));
            }
        }
        return List.copyOf(entries);
    }

    private static LedgerEntry map(ResultSet rows) throws SQLException {
        return new LedgerEntry(
                rows.getLong("id"),
                rows.getObject("character_id", UUID.class),
                rows.getTimestamp("occurred_at").toInstant(),
                rows.getLong("amount"),
                LedgerEntry.Direction.valueOf(rows.getString("direction")),
                BookingReason.valueOf(rows.getString("reason")),
                rows.getLong("balance_before"),
                rows.getLong("balance_after"),
                Optional.ofNullable(rows.getString("actor")));
    }
}
