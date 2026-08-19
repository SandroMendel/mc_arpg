package rpg.core.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.time.Duration;

import org.junit.jupiter.api.Test;

/**
 * T004 / FR-006, FR-009: the two bounds that are correctness constraints are enforced rather than
 * trusted.
 */
class SessionConfigValidationTest {

    @Test
    void theDefaultsAreAccepted() {
        assertThatCode(SessionConfig::defaults).doesNotThrowAnyException();
        assertThat(SessionConfig.defaults().loadTimeout()).isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    void aLoadTimeoutBeyondFiveSecondsIsRejected() {
        // A hung login must not leave a player frozen for longer than being refused would cost.
        IllegalArgumentException thrown =
                catchThrowableOfType(
                        IllegalArgumentException.class,
                        () ->
                                new SessionConfig(
                                        Duration.ofSeconds(20),
                                        Duration.ofSeconds(30),
                                        Duration.ofSeconds(30)));

        assertThat(thrown)
                .hasMessageContaining("load-timeout-seconds")
                .hasMessageContaining("5")
                .hasMessageContaining("500"); // names the target, so the distinction is visible
    }

    @Test
    void aReconcileIntervalBelowFiveSecondsIsRejected() {
        assertThat(
                        catchThrowableOfType(
                                IllegalArgumentException.class,
                                () ->
                                        new SessionConfig(
                                                Duration.ofSeconds(5),
                                                Duration.ofSeconds(1),
                                                Duration.ofSeconds(30))))
                .hasMessageContaining("reconcile-interval-seconds");
    }

    @Test
    void aZeroLoadTimeoutIsRejected() {
        assertThat(
                        catchThrowableOfType(
                                IllegalArgumentException.class,
                                () ->
                                        new SessionConfig(
                                                Duration.ZERO,
                                                Duration.ofSeconds(30),
                                                Duration.ofSeconds(30))))
                .isNotNull();
    }

    @Test
    void theBoundaryValuesThemselvesAreAccepted() {
        assertThatCode(
                        () ->
                                new SessionConfig(
                                        SessionConfig.MAX_LOAD_TIMEOUT,
                                        SessionConfig.MIN_RECONCILE_INTERVAL,
                                        Duration.ofSeconds(1)))
                .doesNotThrowAnyException();
    }

    @Test
    void theTimeoutIsTenTimesTheTargetSoTheDistinctionStaysVisible() {
        // Guards the reasoning, not just the number: if someone lowers the target or raises the
        // timeout, this states what the relationship was meant to be.
        assertThat(SessionConfig.MAX_LOAD_TIMEOUT)
                .isEqualTo(SessionConfig.LOAD_TARGET.multipliedBy(10));
    }

    @Test
    void theSchemaDeclaresEveryDocumentedKey() {
        assertThat(SessionConfig.schema().fields())
                .extracting(field -> field.path())
                .containsExactlyInAnyOrder(
                        "session.load-timeout-seconds",
                        "session.reconcile-interval-seconds",
                        "session.pending-expiry-seconds");
    }
}
