package rpg.core.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.time.Duration;

import org.junit.jupiter.api.Test;

/**
 * T022 / FR-022: configuration is validated at startup, and the two bounds that are correctness
 * constraints rather than preferences are enforced rather than trusted.
 */
class PersistenceConfigValidationTest {

    private static PersistenceConfig valid() {
        return new PersistenceConfig(
                "localhost",
                5432,
                "vuntex",
                "rpg",
                "secret",
                8,
                4,
                Duration.ofSeconds(45),
                50_000,
                Duration.ofSeconds(8));
    }

    private static PersistenceConfig with(Duration autosave, Duration shutdownFlush) {
        PersistenceConfig base = valid();
        return new PersistenceConfig(
                base.host(),
                base.port(),
                base.database(),
                base.user(),
                base.password(),
                base.writePoolSize(),
                base.loginPoolSize(),
                autosave,
                base.bufferCapacity(),
                shutdownFlush);
    }

    @Test
    void aValidConfigurationIsAccepted() {
        assertThatCode(PersistenceConfigValidationTest::valid).doesNotThrowAnyException();
    }

    @Test
    void aShutdownFlushBeyondEightSecondsIsRejected() {
        // The scenario this guards: B01 force-terminates a module after 10s. A 20s flush budget
        // would let it be cut off mid-write, losing data during an orderly shutdown - the exact
        // thing SC-002 forbids. A misconfiguration must not be able to cause that.
        IllegalArgumentException thrown =
                catchThrowableOfType(
                        IllegalArgumentException.class,
                        () -> with(Duration.ofSeconds(45), Duration.ofSeconds(20)));

        assertThat(thrown).isNotNull();
        assertThat(thrown)
                .hasMessageContaining("shutdown-flush-seconds")
                .hasMessageContaining("8")
                .hasMessageContaining("10"); // names B01's budget so the reason is obvious
    }

    @Test
    void anAutosaveBelowThirtySecondsIsRejected() {
        IllegalArgumentException thrown =
                catchThrowableOfType(
                        IllegalArgumentException.class,
                        () -> with(Duration.ofSeconds(5), Duration.ofSeconds(8)));

        assertThat(thrown).hasMessageContaining("autosave-seconds").hasMessageContaining("30");
    }

    @Test
    void anAutosaveAboveSixtySecondsIsRejected() {
        assertThat(
                        catchThrowableOfType(
                                IllegalArgumentException.class,
                                () -> with(Duration.ofSeconds(120), Duration.ofSeconds(8))))
                .hasMessageContaining("60");
    }

    @Test
    void theBoundaryValuesThemselvesAreAccepted() {
        assertThatCode(() -> with(Duration.ofSeconds(30), Duration.ofSeconds(1)))
                .doesNotThrowAnyException();
        assertThatCode(() -> with(Duration.ofSeconds(60), Duration.ofSeconds(8)))
                .doesNotThrowAnyException();
    }

    @Test
    void aZeroShutdownFlushIsRejected() {
        assertThat(
                        catchThrowableOfType(
                                IllegalArgumentException.class,
                                () -> with(Duration.ofSeconds(45), Duration.ZERO)))
                .isNotNull();
    }

    @Test
    void blankOrMissingConnectionDetailsAreRejected() {
        assertThat(
                        catchThrowableOfType(
                                IllegalArgumentException.class,
                                () ->
                                        new PersistenceConfig(
                                                "   ",
                                                5432,
                                                "vuntex",
                                                "rpg",
                                                "secret",
                                                8,
                                                4,
                                                Duration.ofSeconds(45),
                                                50_000,
                                                Duration.ofSeconds(8))))
                .hasMessageContaining("persistence.host");
    }

    @Test
    void anInvalidPortIsRejected() {
        assertThat(
                        catchThrowableOfType(
                                IllegalArgumentException.class,
                                () ->
                                        new PersistenceConfig(
                                                "localhost",
                                                70_000,
                                                "vuntex",
                                                "rpg",
                                                "secret",
                                                8,
                                                4,
                                                Duration.ofSeconds(45),
                                                50_000,
                                                Duration.ofSeconds(8))))
                .hasMessageContaining("persistence.port");
    }

    @Test
    void theSchemaDeclaresEveryDocumentedKey() {
        assertThat(PersistenceConfig.schema().fields())
                .extracting(field -> field.path())
                .containsExactlyInAnyOrder(
                        "persistence.host",
                        "persistence.port",
                        "persistence.database",
                        "persistence.user",
                        "persistence.password",
                        "persistence.pool.write-size",
                        "persistence.pool.login-size",
                        "persistence.autosave-seconds",
                        "persistence.buffer-capacity",
                        "persistence.shutdown-flush-seconds");
    }

    @Test
    void theLoggableDescriptionNeverContainsThePassword() {
        // FR-022: credentials come from configuration and must not leak into any log line.
        assertThat(valid().describeWithoutSecrets()).doesNotContain("secret");
        assertThat(valid().jdbcUrl()).doesNotContain("secret").contains("localhost", "vuntex");
    }
}
