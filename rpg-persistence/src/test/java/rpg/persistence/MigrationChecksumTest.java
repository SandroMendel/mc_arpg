package rpg.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.Statement;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import rpg.core.persistence.PersistenceConfig;
import rpg.core.persistence.PersistenceStartupException;
import rpg.persistence.support.PostgresContainer;

/**
 * T074 / FR-013: a migration file edited after it shipped stops the start.
 *
 * <p>Without this check two servers on the same version can end up with different schemas, and
 * nobody notices until something breaks far away from the cause.
 */
class MigrationChecksumTest {

    private static final Logger QUIET = Logger.getLogger("checksum-test");

    @BeforeEach
    void setUp() {
        QUIET.setLevel(Level.OFF);
        PostgresContainer.resetSchema();
    }

    @Test
    void aTamperedMigrationRecordAbortsTheStart() throws Exception {
        try (ConnectionPools pools = pools()) {
            new SchemaMigrator(pools.writePool(), QUIET).migrateToLatest();

            // Simulate the file having changed after it was applied, by corrupting the recorded
            // checksum - the same condition Flyway detects.
            try (Connection connection = pools.writePool().getConnection();
                    Statement statement = connection.createStatement()) {
                statement.execute(
                        "UPDATE rpg.flyway_schema_history SET checksum = 123456789"
                                + " WHERE version = '1'");
                connection.commit();
            }

            assertThatThrownBy(() -> new SchemaMigrator(pools.writePool(), QUIET).migrateToLatest())
                    .isInstanceOf(PersistenceStartupException.class)
                    .hasMessageContaining("migration");
        }
    }

    @Test
    void aCleanHistoryMigratesWithoutComplaint() throws Exception {
        try (ConnectionPools pools = pools()) {
            assertThat(new SchemaMigrator(pools.writePool(), QUIET).migrateToLatest().applied())
                    .isPositive();
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
                        java.time.Duration.ofSeconds(45),
                        1_000,
                        java.time.Duration.ofSeconds(8)),
                QUIET);
    }
}
