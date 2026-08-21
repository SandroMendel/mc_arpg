package rpg.persistence;

import java.util.Objects;
import java.util.logging.Logger;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import rpg.core.persistence.PersistenceConfig;
import rpg.core.persistence.PersistenceStartupException;

/**
 * Two separate connection pools: one for writing, one for logging in.
 *
 * <p>FR-008 requires the login path to <em>never</em> wait for a connection. With a shared pool
 * that is a hope whose truth depends on how long a batch happens to take - a large autosave can
 * occupy every connection, and the next login queues behind it. Two pools make the requirement
 * structurally true instead of statistically likely: write load cannot starve logins, because it
 * cannot reach their connections at all.
 *
 * <p>The pools are small on purpose. PostgreSQL runs on the same machine as the server (ADR-002)
 * and competes for the same cores, so every additional busy connection is taken directly out of the
 * tick budget. Twelve connections in total sits inside the usual
 * {@code (cores × 2) + spindles} guidance for the 6-8 core target profile.
 *
 * <p>No {@code DataSource} escapes this class to other modules - the separation would be pointless
 * if any block could take connections of its own.
 */
public final class ConnectionPools implements AutoCloseable {

    /**
     * The driver is named explicitly instead of being left to {@link java.sql.DriverManager}.
     *
     * <p>Paper resolves the driver from the {@code libraries:} section of plugin.yml into a
     * classloader of its own (ADR-010). {@code DriverManager} scans for drivers exactly once,
     * through the system classloader, long before that classloader exists - so the driver never
     * registers itself there. Hikari's fallback is {@code DriverManager.getDriver(url)}, which then
     * fails with "No suitable driver" although the jar is present and Hikari itself loaded from the
     * very classloader that holds it. Naming the class makes Hikari load it through that
     * classloader instead of asking a registry that cannot see it.
     *
     * <p>Deliberately not configurable: the project is PostgreSQL-only, and a wrong value must fail
     * the start rather than silently pick something else.
     */
    static final String DRIVER_CLASS = "org.postgresql.Driver";

    private final HikariDataSource writePool;
    private final HikariDataSource loginPool;
    private final Logger logger;

    public ConnectionPools(PersistenceConfig config, Logger logger) {
        Objects.requireNonNull(config, "config");
        this.logger = Objects.requireNonNull(logger, "logger");
        try {
            this.writePool = createPool(config, "rpg-write", config.writePoolSize());
            this.loginPool = createPool(config, "rpg-login", config.loginPoolSize());
        } catch (RuntimeException failure) {
            // The message must name what an operator needs to fix - and never the password
            // (FR-022).
            throw new PersistenceStartupException(
                    "could not open database connections ("
                            + config.describeWithoutSecrets()
                            + "): "
                            + failure.getMessage(),
                    failure);
        }
        logger.info(
                "[persistence] pools ready - write="
                        + config.writePoolSize()
                        + " login="
                        + config.loginPoolSize()
                        + " total="
                        + config.totalPoolSize());
    }

    /** Batches, the shutdown flush and statistics writes. */
    public DataSource writePool() {
        return writePool;
    }

    /** Loading player state on join - deliberately isolated from write load (FR-008). */
    public DataSource loginPool() {
        return loginPool;
    }

    @Override
    public void close() {
        // Called only after the final flush; closing earlier would make that flush impossible.
        closeQuietly(loginPool, "rpg-login");
        closeQuietly(writePool, "rpg-write");
    }

    private void closeQuietly(HikariDataSource pool, String name) {
        try {
            pool.close();
        } catch (RuntimeException failure) {
            logger.warning("[persistence] closing pool " + name + " failed: " + failure);
        }
    }

    private static HikariDataSource createPool(
            PersistenceConfig config, String poolName, int size) {
        return new HikariDataSource(poolConfig(config, poolName, size));
    }

    /**
     * Visible for testing: the pool settings can be asserted without opening a connection, which is
     * the only way to guard the driver decision above in a unit test. On the test classpath the
     * driver sits on the system classloader, so a test that actually connects would pass either
     * way - and did.
     */
    static HikariConfig poolConfig(PersistenceConfig config, String poolName, int size) {
        HikariConfig hikari = new HikariConfig();
        hikari.setPoolName(poolName);
        hikari.setJdbcUrl(config.jdbcUrl());
        hikari.setDriverClassName(DRIVER_CLASS);
        hikari.setUsername(config.user());
        hikari.setPassword(config.password());
        hikari.setMaximumPoolSize(size);
        // Keep the pool warm: opening a connection on the login path is exactly the latency FR-008
        // is about.
        hikari.setMinimumIdle(size);
        // Fail fast rather than queue forever - a blocked caller here is a bug we want to see.
        hikari.setConnectionTimeout(5_000L);
        hikari.setInitializationFailTimeout(10_000L);
        hikari.setAutoCommit(false); // writes are batched inside explicit transactions
        return hikari;
    }
}
