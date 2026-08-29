package rpg.persistence.jdbc;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import javax.sql.DataSource;

import rpg.core.persistence.AggregateType;
import rpg.core.persistence.DirtyMark;
import rpg.core.persistence.PersistenceException;
import rpg.core.persistence.StatisticsRepository;
import rpg.core.persistence.WriteBehindCoordinator;
import rpg.core.scheduler.Scheduler;

/**
 * Daily statistics on top of plain JDBC.
 *
 * <p>The whole design hangs off one statement:
 *
 * <pre>INSERT ... ON CONFLICT (player_id, metric, day) DO UPDATE SET value = value + excluded.value</pre>
 *
 * <p>Because the update <em>adds</em>, the pending delta can be written without ever reading the
 * stored value first (FR-007). And because deltas accumulate in memory between flushes, a thousand
 * kills cost one row write rather than a thousand (FR-002, SC-005).
 *
 * <p>The day rollover needs no special handling: an event after midnight simply lands on a
 * different {@code day} and therefore a different key. There is no switching moment that could
 * lose or double a count (FR-016c).
 */
public final class JdbcStatisticsRepository implements StatisticsRepository, BatchWriter {

    private static final String UPSERT =
            "INSERT INTO rpg.player_statistic_daily (player_id, metric, day, value)"
                    + " VALUES (?, ?, ?, ?)"
                    + " ON CONFLICT (player_id, metric, day)"
                    + " DO UPDATE SET value = rpg.player_statistic_daily.value + excluded.value";

    private static final String SUM_RANGE =
            "SELECT COALESCE(SUM(value), 0) FROM rpg.player_statistic_daily"
                    + " WHERE player_id = ? AND metric = ? AND day BETWEEN ? AND ?";

    private static final String SUM_ALL =
            "SELECT COALESCE(SUM(value), 0) FROM rpg.player_statistic_daily"
                    + " WHERE player_id = ? AND metric = ?";

    /**
     * The second write path (B12, ADR-040): the stored value becomes the larger of the two.
     *
     * <p>Same table, same row, same "never read first" - only the operator differs. Which one
     * applies is decided by the metric's kind in B12's registry, never by the call site.
     */
    private static final String UPSERT_MAX =
            "INSERT INTO rpg.player_statistic_daily (player_id, metric, day, value)"
                    + " VALUES (?, ?, ?, ?)"
                    + " ON CONFLICT (player_id, metric, day)"
                    + " DO UPDATE SET value ="
                    + " GREATEST(rpg.player_statistic_daily.value, excluded.value)";

    /** Deltas not yet written, keyed by player, metric and day. */
    private final Map<Key, AtomicLong> pending = new ConcurrentHashMap<>();

    /**
     * Running maxima not yet written.
     *
     * <p>A second map rather than a flag on the first: an entry here means something different -
     * it is not added to the stored value but compared against it, and a failed write has to be
     * given back with {@code max} instead of {@code +}. One map carrying two meanings would make
     * the distinction a property of the caller again, which is exactly what ADR-040 moved away
     * from.
     */
    private final Map<Key, AtomicLong> pendingMaxima = new ConcurrentHashMap<>();

    private final DataSource readPool;
    private final Scheduler scheduler;
    private final WriteBehindCoordinator coordinator;
    private final Clock clock;

    public JdbcStatisticsRepository(
            DataSource readPool,
            Scheduler scheduler,
            WriteBehindCoordinator coordinator,
            Clock clock) {
        this.readPool = Objects.requireNonNull(readPool, "readPool");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public void increment(UUID playerId, String metric, long delta) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(metric, "metric");
        if (delta == 0) {
            return;
        }
        Key key = new Key(playerId, metric, LocalDate.now(clock.withZone(ZoneOffset.UTC)));
        pending.computeIfAbsent(key, ignored -> new AtomicLong()).addAndGet(delta);
        coordinator.markDirty(AggregateType.STATISTICS, key.asAggregateId());
    }

    @Override
    public void reportMax(UUID playerId, String metric, long value) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(metric, "metric");
        Key key = new Key(playerId, metric, LocalDate.now(clock.withZone(ZoneOffset.UTC)));
        // accumulateAndGet with max, not addAndGet: between two flushes the pending entry is the
        // largest hit so far, not their sum. A thousand hits still cost one row write.
        pendingMaxima
                .computeIfAbsent(key, ignored -> new AtomicLong(Long.MIN_VALUE))
                .accumulateAndGet(value, Math::max);
        coordinator.markDirty(AggregateType.STATISTICS, key.asAggregateId());
    }

    @Override
    public CompletableFuture<Long> sum(UUID playerId, String metric, LocalDate from, LocalDate to) {
        return queryAsync(
                SUM_RANGE,
                statement -> {
                    statement.setObject(1, playerId);
                    statement.setString(2, metric);
                    statement.setDate(3, Date.valueOf(from));
                    statement.setDate(4, Date.valueOf(to));
                });
    }

    @Override
    public CompletableFuture<Long> total(UUID playerId, String metric) {
        return queryAsync(
                SUM_ALL,
                statement -> {
                    statement.setObject(1, playerId);
                    statement.setString(2, metric);
                });
    }

    // --- BatchWriter ---

    @Override
    public List<DirtyMark> write(DataSource dataSource, List<DirtyMark> marks) {
        List<DirtyMark> persisted = new ArrayList<>();
        List<Key> takenKeys = new ArrayList<>();
        List<Long> takenValues = new ArrayList<>();

        List<Key> takenMaxKeys = new ArrayList<>();
        List<Long> takenMaxValues = new ArrayList<>();

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement sums = connection.prepareStatement(UPSERT);
                    PreparedStatement maxima = connection.prepareStatement(UPSERT_MAX)) {
                for (DirtyMark mark : marks) {
                    Key key = Key.parse(mark.aggregateId());

                    // A key belongs to exactly one of the two maps - the metric's kind decides,
                    // and it never changes. Both are consulted anyway: relying on that invariant
                    // here would turn a registry mistake into a silently dropped count.
                    AtomicLong counter = pending.get(key);
                    if (counter != null) {
                        // Take the delta out now. If the write fails it is added back, so a
                        // concurrent increment during the write is preserved either way.
                        long delta = counter.getAndSet(0L);
                        if (delta != 0) {
                            takenKeys.add(key);
                            takenValues.add(delta);
                            bind(sums, key, delta);
                            sums.addBatch();
                        }
                    }

                    AtomicLong highest = pendingMaxima.get(key);
                    if (highest != null) {
                        long value = highest.getAndSet(Long.MIN_VALUE);
                        if (value != Long.MIN_VALUE) {
                            takenMaxKeys.add(key);
                            takenMaxValues.add(value);
                            bind(maxima, key, value);
                            maxima.addBatch();
                        }
                    }

                    persisted.add(mark);
                }
                sums.executeBatch();
                maxima.executeBatch();
                connection.commit();
                takenKeys.forEach(pending::remove);
                takenMaxKeys.forEach(pendingMaxima::remove);
            } catch (SQLException failure) {
                connection.rollback();
                giveBack(takenKeys, takenValues);
                giveBackMaxima(takenMaxKeys, takenMaxValues);
                throw failure;
            }
        } catch (SQLException failure) {
            throw new PersistenceException("statistics batch failed", failure);
        }
        return persisted;
    }

    /** Returns deltas to the pending map after a failed write - nothing is dropped (FR-009). */
    private void giveBack(List<Key> keys, List<Long> values) {
        for (int i = 0; i < keys.size(); i++) {
            pending.computeIfAbsent(keys.get(i), ignored -> new AtomicLong()).addAndGet(values.get(i));
        }
    }

    /**
     * The same for maxima - but given back with {@code max}, not {@code +}.
     *
     * <p>Adding them would turn a retried write into a sum of hits, and the "biggest hit" would
     * grow every time the database hiccuped.
     */
    private void giveBackMaxima(List<Key> keys, List<Long> values) {
        for (int i = 0; i < keys.size(); i++) {
            pendingMaxima
                    .computeIfAbsent(keys.get(i), ignored -> new AtomicLong(Long.MIN_VALUE))
                    .accumulateAndGet(values.get(i), Math::max);
        }
    }

    private static void bind(PreparedStatement statement, Key key, long value) throws SQLException {
        statement.setObject(1, key.playerId());
        statement.setString(2, key.metric());
        statement.setDate(3, Date.valueOf(key.day()));
        statement.setLong(4, value);
    }

    private CompletableFuture<Long> queryAsync(String sql, StatementBinder binder) {
        CompletableFuture<Long> future = new CompletableFuture<>();
        scheduler.runAsync(
                () -> {
                    try (Connection connection = readPool.getConnection();
                            PreparedStatement statement = connection.prepareStatement(sql)) {
                        binder.bind(statement);
                        try (ResultSet rows = statement.executeQuery()) {
                            future.complete(rows.next() ? rows.getLong(1) : 0L);
                        }
                    } catch (SQLException failure) {
                        future.completeExceptionally(
                                new PersistenceException("statistics query failed", failure));
                    }
                });
        return future;
    }

    @FunctionalInterface
    private interface StatementBinder {
        void bind(PreparedStatement statement) throws SQLException;
    }

    /** Composite key of one daily statistic. */
    record Key(UUID playerId, String metric, LocalDate day) {

        /** Encoded form used as the dirty-mark aggregate id. */
        String asAggregateId() {
            return playerId + "|" + metric + "|" + day;
        }

        static Key parse(String aggregateId) {
            String[] parts = aggregateId.split("\\|", 3);
            if (parts.length != 3) {
                throw new PersistenceException("malformed statistics key: " + aggregateId);
            }
            return new Key(UUID.fromString(parts[0]), parts[1], LocalDate.parse(parts[2]));
        }
    }
}
