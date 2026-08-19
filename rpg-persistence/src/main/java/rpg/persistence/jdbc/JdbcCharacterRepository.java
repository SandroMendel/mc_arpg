package rpg.persistence.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
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

import javax.sql.DataSource;

import rpg.core.persistence.AggregateType;
import rpg.core.persistence.DirtyMark;
import rpg.core.persistence.PersistenceException;
import rpg.core.persistence.WriteBehindCoordinator;
import rpg.core.scheduler.Scheduler;
import rpg.core.session.CharacterClass;
import rpg.core.session.CharacterClassTakenException;
import rpg.core.session.CharacterRepository;
import rpg.core.session.PlayerCharacter;

/**
 * Characters on top of plain JDBC, following the same shape as B02's repositories.
 *
 * <p>The one thing worth reading before changing it: {@link #create} relies on the unique key
 * {@code (player_id, character_class)} to reject a duplicate rather than checking first. A
 * read-then-write check would look equivalent and would leave a window in which two concurrent
 * creations both see "no Warrior yet" and both insert one. The database has no such window.
 */
public final class JdbcCharacterRepository implements CharacterRepository, BatchWriter {

    /** PostgreSQL's unique-violation SQLSTATE. */
    private static final String UNIQUE_VIOLATION = "23505";

    private static final String SELECT_BY_PLAYER =
            "SELECT character_id, player_id, character_class, data_version, revision,"
                    + " created_at, last_played_at"
                    + " FROM rpg.character WHERE player_id = ? ORDER BY character_class";

    private static final String SELECT_ONE =
            "SELECT character_id, player_id, character_class, data_version, revision,"
                    + " created_at, last_played_at"
                    + " FROM rpg.character WHERE character_id = ?";

    private static final String INSERT =
            "INSERT INTO rpg.character"
                    + " (character_id, player_id, character_class, data_version, revision,"
                    + "  created_at, last_played_at)"
                    + " VALUES (?, ?, ?, ?, 0, ?, ?)";

    private static final String UPSERT =
            "INSERT INTO rpg.character"
                    + " (character_id, player_id, character_class, data_version, revision,"
                    + "  created_at, last_played_at)"
                    + " VALUES (?, ?, ?, ?, ?, ?, ?)"
                    + " ON CONFLICT (character_id) DO UPDATE SET"
                    + "   data_version = excluded.data_version,"
                    + "   revision = excluded.revision,"
                    + "   last_played_at = excluded.last_played_at";

    private final DataSource readPool;
    private final Scheduler scheduler;
    private final WriteBehindCoordinator coordinator;
    private final Clock clock;

    /** Characters of connected players; authoritative while the session lives (Constitution IV). */
    private final Map<UUID, PlayerCharacter> cache = new ConcurrentHashMap<>();

    public JdbcCharacterRepository(
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
    public CompletableFuture<List<PlayerCharacter>> findByPlayer(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        CompletableFuture<List<PlayerCharacter>> future = new CompletableFuture<>();
        scheduler.runAsync(
                () -> {
                    try (Connection connection = readPool.getConnection()) {
                        future.complete(readByPlayer(connection, playerId));
                    } catch (SQLException failure) {
                        future.completeExceptionally(
                                new PersistenceException(
                                        "could not load characters of " + playerId, failure));
                    }
                });
        return future;
    }

    @Override
    public CompletableFuture<Optional<PlayerCharacter>> find(UUID characterId) {
        Objects.requireNonNull(characterId, "characterId");
        CompletableFuture<Optional<PlayerCharacter>> future = new CompletableFuture<>();
        scheduler.runAsync(
                () -> {
                    try (Connection connection = readPool.getConnection();
                            PreparedStatement statement = connection.prepareStatement(SELECT_ONE)) {
                        statement.setObject(1, characterId);
                        try (ResultSet rows = statement.executeQuery()) {
                            if (!rows.next()) {
                                future.complete(Optional.empty());
                                return;
                            }
                            PlayerCharacter character = read(rows);
                            cache.put(character.characterId(), character);
                            future.complete(Optional.of(character));
                        }
                    } catch (SQLException failure) {
                        future.completeExceptionally(
                                new PersistenceException(
                                        "could not load character " + characterId, failure));
                    }
                });
        return future;
    }

    @Override
    public CompletableFuture<PlayerCharacter> create(UUID playerId, CharacterClass characterClass) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(characterClass, "characterClass");

        CompletableFuture<PlayerCharacter> future = new CompletableFuture<>();
        scheduler.runAsync(
                () -> {
                    PlayerCharacter created =
                            PlayerCharacter.create(playerId, characterClass, clock.instant());
                    try (Connection connection = readPool.getConnection();
                            PreparedStatement statement = connection.prepareStatement(INSERT)) {
                        connection.setAutoCommit(false);
                        statement.setObject(1, created.characterId());
                        statement.setObject(2, created.playerId());
                        statement.setString(3, created.characterClass().name());
                        statement.setInt(4, created.dataVersion());
                        statement.setTimestamp(5, Timestamp.from(created.createdAt()));
                        statement.setTimestamp(6, Timestamp.from(created.lastPlayedAt()));
                        statement.executeUpdate();
                        connection.commit();
                        cache.put(created.characterId(), created);
                        future.complete(created);
                    } catch (SQLException failure) {
                        if (isUniqueViolation(failure)) {
                            // The key did the checking - no window for a concurrent second insert.
                            future.completeExceptionally(
                                    new CharacterClassTakenException(playerId, characterClass));
                        } else {
                            future.completeExceptionally(
                                    new PersistenceException(
                                            "could not create character for " + playerId, failure));
                        }
                    }
                });
        return future;
    }

    @Override
    public void markDirty(UUID characterId) {
        coordinator.markDirty(AggregateType.CHARACTER, characterId.toString());
    }

    /** Puts a character into the cache and marks it for the next flush. */
    public void put(PlayerCharacter character) {
        cache.put(character.characterId(), character);
        markDirty(character.characterId());
    }

    /** Drops characters of a player from the cache once their session ended. */
    public void evictPlayer(UUID playerId) {
        cache.values().removeIf(character -> character.playerId().equals(playerId));
    }

    /** Reads every character of an account on a caller-supplied connection - used by the bundle. */
    public List<PlayerCharacter> readByPlayer(Connection connection, UUID playerId)
            throws SQLException {
        List<PlayerCharacter> characters = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(SELECT_BY_PLAYER)) {
            statement.setObject(1, playerId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    PlayerCharacter character = read(rows);
                    cache.put(character.characterId(), character);
                    characters.add(character);
                }
            }
        }
        return List.copyOf(characters);
    }

    // --- BatchWriter ---

    @Override
    public List<DirtyMark> write(DataSource dataSource, List<DirtyMark> marks) {
        List<DirtyMark> persisted = new ArrayList<>();
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(UPSERT)) {
                for (DirtyMark mark : marks) {
                    PlayerCharacter character = cache.get(UUID.fromString(mark.aggregateId()));
                    if (character == null) {
                        persisted.add(mark);
                        continue;
                    }
                    statement.setObject(1, character.characterId());
                    statement.setObject(2, character.playerId());
                    statement.setString(3, character.characterClass().name());
                    statement.setInt(4, character.dataVersion());
                    statement.setLong(5, character.revision() + 1);
                    statement.setTimestamp(6, Timestamp.from(character.createdAt()));
                    statement.setTimestamp(7, Timestamp.from(clock.instant()));
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
            throw new PersistenceException("character batch failed", failure);
        }
        return persisted;
    }

    private static boolean isUniqueViolation(SQLException failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof SQLIntegrityConstraintViolationException) {
                return true;
            }
            if (current instanceof SQLException sql && UNIQUE_VIOLATION.equals(sql.getSQLState())) {
                return true;
            }
        }
        return false;
    }

    private static PlayerCharacter read(ResultSet rows) throws SQLException {
        return new PlayerCharacter(
                rows.getObject("character_id", UUID.class),
                rows.getObject("player_id", UUID.class),
                CharacterClass.valueOf(rows.getString("character_class")),
                rows.getInt("data_version"),
                rows.getLong("revision"),
                rows.getTimestamp("created_at").toInstant(),
                rows.getTimestamp("last_played_at").toInstant());
    }
}
