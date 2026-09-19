package rpg.core.performance;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class MissingSubsystemIsNotZeroTest {

    @Test
    void anExpectedButUnsampledSourceRemainsExplicitlyMissing() {
        DefaultPerformanceRegistry registry =
                new DefaultPerformanceRegistry(() -> 1_000L, 8, true);
        SubsystemId expected = new SubsystemId("b15-not-yet-sampled");
        registry.register(expected, Duration.ofMillis(5), "B15");

        PerformanceReport report =
                PerformanceReport.fromSnapshot(
                        Instant.ofEpochSecond(1_700_000_000L),
                        registry.snapshot(),
                        19.5d,
                        40.0d,
                        50.0d,
                        0L,
                        0L);

        PerformanceWindow window = report.snapshot().subsystems().get(expected).window();
        assertThat(window.status()).isEqualTo(PerformanceWindow.Status.EMPTY);
        assertThat(report.subsystemDurationSeconds(report.snapshot().subsystems().get(expected), "p95"))
                .isNaN();
    }
}
