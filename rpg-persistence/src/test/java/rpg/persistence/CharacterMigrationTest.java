package rpg.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import rpg.core.persistence.PersistenceConfig;
import rpg.persistence.SchemaMigrator.MigrationOutcome;
import rpg.persistence.support.PostgresContainer;

/**
 * T018: the B03 migration adds the character table without disturbing B02's, and the per-block
 * version space orders correctly.
 *
 * <p>The version space is the part worth testing rather than assuming: every block from here on
 * numbers its migrations {@code V{block}_{seq}}, and if Flyway did not read the underscore as a
 * separator, B03's migration would sort as version 31 and land after everything.
 */
class CharacterMigrationTest {

    private static final Logger QUIET = Logger.getLogger("character-migration-test");

    @BeforeEach
    void setUp() {
        QUIET.setLevel(Level.OFF);
        PostgresContainer.resetSchema();
    }

    @Test
    void theCharacterTableIsCreatedAlongsideB02sTables() throws Exception {
        try (ConnectionPools pools = pools()) {
            new SchemaMigrator(pools.writePool(), QUIET).migrateToLatest();

            assertThat(tableExists("character")).isTrue();
            // B02's tables are untouched.
            assertThat(tableExists("player_state")).isTrue();
            assertThat(tableExists("player_statistic_daily")).isTrue();
            assertThat(tableExists("item_instance")).isTrue();
            assertThat(tableExists("audit_log")).isTrue();
        }
    }

    @Test
    void aSecondRunAppliesNothing() throws Exception {
        try (ConnectionPools pools = pools()) {
            MigrationOutcome first = new SchemaMigrator(pools.writePool(), QUIET).migrateToLatest();
            MigrationOutcome second = new SchemaMigrator(pools.writePool(), QUIET).migrateToLatest();

            assertThat(first.applied()).isEqualTo(6); // V1, V3_1, V3_2, V4_1, V6_1, V7_1
            assertThat(second.applied()).isZero();
        }
    }

    @Test
    void theBlockVersionSpaceOrdersB03AfterB02() throws Exception {
        try (ConnectionPools pools = pools()) {
            new SchemaMigrator(pools.writePool(), QUIET).migrateToLatest();
        }

        // Flyway must have read V3_1 as version "3.1", not "31" - otherwise the per-block numbering
        // scheme silently breaks the moment a block numbers past 9.
        try (Connection connection = PostgresContainer.openConnection();
                Statement statement = connection.createStatement();
                ResultSet rows =
                        statement.executeQuery(
                                // version IS NOT NULL skips Flyway's own schema-creation marker,
                                // which is recorded without a version.
                                "SELECT version FROM rpg.flyway_schema_history"
                                        + " WHERE success = true AND version IS NOT NULL"
                                        + " ORDER BY installed_rank")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getString(1)).isEqualTo("1");
            assertThat(rows.next()).isTrue();
            assertThat(rows.getString(1)).isEqualTo("3.1");
            assertThat(rows.next()).isTrue();
            // Within a block the sequence counts up; 3.2 must follow 3.1, not precede it.
            assertThat(rows.getString(1)).isEqualTo("3.2");
            assertThat(rows.next()).isTrue();
            // And B04 lands after B03, which is the whole point of the per-block numbering.
            assertThat(rows.getString(1)).isEqualTo("4.1");
        }
    }

    @Test
    void theUniqueKeyRejectsASecondCharacterOfTheSameClass() throws Exception {
        try (ConnectionPools pools = pools()) {
            new SchemaMigrator(pools.writePool(), QUIET).migrateToLatest();
        }

        java.util.UUID playerId = java.util.UUID.randomUUID();
        try (Connection connection = PostgresContainer.openConnection();
                Statement statement = connection.createStatement()) {
            statement.execute(
                    "INSERT INTO rpg.player_state (player_id) VALUES ('" + playerId + "')");
            statement.execute(
                    "INSERT INTO rpg.character (character_id, player_id, character_class)"
                            + " VALUES ('"
                            + java.util.UUID.randomUUID()
                            + "', '"
                            + playerId
                            + "', 'WARRIOR')");

            // The database refuses it - not application code that a later block could bypass.
            org.assertj.core.api.Assertions.assertThatThrownBy(
                            () ->
                                    statement.execute(
                                            "INSERT INTO rpg.character"
                                                    + " (character_id, player_id, character_class)"
                                                    + " VALUES ('"
                                                    + java.util.UUID.randomUUID()
                                                    + "', '"
                                                    + playerId
                                                    + "', 'WARRIOR')"))
                    .isInstanceOf(java.sql.SQLException.class);
        }
    }

    @Test
    void anInvalidCharacterClassIsRejected() throws Exception {
        try (ConnectionPools pools = pools()) {
            new SchemaMigrator(pools.writePool(), QUIET).migrateToLatest();
        }

        java.util.UUID playerId = java.util.UUID.randomUUID();
        try (Connection connection = PostgresContainer.openConnection();
                Statement statement = connection.createStatement()) {
            statement.execute(
                    "INSERT INTO rpg.player_state (player_id) VALUES ('" + playerId + "')");

            org.assertj.core.api.Assertions.assertThatThrownBy(
                            () ->
                                    statement.execute(
                                            "INSERT INTO rpg.character"
                                                    + " (character_id, player_id, character_class)"
                                                    + " VALUES ('"
                                                    + java.util.UUID.randomUUID()
                                                    + "', '"
                                                    + playerId
                                                    + "', 'NECROMANCER')"))
                    .isInstanceOf(java.sql.SQLException.class);
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

    private static boolean tableExists(String table) throws Exception {
        try (Connection connection = PostgresContainer.openConnection();
                Statement statement = connection.createStatement();
                ResultSet rows =
                        statement.executeQuery(
                                "SELECT to_regclass('rpg." + table + "') IS NOT NULL")) {
            return rows.next() && rows.getBoolean(1);
        }
    }
}
