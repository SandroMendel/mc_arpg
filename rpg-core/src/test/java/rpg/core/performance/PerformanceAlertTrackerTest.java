package rpg.core.performance;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

class PerformanceAlertTrackerTest {

    @Test
    void carriesWarningCriticalDeduplicationAndRecoveryAcrossReportSnapshots() {
        AtomicLong now = new AtomicLong();
        DefaultPerformanceRegistry registry = new DefaultPerformanceRegistry(now::get, 8, true);
        SubsystemId id = new SubsystemId("b15-tracked");
        registry.register(id, Duration.ofMillis(5), "B15");
        PerformanceAlertTracker tracker =
                new PerformanceAlertTracker(new AlertPolicy(0.90d, Duration.ofSeconds(60)));

        registry.record(id, 4_500_000L);
        PerformanceAlertTracker.Result warning = tracker.evaluate(registry.snapshot());
        assertThat(warning.alertStates()).containsEntry(id, AlertState.WARNING);
        assertThat(warning.transitions()).extracting(AlertTransition::state)
                .containsExactly(AlertState.WARNING);

        now.set(Duration.ofSeconds(60).toNanos());
        registry.record(id, 5_100_000L);
        PerformanceAlertTracker.Result critical = tracker.evaluate(registry.snapshot());
        assertThat(critical.alertStates()).containsEntry(id, AlertState.CRITICAL);
        assertThat(critical.transitions()).extracting(AlertTransition::state)
                .containsExactly(AlertState.CRITICAL);
        assertThat(tracker.evaluate(registry.snapshot()).transitions()).isEmpty();

        now.incrementAndGet();
        for (int i = 0; i < 8; i++) {
            registry.record(id, 1_000_000L);
        }
        PerformanceAlertTracker.Result recovered = tracker.evaluate(registry.snapshot());
        assertThat(recovered.alertStates()).containsEntry(id, AlertState.RECOVERED);
        assertThat(recovered.transitions()).extracting(AlertTransition::state)
                .containsExactly(AlertState.RECOVERED);
    }
}
