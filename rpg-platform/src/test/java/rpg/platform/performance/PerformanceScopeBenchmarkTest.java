package rpg.platform.performance;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import rpg.core.performance.DefaultPerformanceRegistry;
import rpg.core.performance.MeasurementScope;
import rpg.core.performance.SubsystemId;

/** Repeatable no-server benchmark for the common B13/B10 scope boundary. */
class PerformanceScopeBenchmarkTest {

    @Test
    void scopeEntryExitAndSnapshotStayBelowTheFiveMillisecondLocalBudget() {
        DefaultPerformanceRegistry registry =
                new DefaultPerformanceRegistry(System::nanoTime, 256, true);
        SubsystemId hud = new SubsystemId("b13-hud");
        SubsystemId hordes = new SubsystemId("b10-hordes");
        registry.register(hud, Duration.ofMillis(3), "B13");
        registry.register(hordes, Duration.ofMillis(5), "B10");

        for (int i = 0; i < 10_000; i++) {
            try (MeasurementScope ignored = registry.begin(i % 2 == 0 ? hud : hordes)) {
                // The hotpath work is deliberately outside this benchmark; this isolates scope cost.
            }
        }

        long started = System.nanoTime();
        for (int i = 0; i < 50_000; i++) {
            try (MeasurementScope ignored = registry.begin(i % 2 == 0 ? hud : hordes)) {
                // no-op body
            }
        }
        registry.snapshot();
        long elapsed = System.nanoTime() - started;

        assertThat(elapsed / 50_000.0d)
                .as("average scope entry/exit cost in nanoseconds")
                .isLessThan(Duration.ofMillis(5).toNanos());
    }
}
