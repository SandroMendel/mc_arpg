package rpg.persistence.stats;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.persistence.AggregateType;
import rpg.core.persistence.FlushReason;
import rpg.core.persistence.PlayerState;
import rpg.core.stats.CharacterResources;
import rpg.core.stats.ResourcePool;
import rpg.persistence.support.PersistenceHarness;
import rpg.persistence.support.PostgresContainer;

/**
 * T061, T062: resources survive a round trip, and a game event never reaches the database
 * (FR-028, SC-011, SC-012).
 *
 * <p>Runs against a real PostgreSQL through Testcontainers. Principle VII is explicit about that:
 * a mock here would happily agree with whatever the code does, including with an upsert that does
 * not actually update.
 */
class CharacterResourcesRoundTripTest {

    private PersistenceHarness harness;
    private JdbcCharacterResourcesRepository repository;

    /** Stands in for the engine: the live value the flush reads. */
    private final AtomicReference<ResourcePool> live = new AtomicReference<>();

    private UUID characterId;

    @BeforeEach
    void setUp() throws Exception {
        PostgresContainer.resetSchema();
        harness = new PersistenceHarness();

        UUID playerId = UUID.randomUUID();
        harness.playerStates.put(PlayerState.initial(playerId, Instant.now()));
        harness.flushCycle.flushNow(FlushReason.INTERVAL).get();
        characterId = insertCharacter(playerId);

        repository =
                new JdbcCharacterResourcesRepository(
                        harness.pools.loginPool(),
                        harness.scheduler,
                        harness.flushCycle,
                        Clock.systemUTC());
        repository.setLiveSource(id -> Optional.ofNullable(live.get()));
        harness.flushCycle.register(AggregateType.CHARACTER_STATS, repository);
    }

    @AfterEach
    void tearDown() {
        if (harness != null) {
            harness.close();
        }
    }

    @Test
    @DisplayName("a stored value comes back exactly as it went in")
    void roundTrip() throws Exception {
        live.set(new ResourcePool(437.5, 12.25));
        repository.markDirty(characterId);
        harness.flushCycle.flushNow(FlushReason.INTERVAL).get();

        Optional<CharacterResources> reloaded = repository.find(characterId).get();

        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().currentHealth()).isEqualTo(437.5);
        assertThat(reloaded.get().currentMana()).isEqualTo(12.25);
        assertThat(reloaded.get().characterId()).isEqualTo(characterId);
        assertThat(reloaded.get().dataVersion())
                .isEqualTo(CharacterResources.CURRENT_DATA_VERSION);
    }

    @Test
    @DisplayName("a second write updates rather than inserting, and moves the revision on")
    void secondWriteUpdates() throws Exception {
        live.set(new ResourcePool(100.0, 10.0));
        repository.markDirty(characterId);
        harness.flushCycle.flushNow(FlushReason.INTERVAL).get();

        live.set(new ResourcePool(55.0, 3.0));
        repository.markDirty(characterId);
        harness.flushCycle.flushNow(FlushReason.INTERVAL).get();

        assertThat(rowCount()).isEqualTo(1);
        CharacterResources reloaded = repository.find(characterId).get().orElseThrow();
        assertThat(reloaded.currentHealth()).isEqualTo(55.0);
        assertThat(reloaded.revision()).isPositive();
    }

    @Test
    @DisplayName("a character with no stored row reads as absent - which means new, not broken")
    void absentRowIsNormal() throws Exception {
        assertThat(repository.find(characterId).get()).isEmpty();
        assertThat(repository.find(UUID.randomUUID()).get()).isEmpty();
    }

    @Test
    @DisplayName("deleting the character removes its resources without B04 doing anything")
    void deletionCascades() throws Exception {
        live.set(new ResourcePool(200.0, 20.0));
        repository.markDirty(characterId);
        harness.flushCycle.flushNow(FlushReason.INTERVAL).get();
        assertThat(rowCount()).isEqualTo(1);

        try (Connection connection = PostgresContainer.openConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("DELETE FROM rpg.character WHERE character_id = '" + characterId + "'");
        }

        assertThat(rowCount()).isZero();
    }

    @Test
    @DisplayName("500 resource changes produce one write, not 500")
    void noWritePerEvent() throws Exception {
        // The whole point of the write-behind path: a player taking 500 hits in a fight causes one
        // row to be written on the next flush, not 500 round trips while the tick is running.
        for (int i = 0; i < 500; i++) {
            live.set(new ResourcePool(1000.0 - i, 50.0));
            repository.markDirty(characterId);
        }

        assertThat(harness.buffer.pending()).isEqualTo(1); // coalesced to a single mark

        harness.flushCycle.flushNow(FlushReason.INTERVAL).get();

        CharacterResources stored = repository.find(characterId).get().orElseThrow();
        // The last value made it...
        assertThat(stored.currentHealth()).isEqualTo(1000.0 - 499);
        // ...and the revision proves how it got there: one write, not 500. The revision counts
        // writes, so anything above 1 would mean the coalescing did not hold.
        assertThat(stored.revision()).isEqualTo(1L);
        assertThat(rowCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("a mark whose holder has gone is dropped rather than retried forever")
    void markWithoutLiveValueIsDropped() throws Exception {
        live.set(null);
        repository.markDirty(characterId);

        harness.flushCycle.flushNow(FlushReason.INTERVAL).get();

        assertThat(harness.buffer.pending()).isZero();
        assertThat(rowCount()).isZero();
    }

    // --- helpers ---------------------------------------------------------

    private static UUID insertCharacter(UUID playerId) throws Exception {
        UUID characterId = UUID.randomUUID();
        try (Connection connection = PostgresContainer.openConnection();
                PreparedStatement statement =
                        connection.prepareStatement(
                                "INSERT INTO rpg.character (character_id, player_id,"
                                        + " character_class) VALUES (?, ?, 'ROGUE')")) {
            statement.setObject(1, characterId);
            statement.setObject(2, playerId);
            statement.executeUpdate();
        }
        return characterId;
    }

    private int rowCount() throws Exception {
        try (Connection connection = PostgresContainer.openConnection();
                Statement statement = connection.createStatement();
                ResultSet rows =
                        statement.executeQuery(
                                "SELECT count(*) FROM rpg.character_stats WHERE character_id = '"
                                        + characterId
                                        + "'")) {
            rows.next();
            return rows.getInt(1);
        }
    }

}
