package rpg.core.performance;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PerformanceRegistryBenchmarkTest {

    private static final int WARMUP_ROUNDS = 200;
    private static final int MEASURED_ROUNDS = 40;
    private static final int SCOPES_PER_ROUND = 200;
    private static final long BUDGET_NANOS = 5_000_000L;

    @Test
    @DisplayName("registry scope and snapshot overhead stays below the 5 ms local budget")
    void registryOverheadStaysWithinBudget() {
        DefaultPerformanceRegistry registry =
                new DefaultPerformanceRegistry(System::nanoTime, 1_200, true);
        SubsystemId id = new SubsystemId("benchmark");
        registry.register(id, Duration.ofMillis(5), "B15");

        long sink = 0L;
        for (int round = 0; round < WARMUP_ROUNDS; round++) {
            sink += exercise(registry, id);
        }

        long best = Long.MAX_VALUE;
        for (int round = 0; round < MEASURED_ROUNDS; round++) {
            long started = System.nanoTime();
            sink += exercise(registry, id);
            best = Math.min(best, System.nanoTime() - started);
        }

        assertThat(sink).isPositive();
        System.out.printf(
                "[b15] %d scopes and snapshot in %d ns (budget %d ns, margin %.1fx)%n",
                SCOPES_PER_ROUND, best, BUDGET_NANOS, (double) BUDGET_NANOS / best);
        assertThat(best).isLessThan(BUDGET_NANOS);
    }

    private static long exercise(DefaultPerformanceRegistry registry, SubsystemId id) {
        for (int i = 0; i < SCOPES_PER_ROUND; i++) {
            try (MeasurementScope ignored = registry.begin(id)) {
                // The body is intentionally empty: this measures the B15 instrumentation seam.
            }
        }
        return registry.snapshot().subsystems().get(id).window().sampleCount();
    }
}
