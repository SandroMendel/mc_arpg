package rpg.core.session;

import java.time.Duration;
import java.util.Objects;

import rpg.core.config.ConfigSchema;
import rpg.core.config.ConfigView;
import rpg.core.config.FieldDefinition;
import rpg.core.config.FieldType;

/**
 * Validated configuration of the session layer (FR-006, FR-009).
 *
 * <p>Two of these bounds are correctness constraints rather than preferences, and are therefore
 * enforced rather than trusted:
 *
 * <ul>
 *   <li>{@code loadTimeout} at most 5 seconds. It is a last resort, not an expected load time - the
 *       target is 500 ms (SC-001). A larger value would leave a player standing motionless on the
 *       server for longer than a hung login is worth; being refused is recoverable, being frozen is
 *       not.
 *   <li>{@code reconcileInterval} at least 5 seconds. The reconciliation walks every session and
 *       compares it against the connected players; running it more often would spend tick budget on
 *       a safety net that has nothing to catch in normal operation.
 * </ul>
 *
 * @param loadTimeout how long a login may wait for its session before being refused (FR-006)
 * @param reconcileInterval how often orphaned sessions are swept (FR-009)
 * @param pendingExpiry how long a preloaded session waits to be collected before it expires
 */
public record SessionConfig(
        Duration loadTimeout, Duration reconcileInterval, Duration pendingExpiry) {

    /** Hard upper bound on {@link #loadTimeout} - ten times the 500 ms target from SC-001. */
    public static final Duration MAX_LOAD_TIMEOUT = Duration.ofSeconds(5);

    /** Lower bound on {@link #reconcileInterval}. */
    public static final Duration MIN_RECONCILE_INTERVAL = Duration.ofSeconds(5);

    /** The target this block is measured against (SC-001); not a timeout. */
    public static final Duration LOAD_TARGET = Duration.ofMillis(500);

    public SessionConfig {
        Objects.requireNonNull(loadTimeout, "session.load-timeout-seconds");
        Objects.requireNonNull(reconcileInterval, "session.reconcile-interval-seconds");
        Objects.requireNonNull(pendingExpiry, "session.pending-expiry-seconds");

        if (loadTimeout.isNegative() || loadTimeout.isZero()
                || loadTimeout.compareTo(MAX_LOAD_TIMEOUT) > 0) {
            throw new IllegalArgumentException(
                    "session.load-timeout-seconds must be between 1 and "
                            + MAX_LOAD_TIMEOUT.toSeconds()
                            + " (it is a last resort, not an expected load time - the target is "
                            + LOAD_TARGET.toMillis()
                            + "ms), but was "
                            + loadTimeout.toSeconds());
        }
        if (reconcileInterval.compareTo(MIN_RECONCILE_INTERVAL) < 0) {
            throw new IllegalArgumentException(
                    "session.reconcile-interval-seconds must be at least "
                            + MIN_RECONCILE_INTERVAL.toSeconds()
                            + ", but was "
                            + reconcileInterval.toSeconds());
        }
        if (pendingExpiry.isNegative() || pendingExpiry.isZero()) {
            throw new IllegalArgumentException("session.pending-expiry-seconds must be positive");
        }
    }

    /** Defaults matching the specification. */
    public static SessionConfig defaults() {
        return new SessionConfig(
                Duration.ofSeconds(5), Duration.ofSeconds(30), Duration.ofSeconds(30));
    }

    /** The schema this configuration is validated against at startup. */
    public static ConfigSchema<SessionConfig> schema() {
        return ConfigSchema.<SessionConfig>builder(1)
                .field(
                        FieldDefinition.optional("session.load-timeout-seconds", FieldType.INTEGER, 5)
                                .withRange(1, MAX_LOAD_TIMEOUT.toSeconds()))
                .field(
                        FieldDefinition.optional(
                                        "session.reconcile-interval-seconds", FieldType.INTEGER, 30)
                                .withRange(MIN_RECONCILE_INTERVAL.toSeconds(), 3600))
                .field(
                        FieldDefinition.optional(
                                        "session.pending-expiry-seconds", FieldType.INTEGER, 30)
                                .withRange(1, 300))
                .boundTo(SessionConfig::from)
                .build();
    }

    private static SessionConfig from(ConfigView view) {
        return new SessionConfig(
                Duration.ofSeconds(view.getInt("session.load-timeout-seconds")),
                Duration.ofSeconds(view.getInt("session.reconcile-interval-seconds")),
                Duration.ofSeconds(view.getInt("session.pending-expiry-seconds")));
    }
}
