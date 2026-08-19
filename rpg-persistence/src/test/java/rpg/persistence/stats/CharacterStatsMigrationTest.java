package rpg.persistence.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.persistence.PersistenceConfig;
import rpg.persistence.ConnectionPools;
import rpg.persistence.SchemaMigrator;
import rpg.persistence.support.PostgresContainer;

/** T060: the B04 migration, against a real PostgreSQL rather than a mock (Principle VII). */
class CharacterStatsMigrationTest {

    private static final Logger QUIET = Logger.getLogger("character-stats-migration-test");

    @BeforeEach
    void setUp() {
        QUIET.setLevel(Level.OFF);
        PostgresContainer.resetSchema();
        migrate();
    }

    @Test
    @DisplayName("the table exists with its key, its foreign key and both checks")
    void tableShape() throws Exception {
        assertThat(tableExists("character_stats")).isTrue();

        assertThat(constraintType("character_stats_pkey")).isEqualTo("p");
        assertThat(constraintType("chk_character_stats_health")).isEqualTo("c");
        assertThat(constraintType("chk_character_stats_mana")).isEqualTo("c");

        // The foreign key exists and cascades - which is what makes anonymisation work without B02
        // knowing that B04 exists.
        try (Connection connection = PostgresContainer.openConnection();
                Statement statement = connection.createStatement();
                ResultSet rows =
                        statement.executeQuery(
                                "SELECT confdeltype FROM pg_constraint"
                                        + " WHERE conrelid = 'rpg.character_stats'::regclass"
                                        + " AND contype = 'f'")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getString(1)).isEqualTo("c"); // 'c' = ON DELETE CASCADE
        }
    }

    @Test
    @DisplayName("a negative resource cannot be stored, whatever the application thinks")
    void negativeValuesAreRefused() throws Exception {
        UUID characterId = insertCharacter();

        try (Connection connection = PostgresContainer.openConnection();
                Statement statement = connection.createStatement()) {
            assertThatThrownBy(
                            () ->
                                    statement.execute(
                                            "INSERT INTO rpg.character_stats"
                                                    + " (character_id, current_health, current_mana)"
                                                    + " VALUES ('"
                                                    + characterId
                                                    + "', -1, 0)"))
                    .isInstanceOf(SQLException.class);
        }
    }

    @Test
    @DisplayName("deleting a character takes its resources with it")
    void cascadeOnDelete() throws Exception {
        UUID characterId = insertCharacter();

        try (Connection connection = PostgresContainer.openConnection();
                Statement statement = connection.createStatement()) {
            statement.execute(
                    "INSERT INTO rpg.character_stats (character_id, current_health, current_mana)"
                            + " VALUES ('"
                            + characterId
                            + "', 500, 42)");
            assertThat(countStats(characterId)).isEqualTo(1);

            statement.execute("DELETE FROM rpg.character WHERE character_id = '" + characterId + "'");

            assertThat(countStats(characterId)).isZero();
        }
    }

    @Test
    @DisplayName("no orphan row can be created")
    void foreignKeyIsEnforced() throws Exception {
        try (Connection connection = PostgresContainer.openConnection();
                Statement statement = connection.createStatement()) {
            assertThatThrownBy(
                            () ->
                                    statement.execute(
                                            "INSERT INTO rpg.character_stats"
                                                    + " (character_id, current_health, current_mana)"
                                                    + " VALUES ('"
                                                    + UUID.randomUUID()
                                                    + "', 1, 1)"))
                    .isInstanceOf(SQLException.class);
        }
    }

    @Test
    @DisplayName("the version space stays ordered: 1 < 3.1 < 3.2 < 4.1")
    void versionSpaceOrdering() throws Exception {
        try (Connection connection = PostgresContainer.openConnection();
                Statement statement = connection.createStatement();
                ResultSet rows =
                        statement.executeQuery(
                                "SELECT version FROM rpg.flyway_schema_history"
                                        + " WHERE success = true AND version IS NOT NULL"
                                        + " ORDER BY installed_rank")) {
            java.util.List<String> versions = new java.util.ArrayList<>();
            while (rows.next()) {
                versions.add(rows.getString(1));
            }
            assertThat(versions).containsExactly("1", "3.1", "3.2", "4.1");
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

    private static int countStats(UUID characterId) throws Exception {
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

    private static boolean tableExists(String table) throws Exception {
        try (Connection connection = PostgresContainer.openConnection();
                Statement statement = connection.createStatement();
                ResultSet rows =
                        statement.executeQuery(
                                "SELECT to_regclass('rpg." + table + "') IS NOT NULL")) {
            rows.next();
            return rows.getBoolean(1);
        }
    }

    private static String constraintType(String name) throws Exception {
        try (Connection connection = PostgresContainer.openConnection();
                Statement statement = connection.createStatement();
                ResultSet rows =
                        statement.executeQuery(
                                "SELECT contype FROM pg_constraint WHERE conname = '" + name + "'")) {
            return rows.next() ? rows.getString(1) : null;
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
