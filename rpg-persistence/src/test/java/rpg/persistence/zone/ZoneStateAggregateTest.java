package rpg.persistence.zone;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.zone.ZoneCharacterState;
import rpg.core.persistence.PersistenceConfig;
import rpg.persistence.ConnectionPools;
import rpg.persistence.SchemaMigrator;
import rpg.persistence.support.PostgresContainer;

/**
 * T077 - B09's two tables against a real PostgreSQL (Constitution VII.2).
 *
 * <p><b>Against the real database and not a mock</b>, because every promise tested here is the
 * schema's rather than the code's: the cascade, the composite key, and the difference between a
 * missing row and an empty set. A mock would answer whatever the test set it up to answer, and the
 * one thing worth knowing - that {@code ON DELETE CASCADE} really fires - would be assumed rather
 * than shown.
 */
class ZoneStateAggregateTest {

    private static final Logger QUIET = Logger.getLogger("zone-state-aggregate-test");

    @BeforeEach
    void freshSchema() throws Exception {
        PostgresContainer.resetSchema();
        migrate();
    }

    @Test
    @DisplayName("a character this block has never placed reads as empty, not as an empty set")
    void neverSeenReadsEmpty() throws Exception {
        UUID characterId = insertCharacter();

        try (Connection connection = PostgresContainer.openConnection()) {
            assertThat(JdbcZoneStateRepository.read(connection, characterId))
                    .as("empty means never seen - that is what decides the start region (FR-037b)")
                    .isEmpty();
        }
    }

    @Test
    @DisplayName("a placed character with nothing discovered reads as present and empty")
    void placedButEmptyReadsPresent() throws Exception {
        UUID characterId = insertCharacter();
        writeState(characterId, null);

        try (Connection connection = PostgresContainer.openConnection()) {
            Optional<ZoneCharacterState> state =
                    JdbcZoneStateRepository.read(connection, characterId);

            assertThat(state).isPresent();
            assertThat(state.orElseThrow().unlockedCrystals()).isEmpty();
            assertThat(state.orElseThrow().pendingRespawnZone()).isEmpty();
        }
    }

    @Test
    @DisplayName("unlocks and a pending respawn survive a reconnect (SC-019)")
    void stateSurvivesAReconnect() throws Exception {
        UUID characterId = insertCharacter();
        writeState(characterId, "dustlands");
        writeUnlock(characterId, "greenfields-crystal");
        writeUnlock(characterId, "dustlands-crystal");

        // A new connection is what a restart looks like from here: nothing in memory, everything
        // read back from the row.
        try (Connection connection = PostgresContainer.openConnection()) {
            ZoneCharacterState state =
                    JdbcZoneStateRepository.read(connection, characterId).orElseThrow();

            assertThat(state.unlockedCrystals())
                    .containsExactlyInAnyOrder("greenfields-crystal", "dustlands-crystal");
            assertThat(state.pendingRespawnZone()).contains("dustlands");
        }
    }

    @Test
    @DisplayName("the same crystal twice is not an error and not a second row (FR-051b2)")
    void unlockingTwiceIsIdempotent() throws Exception {
        UUID characterId = insertCharacter();
        writeState(characterId, null);
        writeUnlock(characterId, "greenfields-crystal");
        writeUnlock(characterId, "greenfields-crystal");

        assertThat(countWaypoints(characterId)).isEqualTo(1);
    }

    @Test
    @DisplayName("deleting the character takes both tables with it (FR-051b1, SC-027)")
    void deletingTheCharacterCascades() throws Exception {
        UUID characterId = insertCharacter();
        writeState(characterId, "pale-wilds");
        writeUnlock(characterId, "pale-wilds-crystal");
        assertThat(countWaypoints(characterId)).isEqualTo(1);

        try (Connection connection = PostgresContainer.openConnection();
                PreparedStatement statement =
                        connection.prepareStatement(
                                "DELETE FROM rpg.character WHERE character_id = ?")) {
            statement.setObject(1, characterId);
            statement.executeUpdate();
        }

        assertThat(countWaypoints(characterId))
                .as("the unlocks go with the character - no code path had to know about it")
                .isZero();
        try (Connection connection = PostgresContainer.openConnection()) {
            assertThat(JdbcZoneStateRepository.read(connection, characterId)).isEmpty();
        }
    }

    @Test
    @DisplayName("two characters keep separate unlocks (ADR-011, SC-019)")
    void twoCharactersAreSeparate() throws Exception {
        UUID first = insertCharacter();
        UUID second = insertCharacter();
        writeState(first, null);
        writeState(second, null);
        writeUnlock(first, "greenfields-crystal");

        try (Connection connection = PostgresContainer.openConnection()) {
            assertThat(
                            JdbcZoneStateRepository.read(connection, second)
                                    .orElseThrow()
                                    .unlockedCrystals())
                    .as("nothing is inherited within an account")
                    .isEmpty();
            assertThat(
                            JdbcZoneStateRepository.read(connection, first)
                                    .orElseThrow()
                                    .unlockedCrystals())
                    .isEqualTo(Set.of("greenfields-crystal"));
        }
    }

    @Test
    @DisplayName("an unlock for a crystal that left the configuration is kept, not cleaned up")
    void anOrphanedUnlockSurvives() throws Exception {
        UUID characterId = insertCharacter();
        writeState(characterId, null);
        // No foreign key onto a crystal on purpose (FR-051b): a key can disappear from zones.yml
        // while an operator reworks the map, and the discovery has to outlive that.
        writeUnlock(characterId, "a-crystal-that-no-longer-exists");

        try (Connection connection = PostgresContainer.openConnection()) {
            assertThat(
                            JdbcZoneStateRepository.read(connection, characterId)
                                    .orElseThrow()
                                    .unlockedCrystals())
                    .containsExactly("a-crystal-that-no-longer-exists");
        }
    }

    // --- helpers ---------------------------------------------------------

    private static void migrate() {
        try (ConnectionPools pools = pools()) {
            new SchemaMigrator(pools.writePool(), QUIET).migrateToLatest();
        } catch (Exception failure) {
            throw new IllegalStateException("migration failed", failure);
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

    private static UUID insertCharacter() throws Exception {
        UUID playerId = UUID.randomUUID();
        UUID characterId = UUID.randomUUID();
        try (Connection connection = PostgresContainer.openConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO rpg.player_state (player_id) VALUES ('" + playerId + "')");
            statement.execute(
                    "INSERT INTO rpg.character (character_id, player_id, character_class)"
                            + " VALUES ('"
                            + characterId
                            + "', '"
                            + playerId
                            + "', 'MAGE')");
        }
        return characterId;
    }

    private static void writeState(UUID characterId, String pendingZone) throws Exception {
        try (Connection connection = PostgresContainer.openConnection();
                PreparedStatement statement =
                        connection.prepareStatement(
                                "INSERT INTO rpg.character_zone_state"
                                        + " (character_id, pending_respawn_zone, data_version,"
                                        + " revision, updated_at) VALUES (?, ?, 1, 0, ?)"
                                        + " ON CONFLICT (character_id) DO UPDATE SET"
                                        + " pending_respawn_zone = excluded.pending_respawn_zone")) {
            statement.setObject(1, characterId);
            statement.setString(2, pendingZone);
            statement.setTimestamp(3, Timestamp.from(Instant.now()));
            statement.executeUpdate();
        }
    }

    private static void writeUnlock(UUID characterId, String crystalKey) throws Exception {
        try (Connection connection = PostgresContainer.openConnection();
                PreparedStatement statement =
                        connection.prepareStatement(
                                "INSERT INTO rpg.character_waypoints"
                                        + " (character_id, crystal_key, unlocked_at)"
                                        + " VALUES (?, ?, ?)"
                                        + " ON CONFLICT (character_id, crystal_key) DO NOTHING")) {
            statement.setObject(1, characterId);
            statement.setString(2, crystalKey);
            statement.setTimestamp(3, Timestamp.from(Instant.now()));
            statement.executeUpdate();
        }
    }

    private static int countWaypoints(UUID characterId) throws Exception {
        try (Connection connection = PostgresContainer.openConnection();
                PreparedStatement statement =
                        connection.prepareStatement(
                                "SELECT count(*) FROM rpg.character_waypoints"
                                        + " WHERE character_id = ?")) {
            statement.setObject(1, characterId);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getInt(1);
            }
        }
    }
}
