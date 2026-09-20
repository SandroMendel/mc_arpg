package rpg.plugin.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import rpg.core.performance.DefaultPerformanceRegistry;
import rpg.core.performance.DuplicateSubsystemException;
import rpg.core.performance.SubsystemId;
import rpg.core.performance.UnknownSubsystemException;

class PerformanceCoverageTest {

    @Test
    void rejectsDuplicateAndUnknownCoverageIds() {
        DefaultPerformanceRegistry registry =
                new DefaultPerformanceRegistry(() -> 0L, 8, true);
        PerformanceScopeWiring wiring = new PerformanceScopeWiring(registry);

        wiring.registerExpected();

        assertThat(wiring.expectedIds()).hasSize(8);
        assertThatThrownBy(wiring::registerExpected)
                .isInstanceOf(DuplicateSubsystemException.class);
        assertThatThrownBy(() -> wiring.begin("not-a-b15-source"))
                .isInstanceOf(UnknownSubsystemException.class);
        assertThatThrownBy(
                        () ->
                                registry.register(
                                        new SubsystemId("b05-combat"),
                                        Duration.ofMillis(5),
                                        "B05"))
                .isInstanceOf(DuplicateSubsystemException.class);
    }

    @Test
    void reportsAnExpectedSourceThatWasNeverRegistered() {
        DefaultPerformanceRegistry registry =
                new DefaultPerformanceRegistry(() -> 0L, 8, true);
        PerformanceScopeWiring wiring = new PerformanceScopeWiring(registry);

        assertThatThrownBy(() -> wiring.verifyExpected(registry.snapshot()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("b05-combat");
    }
}
