package rpg.persistence.zone;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import javax.sql.DataSource;

import rpg.core.persistence.AggregateType;
import rpg.core.persistence.DirtyMark;
import rpg.core.persistence.PersistenceException;
import rpg.core.persistence.WriteBehindCoordinator;
import rpg.core.scheduler.Scheduler;
import rpg.core.zone.ZoneCharacterState;
import rpg.core.zone.ZoneStateRepository;
import rpg.persistence.jdbc.BatchWriter;

/**
 * B09's state in PostgreSQL: discovered crystals and a pending respawn.
 *
 * <p><b>Repository and batch writer in one class</b>, as in the blocks before it. And <b>two tables
 * under one aggregate</b>, which is the unusual part: {@code character_waypoints} and
 * {@code character_zone_state} belong to the same character and are written in the same moment, so
 * giving them two aggregate types would mean two positions in the write order for one thing.
 * {@code character_inventory} already shows an aggregate may carry more than one table.
 *
 * <p><b>The unlock table is append-only.</b> Writes use {@code ON CONFLICT DO NOTHING}, so a flush
 * that re-sends a crystal somebody already had costs nothing and changes nothing - and no code path
 * exists that could delete one (FR-051b2). The only thing that removes a row is
 * {@code ON DELETE CASCADE} when the character goes (FR-051b1).
 */
public final class JdbcZoneStateRepository implements ZoneStateRepository, BatchWriter {

    private static final String SELECT_STATE =
            "SELECT pending_respawn_zone FROM rpg.character_zone_state WHERE character_id = ?";

    private static final String SELECT_WAYPOINTS =
            "SELECT crystal_key FROM rpg.character_waypoints WHERE character_id = ?";

    private static final String UPSERT_STATE =
            "INSERT INTO rpg.character_zone_state"
                    + " (character_id, pending_respawn_zone, data_version, revision, updated_at)"
                    + " VALUES (?, ?, 1, 0, ?)"
                    + " ON CONFLICT (character_id) DO UPDATE SET"
                    + "   pending_respawn_zone = excluded.pending_respawn_zone,"
                    + "   revision = rpg.character_zone_state.revision + 1,"
                    + "   updated_at = excluded.updated_at";

    private static final String INSERT_WAYPOINT =
            "INSERT INTO rpg.character_waypoints (character_id, crystal_key, unlocked_at)"
                    + " VALUES (?, ?, ?)"
                    + " ON CONFLICT (character_id, crystal_key) DO NOTHING";

    private final DataSource readPool;
    private final Scheduler scheduler;
    private final WriteBehindCoordinator coordinator;
    private final Clock clock;

    /** Where the flush reads the current state from - the in-memory store while online. */
    private volatile Function<UUID, Optional<ZoneCharacterState>> liveSource =
            id -> Optional.empty();

    public JdbcZoneStateRepository(
            DataSource readPool,
            Scheduler scheduler,
            WriteBehindCoordinator coordinator,
            Clock clock) {
        this.readPool = Objects.requireNonNull(readPool, "readPool");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public void setLiveSource(Function<UUID, Optional<ZoneCharacterState>> liveSource) {
        this.liveSource = Objects.requireNonNull(liveSource, "liveSource");
    }

    @Override
    public CompletableFuture<Optional<ZoneCharacterState>> find(UUID characterId) {
        Objects.requireNonNull(characterId, "characterId");
        CompletableFuture<Optional<ZoneCharacterState>> future = new CompletableFuture<>();
        scheduler.runAsync(
                () -> {
                    try (Connection connection = readPool.getConnection()) {
                        future.complete(read(connection, characterId));
                    } catch (SQLException failure) {
                        future.completeExceptionally(
                                new PersistenceException(
                                        "could not load zone state of character " + characterId,
                                        failure));
                    }
                });
        return future;
    }

    /**
     * Reads both tables.
     *
     * <p>The row in {@code character_zone_state} is what decides whether this returns empty: a
     * character with unlocks but no state row cannot exist, because the row is written the first time
     * the block places them. Empty therefore means "never seen", which is what FR-037b turns on.
     */
    public static Optional<ZoneCharacterState> read(Connection connection, UUID characterId)
            throws SQLException {
        String pending;
        try (PreparedStatement statement = connection.prepareStatement(SELECT_STATE)) {
            statement.setObject(1, characterId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return Optional.empty();
                }
                pending = rows.getString("pending_respawn_zone");
            }
        }
        Set<String> unlocked = new HashSet<>();
        try (PreparedStatement statement = connection.prepareStatement(SELECT_WAYPOINTS)) {
            statement.setObject(1, characterId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    unlocked.add(rows.getString("crystal_key"));
                }
            }
        }
        return Optional.of(
                new ZoneCharacterState(characterId, unlocked, Optional.ofNullable(pending)));
    }

    /**
     * Everything this player's characters carry, in one pair of queries.
     *
     * <p>Called by the session loader on the login path, off the tick, so that nothing later has to
     * ask the database again - the reason B02 gives once and every block since has followed.
     */
    public static List<ZoneCharacterState> readForPlayer(Connection connection, UUID playerId)
            throws SQLException {
        Map<UUID, Set<String>> unlocks = new HashMap<>();
        try (PreparedStatement statement =
                connection.prepareStatement(
                        "SELECT w.character_id, w.crystal_key FROM rpg.character_waypoints w"
                                + " JOIN rpg.character c ON c.character_id = w.character_id"
                                + " WHERE c.player_id = ?")) {
            statement.setObject(1, playerId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    unlocks
                            .computeIfAbsent(
                                    rows.getObject("character_id", UUID.class),
                                    ignored -> new HashSet<>())
                            .add(rows.getString("crystal_key"));
                }
            }
        }

        List<ZoneCharacterState> states = new ArrayList<>();
        try (PreparedStatement statement =
                connection.prepareStatement(
                        "SELECT s.character_id, s.pending_respawn_zone"
                                + " FROM rpg.character_zone_state s"
                                + " JOIN rpg.character c ON c.character_id = s.character_id"
                                + " WHERE c.player_id = ?")) {
            statement.setObject(1, playerId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    UUID characterId = rows.getObject("character_id", UUID.class);
                    states.add(
                            new ZoneCharacterState(
                                    characterId,
                                    unlocks.getOrDefault(characterId, Set.of()),
                                    Optional.ofNullable(rows.getString("pending_respawn_zone"))));
                }
            }
        }
        return states;
    }

    @Override
    public void markSeen(UUID characterId) {
        markDirty(characterId);
    }

    @Override
    public void unlock(UUID characterId, String crystalKey) {
        Objects.requireNonNull(crystalKey, "crystalKey");
        markDirty(characterId);
    }

    @Override
    public void setPendingRespawn(UUID characterId, String zoneKey) {
        markDirty(characterId);
    }

    private void markDirty(UUID characterId) {
        Objects.requireNonNull(characterId, "characterId");
        coordinator.markDirty(AggregateType.CHARACTER_ZONE_STATE, characterId.toString());
    }

    /**
     * Writes one transaction for the whole batch.
     *
     * <p><b>One connection and one commit, not one per mark.</b> The write pool hands out
     * connections with auto-commit switched off (ConnectionPools) - every write here lives inside an
     * explicit transaction, and a batch that ended without {@code commit()} would roll back silently
     * on close. Nothing would fail, nothing would be logged, and every flush would quietly write
     * nothing. That is what happened to the first version of this method, and only a test against a
     * real PostgreSQL could show it.
     */
    @Override
    public List<DirtyMark> write(DataSource dataSource, List<DirtyMark> marks) {
        List<DirtyMark> written = new ArrayList<>();
        Timestamp now = Timestamp.from(clock.instant());
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                for (DirtyMark mark : marks) {
                    UUID characterId = UUID.fromString(mark.aggregateId());
                    Optional<ZoneCharacterState> state = liveSource.apply(characterId);
                    if (state.isEmpty()) {
                        // The character left before the flush and the memory copy is gone.
                        // Reporting the mark as written is right: there is nothing left to
                        // persist, and keeping it would retry forever against a source that will
                        // never answer again.
                        written.add(mark);
                        continue;
                    }
                    writeOne(connection, state.get(), now);
                    written.add(mark);
                }
                connection.commit();
            } catch (SQLException failure) {
                connection.rollback();
                throw failure;
            }
        } catch (SQLException failure) {
            // Thrown rather than returned: the buffer keeps the marks and the next round tries
            // again, which is the contract of this method.
            throw new PersistenceException("zone state batch failed", failure);
        }
        return written;
    }

    private static void writeOne(Connection connection, ZoneCharacterState state, Timestamp now)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(UPSERT_STATE)) {
            statement.setObject(1, state.characterId());
            statement.setString(2, state.pendingRespawnZone().orElse(null));
            statement.setTimestamp(3, now);
            statement.executeUpdate();
        }
        if (state.unlockedCrystals().isEmpty()) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(INSERT_WAYPOINT)) {
            for (String crystalKey : state.unlockedCrystals()) {
                statement.setObject(1, state.characterId());
                statement.setString(2, crystalKey);
                statement.setTimestamp(3, now);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }
}
