package rpg.core.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class PerformanceRegistryTest {

    @Test
    void registersSourcesAndPublishesTheirBudgetAndSamples() {
        DefaultPerformanceRegistry registry =
                new DefaultPerformanceRegistry(() -> 12L, 8, true);
        SubsystemId id = new SubsystemId("zones");
        registry.register(id, Duration.ofMillis(5), "B09");
        registry.record(id, 1_000L);

        PerformanceSnapshot snapshot = registry.snapshot();

        assertThat(snapshot.subsystems()).containsKey(id);
        assertThat(snapshot.subsystems().get(id).owner()).isEqualTo("B09");
        assertThat(snapshot.subsystems().get(id).budgetNanos()).isEqualTo(5_000_000L);
        assertThat(snapshot.subsystems().get(id).window().sampleCount()).isEqualTo(1);
    }

    @Test
    void rejectsDuplicateAndUnknownSources() {
        DefaultPerformanceRegistry registry =
                new DefaultPerformanceRegistry(() -> 0L, 8, true);
        SubsystemId id = new SubsystemId("mobs");
        registry.register(id, Duration.ofMillis(5), "B10");

        assertThatThrownBy(() -> registry.register(id, Duration.ofMillis(5), "B10"))
                .isInstanceOf(DuplicateSubsystemException.class);
        assertThatThrownBy(() -> registry.record(new SubsystemId("unknown"), 1L))
                .isInstanceOf(UnknownSubsystemException.class);
    }

    @Test
    void disabledMeasurementDoesNotCreateSamplesOrPretendTheSourceWasZero() {
        DefaultPerformanceRegistry registry =
                new DefaultPerformanceRegistry(() -> 0L, 8, false);
        SubsystemId id = new SubsystemId("hud");
        registry.register(id, Duration.ofMillis(5), "B13");
        registry.record(id, 1_000_000L);

        PerformanceWindow window = registry.snapshot().subsystems().get(id).window();

        assertThat(window.status()).isEqualTo(PerformanceWindow.Status.DISABLED);
        assertThat(window.sampleCount()).isZero();
    }
}
