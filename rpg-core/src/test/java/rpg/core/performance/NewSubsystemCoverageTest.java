package rpg.core.performance;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class NewSubsystemCoverageTest {

    @Test
    void aNewRegistrationAppearsInTheCentralSnapshotAndReportWithoutSpecialWiring() {
        DefaultPerformanceRegistry registry =
                new DefaultPerformanceRegistry(() -> 1_000L, 8, true);
        SubsystemId newSubsystem = new SubsystemId("b15-new-subsystem");
        registry.register(newSubsystem, Duration.ofMillis(5), "B15");
        registry.record(newSubsystem, 2_000_000L);

        PerformanceSnapshot snapshot = registry.snapshot();
        PerformanceReport report =
                PerformanceReport.fromSnapshot(
                        Instant.ofEpochSecond(1_700_000_000L), snapshot, 19.5d, 40.0d, 50.0d, 0L, 0L);

        assertThat(snapshot.subsystems()).containsKey(newSubsystem);
        assertThat(report.snapshot().subsystems()).containsKey(newSubsystem);
        assertThat(report.alertState(newSubsystem)).isEqualTo(AlertState.NORMAL);
    }
}
