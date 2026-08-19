package rpg.persistence.support;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import org.testcontainers.containers.PostgreSQLContainer;

/**
 * One PostgreSQL container for the whole test suite.
 *
 * <p>Constitution VII requires persistence to be tested against a real database rather than mocks,
 * and this is that database. Two deliberate choices:
 *
 * <ul>
 *   <li><strong>Singleton, not per-class.</strong> Starting a container per test class costs
 *       seconds each; one container started on first use and left to the JVM's exit costs it once.
 *   <li><strong>No {@code @Testcontainers} extension.</strong> That extension is built against
 *       JUnit 5 while this project runs Jupiter 6, and its POM declares no JUnit version, so
 *       compatibility cannot be established from metadata. An incompatibility there would most
 *       likely surface as a <em>skipped</em> test rather than a failing one - which looks like
 *       coverage and is not, exactly as MockBukkit did in B01. Managing the container by hand needs
 *       only {@code org.testcontainers:postgresql}, which has no JUnit dependency at all, and
 *       sidesteps the question.
 * </ul>
 *
 * <p>No explicit shutdown: Testcontainers' Ryuk companion removes the container when the JVM ends.
 */
public final class PostgresContainer {

    private static final String IMAGE = "postgres:18-alpine";

    private static PostgreSQLContainer<?> container;

    private PostgresContainer() {}

    /** The running container, started on first use. */
    public static synchronized PostgreSQLContainer<?> get() {
        if (container == null) {
            PostgreSQLContainer<?> started =
                    new PostgreSQLContainer<>(IMAGE)
                            .withDatabaseName("vuntex_test")
                            .withUsername("rpg_test")
                            .withPassword("rpg_test");
            started.start();
            container = started;
        }
        return container;
    }

    public static String jdbcUrl() {
        return get().getJdbcUrl();
    }

    public static String username() {
        return get().getUsername();
    }

    public static String password() {
        return get().getPassword();
    }

    /** Host the container is reachable on from the test JVM. */
    public static String host() {
        return get().getHost();
    }

    /** Mapped port the container is reachable on from the test JVM. */
    public static int port() {
        return get().getFirstMappedPort();
    }

    /**
     * Drops and recreates the {@code rpg} schema.
     *
     * <p>Called between tests that need a clean slate. Recreating the schema is both faster than a
     * new container and stricter than deleting rows, because it also removes anything a previous
     * migration created.
     */
    public static void resetSchema() {
        try (Connection connection = openConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS rpg CASCADE");
            statement.execute("DROP TABLE IF EXISTS flyway_schema_history");
        } catch (SQLException failure) {
            throw new IllegalStateException("could not reset the test schema", failure);
        }
    }

    /** A plain JDBC connection to the container, for test setup and assertions. */
    public static Connection openConnection() throws SQLException {
        return java.sql.DriverManager.getConnection(jdbcUrl(), username(), password());
    }

    /**
     * Whether a table exists in the {@code rpg} schema.
     *
     * <p>Offered here rather than left to each caller so that tests in other modules can assert
     * against the schema without importing {@code java.sql} - which the static check in this module
     * forbids outside it, and rightly so: the exemption would otherwise have to cover every test
     * source in the project.
     */
    public static boolean tableExists(String table) {
        try (Connection connection = openConnection();
                Statement statement = connection.createStatement();
                java.sql.ResultSet rows =
                        statement.executeQuery(
                                "SELECT to_regclass('rpg." + table + "') IS NOT NULL")) {
            return rows.next() && rows.getBoolean(1);
        } catch (SQLException failure) {
            throw new IllegalStateException("could not check for table " + table, failure);
        }
    }

    /** Stops the container. Only for tests that deliberately simulate an outage. */
    public static synchronized void stop() {
        if (container != null) {
            container.stop();
            container = null;
        }
    }
}
