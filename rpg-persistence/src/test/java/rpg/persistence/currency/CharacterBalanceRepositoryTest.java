package rpg.persistence.currency;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.Statement;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.currency.CharacterBalance;
import rpg.core.persistence.AggregateType;
import rpg.core.persistence.DirtyMark;
import rpg.core.persistence.PersistenceConfig;
import rpg.core.persistence.WriteBehindCoordinator;
import rpg.persistence.ConnectionPools;
import rpg.persistence.SchemaMigrator;
import rpg.persistence.support.DirectScheduler;
import rpg.persistence.support.PostgresContainer;

/**
 * T031: {@code rpg.character_balance} gegen ein echtes PostgreSQL (Prinzip VII, SC-002).
 *
 * <p>Der Test, auf den es ankommt, ist {@code balanceSurvivesARestart}: er schreibt, wirft alles
 * weg, was im Speicher stand, und liest neu. Alles andere kann gruen sein und der Stand trotzdem
 * beim Abmelden verschwinden.
 */
class CharacterBalanceRepositoryTest {

    private static final Logger QUIET = Logger.getLogger("character-balance-repository-test");
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-08-22T12:00:00Z"), ZoneOffset.UTC);

    private ConnectionPools pools;
    private JdbcCharacterBalanceRepository repository;
    private RecordingCoordinator coordinator;

    @BeforeEach
    void setUp() {
        QUIET.setLevel(Level.OFF);
        PostgresContainer.resetSchema();
        pools = pools();
        new SchemaMigrator(pools.writePool(), QUIET).migrateToLatest();
        coordinator = new RecordingCoordinator();
        repository =
                new JdbcCharacterBalanceRepository(
                        pools.loginPool(), new DirectScheduler(), coordinator, CLOCK);
    }

    @Test
    @DisplayName("ein nie geschriebener Charakter liefert leer - und leer heisst null")
    void neverWrittenIsEmpty() throws Exception {
        UUID characterId = insertCharacter();

        assertThat(repository.find(characterId).get())
                .as("keine Zeile ist der Normalfall, kein Fehler")
                .isEmpty();
    }

    @Test
    @DisplayName("ein geschriebener Stand ueberlebt und wird unveraendert zurueckgelesen (SC-002)")
    void balanceSurvivesARestart() throws Exception {
        UUID characterId = insertCharacter();

        // Wie im Betrieb: der lebende Wert kommt aus der Regelschicht, der Flush holt ihn dort ab.
        repository.setLiveSource(id -> OptionalLong.of(1234L));
        repository.markDirty(characterId);
        List<DirtyMark> written =
                repository.write(pools.writePool(), coordinator.drain());
        assertThat(written).hasSize(1);

        // Alles wegwerfen, was im Speicher stand - genau das tut ein Neustart.
        JdbcCharacterBalanceRepository afterRestart =
                new JdbcCharacterBalanceRepository(
                        pools.loginPool(), new DirectScheduler(), new RecordingCoordinator(), CLOCK);

        Optional<CharacterBalance> reloaded = afterRestart.find(characterId).get();
        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().balance()).isEqualTo(1234L);
        assertThat(reloaded.get().characterId()).isEqualTo(characterId);
    }

    @Test
    @DisplayName("jeder Schreibvorgang erhoeht die Revision")
    void everyWriteRaisesTheRevision() throws Exception {
        UUID characterId = insertCharacter();
        repository.setLiveSource(id -> OptionalLong.of(10L));

        repository.markDirty(characterId);
        repository.write(pools.writePool(), coordinator.drain());
        long first = repository.find(characterId).get().orElseThrow().revision();

        repository.setLiveSource(id -> OptionalLong.of(20L));
        repository.markDirty(characterId);
        repository.write(pools.writePool(), coordinator.drain());
        long second = repository.find(characterId).get().orElseThrow().revision();

        assertThat(second).isGreaterThan(first);
    }

    @Test
    @DisplayName("eine Markierung ohne lebenden Wert wird verbraucht, nicht endlos wiederholt")
    void aMarkWithoutALiveValueIsConsumed() throws Exception {
        UUID characterId = insertCharacter();
        repository.setLiveSource(id -> OptionalLong.empty());

        repository.markDirty(characterId);
        List<DirtyMark> marks = coordinator.drain();

        assertThat(repository.write(pools.writePool(), marks))
                .as("sonst zaehlte die Markierung bei jedem Flush erneut als fehlgeschlagen")
                .hasSize(1);
    }

    @Test
    @DisplayName("markDirty meldet den richtigen Aggregattyp - Eintragung 1 und 2 von 3")
    void marksTheRightAggregateType() throws Exception {
        UUID characterId = insertCharacter();

        repository.markDirty(characterId);

        assertThat(coordinator.types).containsExactly(AggregateType.CHARACTER_BALANCE);
    }

    @Test
    @DisplayName("readForPlayer liefert die Staende aller Charaktere eines Spielers")
    void readForPlayerReturnsEveryCharacter() throws Exception {
        UUID playerId = UUID.randomUUID();
        UUID warrior = insertCharacter(playerId, "WARRIOR");
        UUID mage = insertCharacter(playerId, "MAGE");

        try (Connection connection = pools.writePool().getConnection()) {
            JdbcCharacterBalanceRepository.write(connection, warrior, 100L, CLOCK);
            JdbcCharacterBalanceRepository.write(connection, mage, 300L, CLOCK);

            List<CharacterBalance> balances =
                    JdbcCharacterBalanceRepository.readForPlayer(connection, playerId);

            assertThat(balances)
                    .as("der Login liest sie in einer Runde mit (FR-017)")
                    .hasSize(2)
                    .extracting(CharacterBalance::balance)
                    .containsExactlyInAnyOrder(100L, 300L);
        }
    }

    @Test
    @DisplayName("der direkte Schreibweg des Betreibers wirkt auf einen abgemeldeten Charakter")
    void theOperatorPathWritesDirectly() throws Exception {
        UUID characterId = insertCharacter();

        try (Connection connection = pools.writePool().getConnection()) {
            JdbcCharacterBalanceRepository.write(connection, characterId, 5000L, CLOCK);

            assertThat(JdbcCharacterBalanceRepository.read(connection, characterId))
                    .isPresent()
                    .get()
                    .extracting(CharacterBalance::balance)
                    .isEqualTo(5000L);
        }
    }

    // --- Hilfsmittel -----------------------------------------------------

    private UUID insertCharacter() throws Exception {
        return insertCharacter(UUID.randomUUID(), "WARRIOR");
    }

    private UUID insertCharacter(UUID playerId, String characterClass) throws Exception {
        UUID characterId = UUID.randomUUID();
        try (Connection connection = PostgresContainer.openConnection();
                Statement statement = connection.createStatement()) {
            statement.execute(
                    "INSERT INTO rpg.player_state (player_id) VALUES ('"
                            + playerId
                            + "') ON CONFLICT DO NOTHING");
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

    /** Sammelt Markierungen, damit der Test sie dem Writer selbst uebergeben kann. */
    private static final class RecordingCoordinator implements WriteBehindCoordinator {

        private final java.util.List<DirtyMark> marks = new java.util.concurrent.CopyOnWriteArrayList<>();
        final java.util.Set<AggregateType> types = ConcurrentHashMap.newKeySet();

        @Override
        public void markDirty(AggregateType type, String aggregateId) {
            types.add(type);
            marks.add(new DirtyMark(type, aggregateId, CLOCK.instant()));
        }

        @Override
        public java.util.concurrent.CompletableFuture<rpg.core.persistence.FlushResult> flushNow(
                rpg.core.persistence.FlushReason reason) {
            throw new UnsupportedOperationException("the test drives the writer itself");
        }

        @Override
        public rpg.core.persistence.BufferStatus bufferStatus() {
            throw new UnsupportedOperationException("not part of what this test is about");
        }

        List<DirtyMark> drain() {
            List<DirtyMark> snapshot = List.copyOf(marks);
            marks.clear();
            return snapshot;
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
}
