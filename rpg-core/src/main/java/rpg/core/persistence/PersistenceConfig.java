package rpg.core.persistence;

import java.time.Duration;
import java.util.Objects;

import rpg.core.config.ConfigSchema;
import rpg.core.config.ConfigView;
import rpg.core.config.FieldDefinition;
import rpg.core.config.FieldType;

/**
 * Validated configuration of the persistence layer (FR-022).
 *
 * <p>Every bound is enforced here rather than trusted, because two of them are not preferences but
 * correctness constraints:
 *
 * <ul>
 *   <li>{@code autosave} within 30-60s is the window the non-functional requirements fix, and it is
 *       what SC-001 measures a crash against.
 *   <li>{@code shutdownFlush} at most 8s must stay inside the 10 second budget B01 grants each
 *       module before it force-terminates it. A larger value would let B01 cut the flush off
 *       mid-write - and losing data during an orderly shutdown is exactly what SC-002 forbids. A
 *       misconfiguration must not be able to cause that, so the cap is hard rather than advisory.
 * </ul>
 *
 * @param host database host
 * @param port database port
 * @param database database name
 * @param user database user
 * @param password database password - never logged (FR-022)
 * @param writePoolSize connections reserved for batches and the shutdown flush
 * @param loginPoolSize connections reserved for loading state on join; separate so write load
 *     cannot starve the login path (FR-008)
 * @param autosave interval between {@link FlushReason#INTERVAL} flushes
 * @param bufferCapacity upper bound on pending marks (FR-009a)
 * @param shutdownFlush budget for the final flush (FR-011, FR-011a)
 */
public record PersistenceConfig(
        String host,
        int port,
        String database,
        String user,
        String password,
        int writePoolSize,
        int loginPoolSize,
        Duration autosave,
        int bufferCapacity,
        Duration shutdownFlush) {

    /** B01 force-terminates a module after this long; the flush must finish inside it. */
    public static final Duration MODULE_SHUTDOWN_BUDGET = Duration.ofSeconds(10);

    /** Hard upper bound on {@link #shutdownFlush}, leaving room to log the outcome. */
    public static final Duration MAX_SHUTDOWN_FLUSH = Duration.ofSeconds(8);

    public static final Duration MIN_AUTOSAVE = Duration.ofSeconds(30);
    public static final Duration MAX_AUTOSAVE = Duration.ofSeconds(60);

    public PersistenceConfig {
        requireText(host, "persistence.host");
        requireText(database, "persistence.database");
        requireText(user, "persistence.user");
        Objects.requireNonNull(password, "persistence.password");
        Objects.requireNonNull(autosave, "persistence.autosave-seconds");
        Objects.requireNonNull(shutdownFlush, "persistence.shutdown-flush-seconds");

        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException(
                    "persistence.port must be between 1 and 65535, but was " + port);
        }
        if (writePoolSize < 1) {
            throw new IllegalArgumentException(
                    "persistence.pool.write-size must be at least 1, but was " + writePoolSize);
        }
        if (loginPoolSize < 1) {
            throw new IllegalArgumentException(
                    "persistence.pool.login-size must be at least 1, but was " + loginPoolSize);
        }
        if (bufferCapacity < 1) {
            throw new IllegalArgumentException(
                    "persistence.buffer-capacity must be at least 1, but was " + bufferCapacity);
        }
        if (autosave.compareTo(MIN_AUTOSAVE) < 0 || autosave.compareTo(MAX_AUTOSAVE) > 0) {
            throw new IllegalArgumentException(
                    "persistence.autosave-seconds must be between "
                            + MIN_AUTOSAVE.toSeconds()
                            + " and "
                            + MAX_AUTOSAVE.toSeconds()
                            + ", but was "
                            + autosave.toSeconds());
        }
        if (shutdownFlush.isNegative()
                || shutdownFlush.isZero()
                || shutdownFlush.compareTo(MAX_SHUTDOWN_FLUSH) > 0) {
            throw new IllegalArgumentException(
                    "persistence.shutdown-flush-seconds must be between 1 and "
                            + MAX_SHUTDOWN_FLUSH.toSeconds()
                            + " (B01 force-terminates a module after "
                            + MODULE_SHUTDOWN_BUDGET.toSeconds()
                            + "s, and the flush must finish inside that), but was "
                            + shutdownFlush.toSeconds());
        }
    }

    /** Total connections opened against the database. */
    public int totalPoolSize() {
        return writePoolSize + loginPoolSize;
    }

    /** JDBC URL derived from host, port and database. Contains no credentials. */
    public String jdbcUrl() {
        return "jdbc:postgresql://" + host + ":" + port + "/" + database;
    }

    /** Description safe to log: everything except the password (FR-022). */
    public String describeWithoutSecrets() {
        return "host="
                + host
                + " port="
                + port
                + " database="
                + database
                + " user="
                + user
                + " writePool="
                + writePoolSize
                + " loginPool="
                + loginPoolSize
                + " autosave="
                + autosave.toSeconds()
                + "s bufferCapacity="
                + bufferCapacity
                + " shutdownFlush="
                + shutdownFlush.toSeconds()
                + "s";
    }

    /** The schema this configuration is validated against at startup (FR-022). */
    public static ConfigSchema<PersistenceConfig> schema() {
        return ConfigSchema.<PersistenceConfig>builder(1)
                .required("persistence.host", FieldType.STRING)
                .field(
                        FieldDefinition.optional("persistence.port", FieldType.INTEGER, 5432)
                                .withRange(1, 65535))
                .required("persistence.database", FieldType.STRING)
                .required("persistence.user", FieldType.STRING)
                .required("persistence.password", FieldType.STRING)
                .field(
                        FieldDefinition.optional("persistence.pool.write-size", FieldType.INTEGER, 8)
                                .withRange(1, 64))
                .field(
                        FieldDefinition.optional("persistence.pool.login-size", FieldType.INTEGER, 4)
                                .withRange(1, 64))
                .field(
                        FieldDefinition.optional(
                                        "persistence.autosave-seconds", FieldType.INTEGER, 45)
                                .withRange(MIN_AUTOSAVE.toSeconds(), MAX_AUTOSAVE.toSeconds()))
                .field(
                        FieldDefinition.optional(
                                        "persistence.buffer-capacity", FieldType.INTEGER, 50_000)
                                .withRange(1, 10_000_000))
                .field(
                        FieldDefinition.optional(
                                        "persistence.shutdown-flush-seconds", FieldType.INTEGER, 8)
                                .withRange(1, MAX_SHUTDOWN_FLUSH.toSeconds()))
                .boundTo(PersistenceConfig::from)
                .build();
    }

    private static PersistenceConfig from(ConfigView view) {
        return new PersistenceConfig(
                view.getString("persistence.host"),
                view.getInt("persistence.port"),
                view.getString("persistence.database"),
                view.getString("persistence.user"),
                view.getString("persistence.password"),
                view.getInt("persistence.pool.write-size"),
                view.getInt("persistence.pool.login-size"),
                Duration.ofSeconds(view.getInt("persistence.autosave-seconds")),
                view.getInt("persistence.buffer-capacity"),
                Duration.ofSeconds(view.getInt("persistence.shutdown-flush-seconds")));
    }

    private static void requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
