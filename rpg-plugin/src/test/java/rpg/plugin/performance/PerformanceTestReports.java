package rpg.plugin.performance;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import rpg.core.performance.AlertState;
import rpg.core.performance.AlertTransition;
import rpg.core.performance.DefaultPerformanceRegistry;
import rpg.core.performance.PerformanceReport;
import rpg.core.performance.PerformanceSnapshot;
import rpg.core.performance.SubsystemId;

final class PerformanceTestReports {

    static final SubsystemId COMBAT = new SubsystemId("b05-combat");

    private PerformanceTestReports() {}

    static PerformanceReport sample(AlertState state) {
        DefaultPerformanceRegistry registry =
                new DefaultPerformanceRegistry(() -> 2_000_000_000L, 16, true);
        registry.register(COMBAT, Duration.ofMillis(5), "B05");
        registry.record(COMBAT, 1_000_000L);
        registry.record(COMBAT, 3_000_000L);
        registry.recordOverallTick(25_000_000L);
        PerformanceSnapshot snapshot = registry.snapshot();
        return new PerformanceReport(
                Instant.ofEpochSecond(1_700_000_000L),
                snapshot,
                19.5d,
                40.0d,
                50.0d,
                12L,
                800L,
                Map.of(COMBAT, state),
                List.of(new AlertTransition(state, 2_000_000_000L, 1L)),
                4L,
                1L,
                0L);
    }
}
