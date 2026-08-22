package rpg.persistence.currency;

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
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import javax.sql.DataSource;

import rpg.core.currency.CharacterBalance;
import rpg.core.currency.CharacterBalanceRepository;
import rpg.core.persistence.AggregateType;
import rpg.core.persistence.DirtyMark;
import rpg.core.persistence.PersistenceException;
import rpg.core.persistence.WriteBehindCoordinator;
import rpg.core.scheduler.Scheduler;
import rpg.persistence.jdbc.BatchWriter;

/**
 * Reads and writes {@code rpg.character_balance} (FR-014).
 *
 * <p>Built exactly like {@code JdbcCharacterProgressRepository} in B06 and
 * {@code JdbcCharacterResourcesRepository} in B04, including the live source: while a session lasts
 * the in-memory value is authoritative (Constitution IV), so the flush asks the rules for the
 * current balance rather than keeping a second copy that could disagree.
 *
 * <p><b>Repository and batch writer in one class</b>, as in both blocks above. Splitting them would
 * put the revision counter in one and the reads that populate it in the other.
 *
 * <p><b>The live source returns the stashed value too.</b> {@code DefaultCurrency.liveOrLastKnown}
 * answers from the live map while the session is open and from the stash afterwards, which is what
 * makes the marks left by the last bookings of a session survive the release that follows them
 * (ADR-015 point 7).
 */
public final class JdbcCharacterBalanceRepository
        implements CharacterBalanceRepository, BatchWriter {

    private static final String SELECT_ONE =
            "SELECT character_id, balance, data_version, revision"
                    + " FROM rpg.character_balance WHERE character_id = ?";

    private static final String SELECT_BY_PLAYER =
            "SELECT b.character_id, b.balance, b.data_version, b.revision"
                    + " FROM rpg.character_balance b"
                    + " JOIN rpg.character c ON c.character_id = b.character_id"
                    + " WHERE c.player_id = ?";

    private static final String UPSERT =
            "INSERT INTO rpg.character_balance"
                    + " (character_id, balance, data_version, revision, updated_at)"
                    + " VALUES (?, ?, ?, ?, ?)"
                    + " ON CONFLICT (character_id) DO UPDATE SET"
                    + "   balance = excluded.balance,"
                    + "   data_version = excluded.data_version,"
                    + "   revision = rpg.character_balance.revision + 1,"
                    + "   updated_at = excluded.updated_at";

    private final DataSource readPool;
    private final Scheduler scheduler;
    private final WriteBehindCoordinator coordinator;
    private final Clock clock;
    private final Map<UUID, Long> revisions = new ConcurrentHashMap<>();

    private volatile Function<UUID, OptionalLong> liveSource = id -> OptionalLong.empty();

    public JdbcCharacterBalanceRepository(
            DataSource readPool,
            Scheduler scheduler,
            WriteBehindCoordinator coordinator,
            Clock clock) {
        this.readPool = Objects.requireNonNull(readPool, "readPool");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Where the flush reads the current balance from - live while online, stashed just after. */
    public void setLiveSource(Function<UUID, OptionalLong> liveSource) {
        this.liveSource = Objects.requireNonNull(liveSource, "liveSource");
    }

    @Override
    public CompletableFuture<Optional<CharacterBalance>> find(UUID characterId) {
        Objects.requireNonNull(characterId, "characterId");
        CompletableFuture<Optional<CharacterBalance>> future = new CompletableFuture<>();
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
                                        "could not load balance of character " + characterId,
                                        failure));
                    }
                });
        return future;
    }

    @Override
    public void markDirty(UUID characterId) {
        Objects.requireNonNull(characterId, "characterId");
        coordinator.markDirty(AggregateType.CHARACTER_BALANCE, characterId.toString());
    }

    /** Synchronous read on a caller-provided connection, for the login bundle. */
    public static List<CharacterBalance> readForPlayer(Connection connection, UUID playerId)
            throws SQLException {
        List<CharacterBalance> balances = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(SELECT_BY_PLAYER)) {
            statement.setObject(1, playerId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    balances.add(mapRow(rows));
                }
            }
        }
        return balances;
    }

    /**
     * Synchronous read of one balance, for the operator's path to an offline character.
     *
     * <p>Reaching the database here is deliberate and allowed: Constitution II forbids database
     * access <b>per game event</b>, and an operator command is not one (ADR-028, FR-042).
     */
    public static Optional<CharacterBalance> read(Connection connection, UUID characterId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_ONE)) {
            statement.setObject(1, characterId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(mapRow(rows)) : Optional.empty();
            }
        }
    }

    /** Writes one balance directly, for the same operator path. Never called from a game event. */
    public static void write(Connection connection, UUID characterId, long balance, Clock clock)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(UPSERT)) {
            statement.setObject(1, characterId);
            statement.setLong(2, balance);
            statement.setInt(3, CharacterBalance.CURRENT_DATA_VERSION);
            statement.setLong(4, 1L);
            statement.setTimestamp(5, Timestamp.from(clock.instant()));
            statement.executeUpdate();
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
                    OptionalLong balance = liveSource.apply(characterId);
                    if (balance.isEmpty()) {
                        // Neither live nor stashed. Either the value was already written by an
                        // earlier flush of the same release, or the character was never loaded.
                        // Nothing to salvage and nothing to retry.
                        persisted.add(mark);
                        continue;
                    }
                    statement.setObject(1, characterId);
                    statement.setLong(2, balance.getAsLong());
                    statement.setInt(3, CharacterBalance.CURRENT_DATA_VERSION);
                    statement.setLong(4, revisions.getOrDefault(characterId, 0L) + 1);
                    statement.setTimestamp(5, Timestamp.from(clock.instant()));
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
            throw new PersistenceException("character balance batch failed", failure);
        }
        return persisted;
    }

    private CharacterBalance map(ResultSet rows) throws SQLException {
        CharacterBalance balance = mapRow(rows);
        revisions.put(balance.characterId(), balance.revision());
        return balance;
    }

    private static CharacterBalance mapRow(ResultSet rows) throws SQLException {
        return new CharacterBalance(
                rows.getObject("character_id", UUID.class),
                rows.getLong("balance"),
                rows.getInt("data_version"),
                rows.getLong("revision"));
    }
}
