package rpg.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import rpg.core.persistence.FlushReason;
import rpg.core.persistence.PersistenceConfig;
import rpg.core.persistence.PlayerState;
import rpg.persistence.SchemaMigrator.MigrationOutcome;
import rpg.persistence.support.PersistenceHarness;
import rpg.persistence.support.PostgresContainer;

/**
 * T072 / T073 / SC-006: migrations run on an empty and on a populated database, and never twice.
 */
class MigrationIdempotencyTest {

    private static final Logger QUIET = Logger.getLogger("migration-test");

    @BeforeEach
    void setUp() {
        QUIET.setLevel(Level.OFF);
        PostgresContainer.resetSchema();
    }

    @Test
    void anEmptyDatabaseGetsTheFullSchema() throws Exception {
        try (ConnectionPools pools = pools()) {
            MigrationOutcome outcome = new SchemaMigrator(pools.writePool(), QUIET).migrateToLatest();

            assertThat(outcome.applied()).isPositive();
            assertThat(tableExists(pools.writePool(), "player_state")).isTrue();
            assertThat(tableExists(pools.writePool(), "player_statistic_daily")).isTrue();
            assertThat(tableExists(pools.writePool(), "item_instance")).isTrue();
            assertThat(tableExists(pools.writePool(), "audit_log")).isTrue();
        }
    }

    @Test
    void asecondRunAppliesNothing() throws Exception {
        try (ConnectionPools pools = pools()) {
            new SchemaMigrator(pools.writePool(), QUIET).migrateToLatest();
            MigrationOutcome second = new SchemaMigrator(pools.writePool(), QUIET).migrateToLatest();

            assertThat(second.applied()).isZero();
        }
    }

    @Test
    void apopulatedDatabaseKeepsItsRowsAcrossAMigrationRun() throws Exception {
        UUID playerId = UUID.randomUUID();

        // Populate through the shipping write path, then migrate again.
        try (PersistenceHarness harness = new PersistenceHarness()) {
            harness.playerStates.put(PlayerState.initial(playerId, Instant.now()));
            harness.flushCycle.flushNow(FlushReason.INTERVAL).get();
        }

        long before = countPlayers();
        try (ConnectionPools pools = pools()) {
            MigrationOutcome outcome = new SchemaMigrator(pools.writePool(), QUIET).migrateToLatest();
            assertThat(outcome.applied()).isZero();
        }

        // Row counts compared rather than eyeballed - the point of SC-006.
        assertThat(countPlayers()).isEqualTo(before).isEqualTo(1L);
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
                        java.time.Duration.ofSeconds(45),
                        1_000,
                        java.time.Duration.ofSeconds(8)),
                QUIET);
    }

    private static boolean tableExists(DataSource dataSource, String table) throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet rows =
                        statement.executeQuery(
                                "SELECT to_regclass('rpg." + table + "') IS NOT NULL")) {
            return rows.next() && rows.getBoolean(1);
        }
    }

    private static long countPlayers() throws Exception {
        try (Connection connection = PostgresContainer.openConnection();
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT count(*) FROM rpg.player_state")) {
            return rows.next() ? rows.getLong(1) : -1L;
        }
    }
}
