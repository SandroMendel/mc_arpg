package rpg.persistence.item;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import javax.sql.DataSource;

import rpg.core.item.CosmeticRepository;
import rpg.core.item.CosmeticUnlock;
import rpg.core.persistence.AggregateType;
import rpg.core.persistence.DirtyMark;
import rpg.core.persistence.PersistenceException;
import rpg.core.persistence.WriteBehindCoordinator;
import rpg.core.scheduler.Scheduler;
import rpg.persistence.jdbc.BatchWriter;

/**
 * Registration 3 von 3 für {@link AggregateType#CHARACTER_COSMETIC} (ADR-015).
 *
 * <p><b>Eine Menge je Charakter, nicht ein Wert</b> — dieselbe Form wie B08s Fähigkeitszeilen, und
 * mit derselben Folge für den Stapel: er schreibt <em>alle</em> Zeilen des Charakters und löscht,
 * was nicht mehr dabei ist. Nur zu schreiben, was da ist, ließe eine abgelegte Farbe für immer als
 * getragen stehen.
 *
 * <p><b>Und die Reihenfolge innerhalb des Stapels ist tragend.</b> Erst wird jede Zeile auf
 * {@code applied = false} gesetzt, dann die eine getragene auf {@code true}. Andersherum verletzte
 * der Zwischenzustand den Teilindex aus {@code V11_3} — zwei getragene Farben für die Dauer einer
 * Anweisung —, und die Datenbank wiese den ganzen Stapel zurück. Das ist die Art Fehler, die im Test
 * nie auftritt, weil dort nie zwei Farben im Spiel sind.
 */
public final class JdbcCosmeticRepository implements CosmeticRepository, BatchWriter {

    private static final String SELECT_FOR_CHARACTER =
            "SELECT character_id, template_key, applied, acquired_at"
                    + " FROM rpg.character_cosmetic WHERE character_id = ?"
                    + " ORDER BY acquired_at, template_key";

    private static final String SELECT_FOR_PLAYER =
            "SELECT c.character_id, c.template_key, c.applied, c.acquired_at"
                    + " FROM rpg.character_cosmetic c"
                    + " JOIN rpg.character ch ON ch.character_id = c.character_id"
                    + " WHERE ch.player_id = ?"
                    + " ORDER BY c.acquired_at, c.template_key";

    private static final String UPSERT =
            "INSERT INTO rpg.character_cosmetic"
                    + " (character_id, template_key, applied, acquired_at, data_version, revision,"
                    + " updated_at)"
                    + " VALUES (?, ?, ?, ?, 1, 0, ?)"
                    + " ON CONFLICT (character_id, template_key) DO UPDATE SET"
                    + "   applied = excluded.applied,"
                    + "   revision = rpg.character_cosmetic.revision + 1,"
                    + "   updated_at = excluded.updated_at";

    /** Alles ablegen, bevor eine Farbe getragen wird — siehe Klassenkommentar. */
    private static final String CLEAR_APPLIED =
            "UPDATE rpg.character_cosmetic SET applied = false WHERE character_id = ? AND applied";

    private static final String DELETE_MISSING =
            "DELETE FROM rpg.character_cosmetic WHERE character_id = ? AND template_key <> ALL (?)";

    private final DataSource readPool;
    private final Scheduler scheduler;
    private final WriteBehindCoordinator coordinator;
    private final Clock clock;

    private volatile Function<UUID, List<CosmeticUnlock>> liveSource = id -> List.of();

    public JdbcCosmeticRepository(
            DataSource readPool,
            Scheduler scheduler,
            WriteBehindCoordinator coordinator,
            Clock clock) {
        this.readPool = Objects.requireNonNull(readPool, "readPool");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Woher der Stapel den aktuellen Besitz liest — der Sitzungsspeicher, der maßgeblich ist. */
    public void setLiveSource(Function<UUID, List<CosmeticUnlock>> liveSource) {
        this.liveSource = Objects.requireNonNull(liveSource, "liveSource");
    }

    @Override
    public CompletableFuture<List<CosmeticUnlock>> find(UUID characterId) {
        Objects.requireNonNull(characterId, "characterId");
        CompletableFuture<List<CosmeticUnlock>> future = new CompletableFuture<>();
        scheduler.runAsync(
                () -> {
                    try (Connection connection = readPool.getConnection()) {
                        future.complete(read(connection, characterId));
                    } catch (SQLException failure) {
                        future.completeExceptionally(
                                new PersistenceException(
                                        "could not load the cosmetics of character " + characterId,
                                        failure));
                    }
                });
        return future;
    }

    @Override
    public void markDirty(UUID characterId) {
        Objects.requireNonNull(characterId, "characterId");
        coordinator.markDirty(AggregateType.CHARACTER_COSMETIC, characterId.toString());
    }

    /** Liest auf einer bestehenden Verbindung. */
    public static List<CosmeticUnlock> read(Connection connection, UUID characterId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_FOR_CHARACTER)) {
            statement.setObject(1, characterId);
            return collect(statement);
        }
    }

    /** Alle Figuren eines Spielers — eine Abfrage für den einen Ladevorgang. */
    public static List<CosmeticUnlock> readForPlayer(Connection connection, UUID playerId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_FOR_PLAYER)) {
            statement.setObject(1, playerId);
            return collect(statement);
        }
    }

    @Override
    public List<DirtyMark> write(DataSource dataSource, List<DirtyMark> marks) {
        List<DirtyMark> persisted = new ArrayList<>();
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                for (DirtyMark mark : marks) {
                    writeOne(connection, UUID.fromString(mark.aggregateId()));
                    persisted.add(mark);
                }
                connection.commit();
            } catch (SQLException failure) {
                connection.rollback();
                throw failure;
            }
        } catch (SQLException failure) {
            throw new PersistenceException("cosmetic batch failed", failure);
        }
        return persisted;
    }

    private void writeOne(Connection connection, UUID characterId) throws SQLException {
        List<CosmeticUnlock> owned = liveSource.apply(characterId);
        if (owned.isEmpty()) {
            // Zwischen Markierung und Stapel freigegeben. Der Entladepfad schreibt den Endstand
            // selbst; hier gibt es nichts zu retten und nichts zu wiederholen.
            return;
        }

        // Erst ablegen, dann tragen. Andersherum staenden fuer die Dauer einer Anweisung zwei
        // getragene Farben da, und der Teilindex aus V11_3 wiese den ganzen Stapel zurueck.
        try (PreparedStatement clear = connection.prepareStatement(CLEAR_APPLIED)) {
            clear.setObject(1, characterId);
            clear.executeUpdate();
        }

        Timestamp now = Timestamp.from(clock.instant());
        try (PreparedStatement statement = connection.prepareStatement(UPSERT)) {
            for (CosmeticUnlock unlock : owned) {
                statement.setObject(1, characterId);
                statement.setString(2, unlock.templateKey());
                statement.setBoolean(3, unlock.applied());
                statement.setTimestamp(4, Timestamp.from(unlock.acquiredAt()));
                statement.setTimestamp(5, now);
                statement.addBatch();
            }
            statement.executeBatch();
        }

        // Was der Speicher nicht mehr kennt, ist weg. Ohne das bliebe eine Farbe, die ein
        // Betreiberbefehl entfernt hat, bis zum naechsten Neustart bestehen.
        try (PreparedStatement delete = connection.prepareStatement(DELETE_MISSING)) {
            delete.setObject(1, characterId);
            delete.setArray(
                    2,
                    connection.createArrayOf(
                            "text", owned.stream().map(CosmeticUnlock::templateKey).toArray()));
            delete.executeUpdate();
        }
    }

    private static List<CosmeticUnlock> collect(PreparedStatement statement) throws SQLException {
        List<CosmeticUnlock> unlocks = new ArrayList<>();
        try (ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                unlocks.add(
                        new CosmeticUnlock(
                                rows.getObject("character_id", UUID.class),
                                rows.getString("template_key"),
                                rows.getBoolean("applied"),
                                rows.getTimestamp("acquired_at").toInstant()));
            }
        }
        return unlocks;
    }
}
