package rpg.persistence;

import java.time.Duration;
import java.util.Objects;
import java.util.logging.Logger;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.output.MigrateResult;

import rpg.core.persistence.PersistenceStartupException;

/**
 * Brings the schema to the version this build expects, or refuses to start (FR-012 to FR-014).
 *
 * <p>Runs before any repository is published: no block may touch a database whose schema state is
 * unconfirmed.
 *
 * <p>Checksum validation is left on deliberately. A migration file that was edited after it shipped
 * is the failure mode nobody notices until two servers on the same version disagree about their
 * schema - so it fails the start instead.
 */
public final class SchemaMigrator {

    private static final String SCHEMA = "rpg";
    private static final String MIGRATION_LOCATION = "classpath:db/migration";

    private final DataSource dataSource;
    private final Logger logger;

    public SchemaMigrator(DataSource dataSource, Logger logger) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * Applies every migration not yet applied.
     *
     * @return what was applied and how long it took
     * @throws PersistenceStartupException if a migration fails or a checksum no longer matches
     */
    public MigrationOutcome migrateToLatest() {
        long startedAt = System.nanoTime();
        try {
            Flyway flyway =
                    Flyway.configure(SchemaMigrator.class.getClassLoader())
                            .dataSource(dataSource)
                            .locations(MIGRATION_LOCATION)
                            .schemas(SCHEMA)
                            .defaultSchema(SCHEMA)
                            .createSchemas(true)
                            // An existing, non-empty database must be adoptable without wiping it.
                            .baselineOnMigrate(false)
                            // Detect a migration file edited after release (FR-013).
                            .validateOnMigrate(true)
                            // Never silently repair or clean - both would destroy player data.
                            .cleanDisabled(true)
                            .load();

            MigrateResult result = flyway.migrate();
            Duration took = Duration.ofNanos(System.nanoTime() - startedAt);
            String version =
                    result.targetSchemaVersion == null ? "<none>" : result.targetSchemaVersion;

            logger.info(
                    "[persistence] schema at version "
                            + version
                            + " - "
                            + result.migrationsExecuted
                            + " migration(s) applied in "
                            + took.toMillis()
                            + "ms");
            return new MigrationOutcome(result.migrationsExecuted, version, took);
        } catch (FlywayException failure) {
            throw new PersistenceStartupException(
                    "schema migration failed - refusing to run against an unconfirmed schema: "
                            + failure.getMessage(),
                    failure);
        }
    }

    /**
     * What a migration run did.
     *
     * @param applied number of migrations executed; zero when the schema was already current
     * @param schemaVersion the version reached
     * @param took wall-clock duration
     */
    public record MigrationOutcome(int applied, String schemaVersion, Duration took) {}
}
