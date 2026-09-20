package rpg.core.performance;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

class PerformanceWindowTest {

    @Test
    void keepsOnlyTheConfiguredNumberOfSamplesAndCalculatesPercentiles() {
        AtomicLong now = new AtomicLong();
        DefaultPerformanceRegistry registry =
                new DefaultPerformanceRegistry(now::get, 5, true);
        SubsystemId id = new SubsystemId("combat");
        registry.register(id, Duration.ofMillis(5), "B05");

        for (long sample : new long[] {1, 2, 3, 4, 5, 6, 7}) {
            registry.record(id, sample);
        }
        now.set(7);

        PerformanceWindow window = registry.snapshot().subsystems().get(id).window();

        assertThat(window.status()).isEqualTo(PerformanceWindow.Status.COMPLETE);
        assertThat(window.sampleCount()).isEqualTo(5);
        assertThat(window.minimumNanos()).isEqualTo(3);
        assertThat(window.maximumNanos()).isEqualTo(7);
        assertThat(window.p50Nanos()).isEqualTo(5);
        assertThat(window.p95Nanos()).isEqualTo(7);
        assertThat(window.p99Nanos()).isEqualTo(7);
    }

    @Test
    void distinguishesEmptyFromDisabledWindows() {
        DefaultPerformanceRegistry enabled =
                new DefaultPerformanceRegistry(() -> 0L, 4, true);
        SubsystemId enabledId = new SubsystemId("empty");
        enabled.register(enabledId, Duration.ofMillis(5), "TEST");

        DefaultPerformanceRegistry disabled =
                new DefaultPerformanceRegistry(() -> 0L, 4, false);
        SubsystemId disabledId = new SubsystemId("disabled");
        disabled.register(disabledId, Duration.ofMillis(5), "TEST");

        assertThat(enabled.snapshot().subsystems().get(enabledId).window().status())
                .isEqualTo(PerformanceWindow.Status.EMPTY);
        assertThat(disabled.snapshot().subsystems().get(disabledId).window().status())
                .isEqualTo(PerformanceWindow.Status.DISABLED);
    }
}
