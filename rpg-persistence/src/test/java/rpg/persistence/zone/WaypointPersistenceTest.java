package rpg.persistence.zone;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.logging.Logger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.persistence.AggregateType;
import rpg.core.persistence.BufferStatus;
import rpg.core.persistence.DirtyMark;
import rpg.core.persistence.FlushReason;
import rpg.core.persistence.FlushResult;
import rpg.core.persistence.PersistenceConfig;
import rpg.core.persistence.WriteBehindCoordinator;
import rpg.core.zone.ZoneCharacterState;
import rpg.core.zone.ZoneStateStore;
import rpg.persistence.ConnectionPools;
import rpg.persistence.SchemaMigrator;
import rpg.persistence.support.DirectScheduler;
import rpg.persistence.support.PostgresContainer;

/**
 * T094 - Freischaltungen gegen ein echtes PostgreSQL (SC-019, SC-027, FR-051a, FR-051b).
 *
 * <p><b>Der Unterschied zu {@code ZoneStateAggregateTest}:</b> dort wird die Tabelle mit
 * handgeschriebenem SQL gefuellt und die Leseseite geprueft. Hier geht der Weg durch den echten
 * Schreibpfad - Store, Markierung, Flush - und die zwei Charaktere haengen am <b>selben</b> Account.
 * Das ist der Unterschied, den ein Testaufbau mit frischen UUIDs verschluckt: solange jeder Charakter
 * seinen eigenen Spieler bekommt, sieht eine kontogebundene Ablage genauso aus wie eine
 * charaktergebundene.
 */
class WaypointPersistenceTest {

    private static final Logger QUIET = Logger.getLogger("waypoint-persistence-test");
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-08-23T18:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void freshSchema() throws Exception {
        PostgresContainer.resetSchema();
        try (ConnectionPools pools = pools()) {
            new SchemaMigrator(pools.writePool(), QUIET).migrateToLatest();
        }
    }

    @Test
    @DisplayName("eine Freischaltung uebersteht den Neustart (SC-019)")
    void anUnlockSurvivesARestart() throws Exception {
        UUID player = insertPlayer();
        UUID character = insertCharacter(player);

        try (ConnectionPools pools = pools()) {
            RecordingCoordinator coordinator = new RecordingCoordinator();
            JdbcZoneStateRepository repository = repository(pools, coordinator);
            ZoneStateStore store = new ZoneStateStore(repository);
            repository.setLiveSource(snapshotOf(store));
            store.load(character, Optional.empty());

            store.markSeen(character);
            store.unlock(character, "greenfields-crystal");
            store.unlock(character, "dustlands-crystal");
            flush(repository, pools, coordinator);

            // Der Neustart: alles Speichergebundene ist weg, nur die Datenbank bleibt.
            try (Connection connection = pools.loginPool().getConnection()) {
                assertThat(
                                JdbcZoneStateRepository.read(connection, character)
                                        .orElseThrow()
                                        .unlockedCrystals())
                        .containsExactlyInAnyOrder("greenfields-crystal", "dustlands-crystal");
            }
        }
    }

    @Test
    @DisplayName("ein zweiter Charakter DESSELBEN Accounts erbt nichts (SC-019, ADR-011)")
    void asecondCharacterOfTheSameAccountInheritsNothing() throws Exception {
        UUID player = insertPlayer();
        UUID warrior = insertCharacter(player, "WARRIOR");
        UUID mage = insertCharacter(player, "MAGE");

        try (ConnectionPools pools = pools()) {
            RecordingCoordinator coordinator = new RecordingCoordinator();
            JdbcZoneStateRepository repository = repository(pools, coordinator);
            ZoneStateStore store = new ZoneStateStore(repository);
            repository.setLiveSource(snapshotOf(store));
            store.load(warrior, Optional.empty());
            store.load(mage, Optional.empty());

            store.markSeen(warrior);
            store.markSeen(mage);
            store.unlock(warrior, "pale-wilds-crystal");
            flush(repository, pools, coordinator);

            // Ueber den Login-Pfad gelesen, weil genau dort eine kontogebundene Verwechslung
            // entstuende: eine Abfrage nach player_id, die alle Zeilen des Accounts einsammelt.
            try (Connection connection = pools.loginPool().getConnection()) {
                List<ZoneCharacterState> states =
                        JdbcZoneStateRepository.readForPlayer(connection, player);

                assertThat(states).hasSize(2);
                assertThat(byId(states, warrior).unlockedCrystals())
                        .containsExactly("pale-wilds-crystal");
                assertThat(byId(states, mage).unlockedCrystals())
                        .as("wer mit dem Krieger gelaufen ist, faengt mit dem Magier neu an")
                        .isEmpty();
            }
        }
    }

    @Test
    @DisplayName("das Loeschen des Charakters raeumt beide Tabellen ab (SC-027, FR-051b1)")
    void deletingTheCharacterCleansUp() throws Exception {
        UUID player = insertPlayer();
        UUID character = insertCharacter(player);

        try (ConnectionPools pools = pools()) {
            RecordingCoordinator coordinator = new RecordingCoordinator();
            JdbcZoneStateRepository repository = repository(pools, coordinator);
            ZoneStateStore store = new ZoneStateStore(repository);
            repository.setLiveSource(snapshotOf(store));
            store.load(character, Optional.empty());
            store.markSeen(character);
            store.unlock(character, "darkforest-crystal");
            store.setPendingRespawn(character, "darkforest");
            flush(repository, pools, coordinator);

            deleteCharacter(character);

            // Es gibt keinen Aufraeumcode im Block - das erledigt ON DELETE CASCADE, und deshalb
            // gibt es auch keine Stelle, die man beim naechsten Feld vergessen koennte.
            assertThat(countRows("rpg.character_waypoints", character)).isZero();
            assertThat(countRows("rpg.character_zone_state", character)).isZero();
        }
    }

    @Test
    @DisplayName("ein Kristall, der aus der Konfiguration faellt und zurueckkommt, wirkt wieder")
    void anUnlockOutlivesAConfigurationChange() throws Exception {
        UUID player = insertPlayer();
        UUID character = insertCharacter(player);

        try (ConnectionPools pools = pools()) {
            RecordingCoordinator coordinator = new RecordingCoordinator();
            JdbcZoneStateRepository repository = repository(pools, coordinator);
            ZoneStateStore store = new ZoneStateStore(repository);
            repository.setLiveSource(snapshotOf(store));
            store.load(character, Optional.empty());
            store.markSeen(character);
            store.unlock(character, "safari-plains-crystal");
            flush(repository, pools, coordinator);

            // Zwischendurch nimmt ein Betreiber die Region aus zones.yml und traegt sie spaeter
            // wieder ein. In der Datenbank passiert dabei nichts - es gibt keinen Fremdschluessel
            // auf eine Zone, weil eine verwaiste Zeile ein gueltiger Zustand ist (FR-051b).
            try (Connection connection = pools.loginPool().getConnection()) {
                assertThat(
                                JdbcZoneStateRepository.read(connection, character)
                                        .orElseThrow()
                                        .unlockedCrystals())
                        .containsExactly("safari-plains-crystal");
            }
        }
    }

    @Test
    @DisplayName("zweimal derselbe Kristall erzeugt keine zweite Zeile und keinen Fehler")
    void flushingTwiceIsHarmless() throws Exception {
        UUID player = insertPlayer();
        UUID character = insertCharacter(player);

        try (ConnectionPools pools = pools()) {
            RecordingCoordinator coordinator = new RecordingCoordinator();
            JdbcZoneStateRepository repository = repository(pools, coordinator);
            ZoneStateStore store = new ZoneStateStore(repository);
            repository.setLiveSource(snapshotOf(store));
            store.load(character, Optional.empty());
            store.markSeen(character);
            store.unlock(character, "greenfields-crystal");

            // Jeder Flush schickt den ganzen Satz mit, nicht nur das Neue. Ohne
            // ON CONFLICT DO NOTHING waere der zweite Durchlauf ein Schluesselkonflikt - und der
            // faende in der Praxis alle dreissig Sekunden statt.
            flush(repository, pools, coordinator);
            coordinator.markDirty(AggregateType.CHARACTER_ZONE_STATE, character.toString());
            flush(repository, pools, coordinator);

            assertThat(countRows("rpg.character_waypoints", character)).isEqualTo(1);
        }
    }

    // --- helpers ---------------------------------------------------------

    private static ZoneCharacterState byId(List<ZoneCharacterState> states, UUID characterId) {
        return states.stream()
                .filter(state -> state.characterId().equals(characterId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("kein Zustand fuer " + characterId));
    }

    private static Function<UUID, Optional<ZoneCharacterState>> snapshotOf(ZoneStateStore store) {
        return characterId ->
                store.isLoaded(characterId)
                        ? Optional.of(
                                new ZoneCharacterState(
                                        characterId,
                                        store.unlockedBy(characterId),
                                        store.pendingRespawnOf(characterId)))
                        : Optional.empty();
    }

    private static void flush(
            JdbcZoneStateRepository repository,
            ConnectionPools pools,
            RecordingCoordinator coordinator) {
        List<DirtyMark> marks = coordinator.drain();
        assertThat(marks)
                .as("ohne Markierung schriebe der Flush nichts - das waere kein Test")
                .isNotEmpty();
        assertThat(repository.write(pools.writePool(), marks)).hasSameSizeAs(marks);
    }

    private static JdbcZoneStateRepository repository(
            ConnectionPools pools, RecordingCoordinator coordinator) {
        return new JdbcZoneStateRepository(
                pools.loginPool(), new DirectScheduler(), coordinator, CLOCK);
    }

    private static UUID insertPlayer() throws Exception {
        UUID playerId = UUID.randomUUID();
        try (Connection connection = PostgresContainer.openConnection();
                Statement statement = connection.createStatement()) {
            statement.execute(
                    "INSERT INTO rpg.player_state (player_id) VALUES ('" + playerId + "')");
        }
        return playerId;
    }

    /** Ein Charakter je Klasse und Spieler - die Datenbank laesst keinen zweiten Magier zu. */
    private static UUID insertCharacter(UUID playerId) throws Exception {
        return insertCharacter(playerId, "MAGE");
    }

    private static UUID insertCharacter(UUID playerId, String characterClass) throws Exception {
        UUID characterId = UUID.randomUUID();
        try (Connection connection = PostgresContainer.openConnection();
                Statement statement = connection.createStatement()) {
            statement.execute(
                    "INSERT INTO rpg.character (character_id, player_id, character_class)"
                            + " VALUES ('"
                            + characterId
                            + "', '"
                            + playerId
                            + "', '"
                            + characterClass
                            + "')");
        }
        return characterId;
    }

    private static void deleteCharacter(UUID characterId) throws Exception {
        try (Connection connection = PostgresContainer.openConnection();
                Statement statement = connection.createStatement()) {
            statement.execute(
                    "DELETE FROM rpg.character WHERE character_id = '" + characterId + "'");
        }
    }

    private static int countRows(String table, UUID characterId) throws Exception {
        String query =
                "SELECT count(*) FROM " + table + " WHERE character_id = '" + characterId + "'";
        try (Connection connection = PostgresContainer.openConnection();
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(query)) {
            rows.next();
            return rows.getInt(1);
        }
    }

    private static ConnectionPools pools() {
        return new ConnectionPools(
                new PersistenceConfig(
                        PostgresContainer.host(),
                        PostgresContainer.port(),
                        "vuntex_test",
                        PostgresContainer.username(),
                        PostgresContainer.password(),
                        2,
                        1,
                        Duration.ofSeconds(45),
                        1_000,
                        Duration.ofSeconds(8)),
                QUIET);
    }

    /** Sammelt Markierungen, damit der Test den Writer selbst antreibt. */
    private static final class RecordingCoordinator implements WriteBehindCoordinator {

        private final List<DirtyMark> marks = new CopyOnWriteArrayList<>();

        @Override
        public void markDirty(AggregateType type, String aggregateId) {
            marks.add(new DirtyMark(type, aggregateId, CLOCK.instant()));
        }

        @Override
        public CompletableFuture<FlushResult> flushNow(FlushReason reason) {
            throw new UnsupportedOperationException("der Test treibt den Writer selbst an");
        }

        @Override
        public BufferStatus bufferStatus() {
            throw new UnsupportedOperationException("nicht Gegenstand dieses Tests");
        }

        List<DirtyMark> drain() {
            List<DirtyMark> snapshot = List.copyOf(marks);
            marks.clear();
            return snapshot;
        }
    }
}
