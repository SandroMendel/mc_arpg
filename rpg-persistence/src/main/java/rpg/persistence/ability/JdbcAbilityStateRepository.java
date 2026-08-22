package rpg.persistence.ability;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import javax.sql.DataSource;

import rpg.core.ability.AbilityState;
import rpg.core.ability.AbilityStateRepository;
import rpg.core.ability.ToggleState;
import rpg.core.persistence.AggregateType;
import rpg.core.persistence.DirtyMark;
import rpg.core.persistence.PersistenceException;
import rpg.core.persistence.WriteBehindCoordinator;
import rpg.core.scheduler.Scheduler;
import rpg.persistence.jdbc.BatchWriter;

/**
 * Registration 3 of 3 for the new aggregate type (ADR-015).
 *
 * <p>Structured like {@code JdbcClassProgressRepository} from B07, down to the live source: the
 * buffer holds a mark, not a value, so at flush time the current state is fetched from wherever the
 * session keeps it. A value captured at mark time would be stale by the time the batch runs.
 *
 * <p><b>One difference that matters: a character has many rows, not one.</b> A flush therefore writes
 * a whole set and <em>deletes</em> what fell back to the default - rank 1, no running cooldown, no
 * toggle. Without the delete the table would keep a row for every ability a player ever used, all of
 * them carrying nothing but defaults after the cooldown passed.
 */
public final class JdbcAbilityStateRepository implements AbilityStateRepository, BatchWriter {

    private static final String SELECT_ALL =
            "SELECT character_id, ability_id, rank, cooldown_until, toggle_state, data_version,"
                    + " revision"
                    + " FROM rpg.character_abilities WHERE character_id = ?";

    private static final String UPSERT =
            "INSERT INTO rpg.character_abilities"
                    + " (character_id, ability_id, rank, cooldown_until, toggle_state, data_version,"
                    + "  revision, updated_at)"
                    + " VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
                    + " ON CONFLICT (character_id, ability_id) DO UPDATE SET"
                    + "   rank = excluded.rank,"
                    + "   cooldown_until = excluded.cooldown_until,"
                    + "   toggle_state = excluded.toggle_state,"
                    + "   data_version = excluded.data_version,"
                    + "   revision = rpg.character_abilities.revision + 1,"
                    + "   updated_at = excluded.updated_at";

    /**
     * Removes the row of an ability whose live state fell back to the default.
     *
     * <p><b>Needed next to the sweep below, not instead of it.</b> A state that is default is skipped
     * by the upsert - there is nothing worth writing - and the sweep only matches rows that are
     * <em>stored</em> as default. Without this statement a rank-4 row whose ability was reset to rank
     * 1 would match neither and sit there forever with the old value, which is exactly what the
     * persistence test caught.
     */
    private static final String DELETE_ONE =
            "DELETE FROM rpg.character_abilities WHERE character_id = ? AND ability_id = ?";

    /**
     * Removes rows that carry nothing any more, whether or not the character still holds them.
     *
     * <p>Catches the case the statement above cannot: a cooldown that expired while the character was
     * offline is dropped on load, so it never appears in the live set at all.
     *
     * <p>Deliberately expressed as a condition rather than by listing ids: the set of abilities is
     * configuration, so a delete statement that names them would go stale the moment one is added.
     */
    private static final String DELETE_DEFAULTS =
            "DELETE FROM rpg.character_abilities"
                    + " WHERE character_id = ?"
                    + "   AND rank = 1"
                    + "   AND toggle_state IS NULL"
                    + "   AND (cooldown_until IS NULL OR cooldown_until <= ?)";

    private final DataSource readPool;
    private final Scheduler scheduler;
    private final WriteBehindCoordinator coordinator;
    private final Clock clock;
    private final Map<UUID, Long> revisions = new ConcurrentHashMap<>();

    private volatile Function<UUID, List<AbilityState>> liveSource = id -> List.of();

    public JdbcAbilityStateRepository(
            DataSource readPool,
            Scheduler scheduler,
            WriteBehindCoordinator coordinator,
            Clock clock) {
        this.readPool = Objects.requireNonNull(readPool, "readPool");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Where the flush reads the current state from - the session cache, which is authoritative. */
    public void setLiveSource(Function<UUID, List<AbilityState>> liveSource) {
        this.liveSource = Objects.requireNonNull(liveSource, "liveSource");
    }

    @Override
    public CompletableFuture<List<AbilityState>> findAll(UUID characterId) {
        Objects.requireNonNull(characterId, "characterId");
        CompletableFuture<List<AbilityState>> future = new CompletableFuture<>();
        scheduler.runAsync(
                () -> {
                    try (Connection connection = readPool.getConnection()) {
                        future.complete(read(connection, characterId, clock.instant()));
                    } catch (SQLException failure) {
                        future.completeExceptionally(
                                new PersistenceException(
                                        "could not load the abilities of character " + characterId,
                                        failure));
                    }
                });
        return future;
    }

    @Override
    public void markDirty(UUID characterId) {
        Objects.requireNonNull(characterId, "characterId");
        coordinator.markDirty(AggregateType.CHARACTER_ABILITIES, characterId.toString());
    }

    /**
     * Reads on an existing connection, for the session load that batches its queries.
     *
     * <p><b>An expired cooldown is discarded rather than loaded</b> (FR-031). It has passed; carrying
     * it into memory would only mean every read has to compare it against now again, and the row is
     * deleted on the next flush anyway.
     */
    public static List<AbilityState> read(Connection connection, UUID characterId, Instant now)
            throws SQLException {
        List<AbilityState> states = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(SELECT_ALL)) {
            statement.setObject(1, characterId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    AbilityState state = mapRow(rows, now);
                    if (!state.isDefault(now)) {
                        states.add(state);
                    }
                }
            }
        }
        return List.copyOf(states);
    }

    @Override
    public List<DirtyMark> write(DataSource dataSource, List<DirtyMark> marks) {
        List<DirtyMark> persisted = new ArrayList<>();
        Instant now = clock.instant();
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement upsert = connection.prepareStatement(UPSERT);
                    PreparedStatement deleteOne = connection.prepareStatement(DELETE_ONE);
                    PreparedStatement deleteDefaults =
                            connection.prepareStatement(DELETE_DEFAULTS)) {
                for (DirtyMark mark : marks) {
                    UUID characterId = UUID.fromString(mark.aggregateId());
                    List<AbilityState> states = liveSource.apply(characterId);
                    long revision = revisions.getOrDefault(characterId, 0L) + 1;
                    for (AbilityState state : states) {
                        if (state.isDefault(now)) {
                            // Nothing worth a row - and if one exists from earlier, it has to go.
                            // Merely skipping the upsert would leave the old rank standing.
                            deleteOne.setObject(1, characterId);
                            deleteOne.setString(2, state.abilityId());
                            deleteOne.addBatch();
                            continue;
                        }
                        upsert.setObject(1, characterId);
                        upsert.setString(2, state.abilityId());
                        upsert.setInt(3, state.rank());
                        setTimestamp(upsert, 4, state.cooldownUntil());
                        setToggle(upsert, 5, state.toggleState());
                        upsert.setInt(6, AbilityState.CURRENT_DATA_VERSION);
                        upsert.setLong(7, revision);
                        upsert.setTimestamp(8, Timestamp.from(now));
                        upsert.addBatch();
                    }
                    deleteDefaults.setObject(1, characterId);
                    deleteDefaults.setTimestamp(2, Timestamp.from(now));
                    deleteDefaults.addBatch();
                    revisions.merge(characterId, 1L, Long::sum);
                    persisted.add(mark);
                }
                upsert.executeBatch();
                deleteOne.executeBatch();
                deleteDefaults.executeBatch();
                connection.commit();
            } catch (SQLException failure) {
                connection.rollback();
                throw new PersistenceException("could not write ability state", failure);
            }
        } catch (SQLException failure) {
            throw new PersistenceException("could not write ability state", failure);
        }
        return persisted;
    }

    private static void setTimestamp(PreparedStatement statement, int index, Instant value)
            throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.TIMESTAMP_WITH_TIMEZONE);
        } else {
            statement.setTimestamp(index, Timestamp.from(value));
        }
    }

    private static void setToggle(PreparedStatement statement, int index, ToggleState value)
            throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.VARCHAR);
        } else {
            statement.setString(index, value.name());
        }
    }

    private static AbilityState mapRow(ResultSet rows, Instant now) throws SQLException {
        Timestamp cooldown = rows.getTimestamp("cooldown_until");
        String toggle = rows.getString("toggle_state");
        Instant cooldownUntil = cooldown == null ? null : cooldown.toInstant();
        if (cooldownUntil != null && !cooldownUntil.isAfter(now)) {
            cooldownUntil = null;
        }
        return new AbilityState(
                rows.getObject("character_id", UUID.class),
                rows.getString("ability_id"),
                rows.getInt("rank"),
                cooldownUntil,
                toggle == null ? null : ToggleState.valueOf(toggle),
                rows.getInt("data_version"),
                rows.getLong("revision"));
    }
}
