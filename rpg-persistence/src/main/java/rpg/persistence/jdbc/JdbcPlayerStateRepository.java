package rpg.persistence.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.sql.DataSource;

import rpg.core.persistence.AggregateType;
import rpg.core.persistence.AnonymizedId;
import rpg.core.persistence.PersistenceException;
import rpg.core.persistence.PlayerState;
import rpg.core.persistence.PlayerStateRepository;
import rpg.core.persistence.StaleVersionException;
import rpg.core.persistence.WriteBehindCoordinator;
import rpg.core.scheduler.Scheduler;

/**
 * Player state on top of plain JDBC.
 *
 * <p>Two things here are worth reading before changing anything.
 *
 * <p><strong>The write is conditional.</strong> The {@code UPDATE ... WHERE revision = ?} form is
 * what enforces FR-019b: a write based on a revision that no longer matches touches zero rows and
 * is reported instead of applied. That is the net under the session handover - without it, a ghost
 * session's late flush would roll a returning player back.
 *
 * <p><strong>A record that cannot be read is not substituted.</strong> {@link #load} completes
 * exceptionally, and the login path turns that into a rejection (FR-005a). Handing out a default
 * state would look like a graceful degradation and would in fact overwrite the player's real
 * progress at the next flush - the single worst outcome this block can produce.
 */
public final class JdbcPlayerStateRepository implements PlayerStateRepository, BatchWriter {

    private static final String SELECT =
            "SELECT player_id, data_version, revision, last_seen_at, anonymized"
                    + " FROM rpg.player_state WHERE player_id = ?";

    private static final String UPSERT =
            "INSERT INTO rpg.player_state"
                    + " (player_id, data_version, revision, last_seen_at, anonymized, updated_at)"
                    + " VALUES (?, ?, ?, ?, ?, now())"
                    + " ON CONFLICT (player_id) DO UPDATE SET"
                    + "   data_version = excluded.data_version,"
                    + "   revision = excluded.revision,"
                    + "   last_seen_at = excluded.last_seen_at,"
                    + "   anonymized = excluded.anonymized,"
                    + "   updated_at = now()"
                    + " WHERE rpg.player_state.revision = ?";

    /**
     * The first row of an account. {@code DO NOTHING} because two logins of the same account racing
     * here is a normal outcome, not a conflict to report.
     */
    private static final String INSERT_INITIAL =
            "INSERT INTO rpg.player_state"
                    + " (player_id, data_version, revision, last_seen_at, anonymized, updated_at)"
                    + " VALUES (?, ?, ?, ?, ?, now())"
                    + " ON CONFLICT (player_id) DO NOTHING";

    private static final String REPOINT_STATISTICS =
            "UPDATE rpg.player_statistic_daily SET player_id = ? WHERE player_id = ?";

    private static final String REPOINT_AUDIT_ACTOR =
            "UPDATE rpg.audit_log SET actor = ? WHERE actor = ?";

    private static final String CLEAR_AUDIT_TARGET =
            "UPDATE rpg.audit_log SET target_player_id = NULL WHERE target_player_id = ?";

    private static final String DELETE_ITEMS =
            // Items hang off the character since ADR-011, so the account reaches them through it.
            // Deleting the account would cascade there anyway; doing it explicitly keeps the
            // anonymisation readable as a list of what it removes, rather than as something the
            // reader has to reconstruct from foreign keys.
            "DELETE FROM rpg.item_instance WHERE owner_character_id IN"
                    + " (SELECT character_id FROM rpg.character WHERE player_id = ?)";

    private static final String DELETE_PLAYER =
            "DELETE FROM rpg.player_state WHERE player_id = ?";

    private static final String RECORD_ANONYMISATION =
            "INSERT INTO rpg.audit_log (occurred_at, actor, action, target_player_id, details)"
                    + " VALUES (?, ?, ?, NULL, '{}'::jsonb)";

    private final DataSource loginPool;
    private final Scheduler scheduler;
    private final WriteBehindCoordinator coordinator;
    private final Logger logger;
    private final Clock clock;

    /** In-memory authoritative state while a player is connected (Constitution IV). */
    private final Map<UUID, PlayerState> cache = new ConcurrentHashMap<>();

    public JdbcPlayerStateRepository(
            DataSource loginPool,
            Scheduler scheduler,
            WriteBehindCoordinator coordinator,
            Logger logger,
            Clock clock) {
        this.loginPool = Objects.requireNonNull(loginPool, "loginPool");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public AggregateType aggregateType() {
        return AggregateType.PLAYER_STATE;
    }

    @Override
    public CompletableFuture<Optional<PlayerState>> load(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        CompletableFuture<Optional<PlayerState>> future = new CompletableFuture<>();
        scheduler.runAsync(
                () -> {
                    try {
                        Optional<PlayerState> loaded = readFromDatabase(playerId);
                        loaded.ifPresent(state -> cache.put(playerId, state));
                        future.complete(loaded);
                    } catch (SQLException | RuntimeException failure) {
                        // Deliberately failed, never Optional.empty(): "unknown player" and
                        // "could not be read" must not look the same to the login path.
                        logger.log(
                                Level.SEVERE,
                                "[persistence] could not load state for " + playerId,
                                failure);
                        future.completeExceptionally(
                                new PersistenceException(
                                        "could not load player state for " + playerId, failure));
                    }
                });
        return future;
    }

    @Override
    public void markDirty(UUID playerId) {
        coordinator.markDirty(AggregateType.PLAYER_STATE, playerId.toString());
    }

    @Override
    public CompletableFuture<Void> awaitPendingWrites(UUID playerId, Duration timeout) {
        // Implemented by SessionHandover, which owns the waiting policy; the repository only knows
        // whether a mark is still outstanding.
        return SessionHandoverSupport.await(
                () -> coordinator.bufferStatus().pending() == 0, timeout, scheduler, clock);
    }

    @Override
    public CompletableFuture<Void> anonymize(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        CompletableFuture<Void> future = new CompletableFuture<>();
        scheduler.runAsync(
                () -> {
                    try {
                        anonymizeInOneTransaction(playerId);
                        cache.remove(playerId);
                        future.complete(null);
                    } catch (SQLException | RuntimeException failure) {
                        logger.log(
                                Level.SEVERE,
                                "[persistence] anonymisation of " + playerId + " failed",
                                failure);
                        future.completeExceptionally(
                                new PersistenceException(
                                        "anonymisation failed for " + playerId, failure));
                    }
                });
        return future;
    }

    /**
     * Replaces every trace of a player with a random substitute, in one transaction.
     *
     * <p>All of it or none of it. A partially anonymised state would be the worst outcome
     * available: it satisfies no deletion request and leaves the data inconsistent. Hence a single
     * transaction with an explicit rollback rather than a sequence of independent statements.
     *
     * <p>Order matters. Statistics and audit rows are re-pointed at the substitute <em>before</em>
     * the player row is deleted, because the foreign key would otherwise cascade them away and the
     * all-time totals FR-017 promises would lose exactly this player's contribution.
     */
    private void anonymizeInOneTransaction(UUID playerId) throws SQLException {
        AnonymizedId substitute = AnonymizedId.random();

        try (Connection connection = loginPool.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement statement =
                        connection.prepareStatement(REPOINT_STATISTICS)) {
                    statement.setObject(1, substitute.value());
                    statement.setObject(2, playerId);
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement(REPOINT_AUDIT_ACTOR)) {
                    statement.setString(1, substitute.value().toString());
                    statement.setString(2, playerId.toString());
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement(CLEAR_AUDIT_TARGET)) {
                    statement.setObject(1, playerId);
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement(DELETE_ITEMS)) {
                    statement.setObject(1, playerId);
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement(DELETE_PLAYER)) {
                    statement.setObject(1, playerId);
                    statement.executeUpdate();
                }
                // FR-017c: record the act itself - but without the anonymised identifier, or the
                // audit log would preserve exactly the reference the operation removed.
                try (PreparedStatement statement = connection.prepareStatement(RECORD_ANONYMISATION)) {
                    statement.setTimestamp(1, Timestamp.from(clock.instant()));
                    statement.setString(2, "system");
                    statement.setString(3, "player_anonymized");
                    statement.executeUpdate();
                }
                connection.commit();
            } catch (SQLException failure) {
                connection.rollback();
                throw failure;
            }
        }
    }

    /**
     * Writes the row for an account seen for the first time, inside the caller's transaction.
     *
     * <p>Deliberately outside the write-behind buffer, and the only place that is. Everything a player
     * owns references this row by foreign key - {@code rpg.character.player_id} above all - and B07
     * creates a character within seconds of the login, long before the first autosave. Left to the
     * buffer, the very first class selection of every new player failed on that key.
     *
     * <p>Not a second write path for player <em>state</em>: this writes the initial record once, and
     * every change to it afterwards still goes through the buffer and its revision guard. The insert
     * does nothing if the row is already there, so a concurrent login cannot turn into an error.
     *
     * <p>Also puts the state into the cache, so the next flush computes its revision guard against the
     * row that now exists rather than finding nothing and dropping the mark.
     */
    public void insertInitial(Connection connection, PlayerState state) throws SQLException {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(state, "state");
        try (PreparedStatement statement = connection.prepareStatement(INSERT_INITIAL)) {
            statement.setObject(1, state.playerId());
            statement.setInt(2, state.dataVersion());
            statement.setLong(3, state.revision());
            statement.setTimestamp(4, Timestamp.from(state.lastSeenAt()));
            statement.setBoolean(5, state.anonymized());
            statement.executeUpdate();
        }
        cache.put(state.playerId(), state);
    }

    /** Puts a state into the in-memory cache and marks it for the next flush. */
    public void put(PlayerState state) {
        cache.put(state.playerId(), state);
        markDirty(state.playerId());
    }

    /** The cached, authoritative state of a connected player. */
    public Optional<PlayerState> cached(UUID playerId) {
        return Optional.ofNullable(cache.get(playerId));
    }

    /** Drops a player from the cache once their final write is done. */
    public void evict(UUID playerId) {
        cache.remove(playerId);
    }

    // --- BatchWriter ---

    @Override
    public List<rpg.core.persistence.DirtyMark> write(
            DataSource dataSource, List<rpg.core.persistence.DirtyMark> marks) {
        List<rpg.core.persistence.DirtyMark> persisted = new ArrayList<>();
        Map<rpg.core.persistence.DirtyMark, PlayerState> written = new HashMap<>();

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(UPSERT)) {
                for (rpg.core.persistence.DirtyMark mark : marks) {
                    UUID playerId = UUID.fromString(mark.aggregateId());
                    PlayerState state = cache.get(playerId);
                    if (state == null) {
                        // Evicted between marking and flushing - nothing to write, and keeping the
                        // mark forever would block the buffer.
                        persisted.add(mark);
                        continue;
                    }
                    PlayerState next = state.nextRevision(clock.instant());
                    statement.setObject(1, next.playerId());
                    statement.setInt(2, next.dataVersion());
                    statement.setLong(3, next.revision());
                    statement.setTimestamp(4, Timestamp.from(next.lastSeenAt()));
                    statement.setBoolean(5, next.anonymized());
                    statement.setLong(6, state.revision()); // the conditional guard
                    statement.addBatch();
                    written.put(mark, next);
                }

                int[] results = statement.executeBatch();
                connection.commit();
                applyResults(marks, written, results, persisted);
            } catch (SQLException failure) {
                connection.rollback();
                throw failure;
            }
        } catch (SQLException failure) {
            throw new PersistenceException("player state batch failed", failure);
        }
        return persisted;
    }

    /**
     * Turns the batch's per-row counts into "persisted" decisions.
     *
     * <p>A row count of zero means the conditional {@code WHERE revision = ?} did not match, i.e.
     * someone else wrote in the meantime. That mark is <em>not</em> reported as persisted, so it
     * stays in the buffer and is retried against the then-current revision.
     */
    private void applyResults(
            List<rpg.core.persistence.DirtyMark> marks,
            Map<rpg.core.persistence.DirtyMark, PlayerState> written,
            int[] results,
            List<rpg.core.persistence.DirtyMark> persisted) {
        int index = 0;
        for (rpg.core.persistence.DirtyMark mark : marks) {
            PlayerState next = written.get(mark);
            if (next == null) {
                continue; // already accounted for above
            }
            int affected = index < results.length ? results[index] : 0;
            index++;
            if (affected > 0 || affected == PreparedStatement.SUCCESS_NO_INFO) {
                cache.put(next.playerId(), next);
                persisted.add(mark);
            } else {
                logger.warning(
                        "[persistence] "
                                + new StaleVersionException(
                                                mark.aggregateId(), next.revision() - 1, -1)
                                        .getMessage()
                                + " - keeping the change for the next flush");
            }
        }
    }

    private Optional<PlayerState> readFromDatabase(UUID playerId) throws SQLException {
        try (Connection connection = loginPool.getConnection();
                PreparedStatement statement = connection.prepareStatement(SELECT)) {
            statement.setObject(1, playerId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return Optional.empty();
                }
                return Optional.of(PlayerStateRowMapper.INSTANCE.fromRow(rows));
            }
        }
    }
}
