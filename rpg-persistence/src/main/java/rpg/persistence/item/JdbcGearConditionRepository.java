package rpg.persistence.item;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import javax.sql.DataSource;

import rpg.core.classes.LadderSlot;
import rpg.core.item.GearCondition;
import rpg.core.item.GearConditionRepository;
import rpg.core.persistence.AggregateType;
import rpg.core.persistence.DirtyMark;
import rpg.core.persistence.PersistenceException;
import rpg.core.persistence.WriteBehindCoordinator;
import rpg.core.scheduler.Scheduler;
import rpg.persistence.jdbc.BatchWriter;

/**
 * Registration 3 von 3 für den neuen Aggregattyp (ADR-015).
 *
 * <p>Gebaut wie {@code JdbcClassProgressRepository} in B07, bis hin zur lebenden Quelle: der Puffer
 * hält eine <b>Markierung, keinen Wert</b>. Beim Schreiben wird der aktuelle Zustand dort geholt, wo
 * die Sitzung ihn hält. Ein zur Markierungszeit festgehaltener Wert wäre veraltet, sobald der Stapel
 * läuft — und bei diesem Aggregat mehr als irgendwo sonst, weil zwischen Markierung und Stapel
 * dutzende Treffer liegen können.
 *
 * <p><b>Und genau deshalb ist es hier richtig und nicht bloß üblich.</b> Der Zustand ändert sich bei
 * jedem Treffer. Würde jede Änderung geschrieben, wäre der Kampfpfad ein Datenbankpfad — das
 * verbietet Prinzip II, und bei 150 Spielern und 800 Kreaturen wäre es das Erste, was umfällt.
 */
public final class JdbcGearConditionRepository implements GearConditionRepository, BatchWriter {

    private static final String SELECT_ONE =
            "SELECT character_id, armor_condition, weapon_condition, data_version, revision"
                    + " FROM rpg.character_gear_condition WHERE character_id = ?";

    private static final String UPSERT =
            "INSERT INTO rpg.character_gear_condition"
                    + " (character_id, armor_condition, weapon_condition, data_version, revision,"
                    + " updated_at)"
                    + " VALUES (?, ?, ?, ?, ?, ?)"
                    + " ON CONFLICT (character_id) DO UPDATE SET"
                    + "   armor_condition = excluded.armor_condition,"
                    + "   weapon_condition = excluded.weapon_condition,"
                    + "   data_version = excluded.data_version,"
                    + "   revision = rpg.character_gear_condition.revision + 1,"
                    + "   updated_at = excluded.updated_at";

    private final DataSource readPool;
    private final Scheduler scheduler;
    private final WriteBehindCoordinator coordinator;
    private final Clock clock;
    private final Map<UUID, Long> revisions = new ConcurrentHashMap<>();

    private volatile Function<UUID, Optional<GearCondition>> liveSource = id -> Optional.empty();

    public JdbcGearConditionRepository(
            DataSource readPool,
            Scheduler scheduler,
            WriteBehindCoordinator coordinator,
            Clock clock) {
        this.readPool = Objects.requireNonNull(readPool, "readPool");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Woher der Stapel den aktuellen Zustand liest — der Sitzungsspeicher, der maßgeblich ist. */
    public void setLiveSource(Function<UUID, Optional<GearCondition>> liveSource) {
        this.liveSource = Objects.requireNonNull(liveSource, "liveSource");
    }

    @Override
    public CompletableFuture<Optional<GearCondition>> find(UUID characterId) {
        Objects.requireNonNull(characterId, "characterId");
        CompletableFuture<Optional<GearCondition>> future = new CompletableFuture<>();
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
                                        "could not load the gear condition of character "
                                                + characterId,
                                        failure));
                    }
                });
        return future;
    }

    @Override
    public void markDirty(UUID characterId) {
        Objects.requireNonNull(characterId, "characterId");
        coordinator.markDirty(AggregateType.CHARACTER_GEAR_CONDITION, characterId.toString());
    }

    private static final String SELECT_FOR_PLAYER =
            "SELECT g.character_id, g.armor_condition, g.weapon_condition, g.data_version, g.revision"
                    + " FROM rpg.character_gear_condition g"
                    + " JOIN rpg.character c ON c.character_id = g.character_id"
                    + " WHERE c.player_id = ?";

    /**
     * Der Zustand aller Figuren eines Spielers — eine Abfrage für den einen Ladevorgang.
     *
     * <p>Auf der Verbindung, die der Sitzungsstart ohnehin offen hat. Ein zweiter Ladeweg wäre eine
     * zweite Runde zur Datenbank in dem Moment, in dem ein Spieler wartet.
     */
    public static List<GearCondition> readForPlayer(Connection connection, UUID playerId)
            throws SQLException {
        List<GearCondition> conditions = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(SELECT_FOR_PLAYER)) {
            statement.setObject(1, playerId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    conditions.add(mapRow(rows));
                }
            }
        }
        return conditions;
    }

    /** Liest auf einer bestehenden Verbindung — für den Sitzungsstart, der seine Abfragen bündelt. */
    public static Optional<GearCondition> read(Connection connection, UUID characterId)
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
                    Optional<GearCondition> condition = liveSource.apply(characterId);
                    if (condition.isEmpty()) {
                        // Zwischen Markierung und Stapel freigegeben - meist ein Logout. Der
                        // Entladepfad schreibt den Endstand selbst; hier gibt es nichts zu retten
                        // und nichts zu wiederholen.
                        persisted.add(mark);
                        continue;
                    }
                    statement.setObject(1, characterId);
                    statement.setDouble(2, condition.get().of(LadderSlot.ARMOR));
                    statement.setDouble(3, condition.get().of(LadderSlot.WEAPON));
                    statement.setInt(4, GearCondition.CURRENT_DATA_VERSION);
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
            throw new PersistenceException("gear condition batch failed", failure);
        }
        return persisted;
    }

    private GearCondition map(ResultSet rows) throws SQLException {
        GearCondition condition = mapRow(rows);
        revisions.put(condition.characterId(), rows.getLong("revision"));
        return condition;
    }

    private static GearCondition mapRow(ResultSet rows) throws SQLException {
        Map<LadderSlot, Double> conditions = new EnumMap<>(LadderSlot.class);
        conditions.put(LadderSlot.ARMOR, rows.getDouble("armor_condition"));
        conditions.put(LadderSlot.WEAPON, rows.getDouble("weapon_condition"));
        return new GearCondition(
                rows.getObject("character_id", UUID.class), conditions, rows.getInt("data_version"));
    }
}
