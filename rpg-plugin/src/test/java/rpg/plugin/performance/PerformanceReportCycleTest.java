package rpg.plugin.performance;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import rpg.core.performance.AlertState;
import rpg.core.performance.PerformanceReport;
import rpg.core.scheduler.EntityRef;
import rpg.core.scheduler.Scheduler;
import rpg.core.scheduler.TaskHandle;
import rpg.core.scheduler.WorldPosition;

class PerformanceReportCycleTest {

    @Test
    void usesOneAsyncOneShotAndReschedulesAfterACompletedCycle() {
        RecordingScheduler scheduler = new RecordingScheduler();
        PrometheusTextExporter exporter = new PrometheusTextExporter(null);
        List<PerformanceReport> reported = new ArrayList<>();
        PerformanceReport report = PerformanceTestReports.sample(AlertState.CRITICAL);
        PerformanceReportCycle cycle =
                new PerformanceReportCycle(
                        scheduler,
                        Duration.ofSeconds(60),
                        () -> report,
                        exporter,
                        reported::add,
                        new PerformanceLogReporter(),
                        line -> {});

        cycle.start();
        assertThat(scheduler.delays).containsExactly(Duration.ofSeconds(60));
        assertThat(scheduler.tasks).hasSize(1);

        scheduler.tasks.get(0).run();

        assertThat(reported).containsExactly(report);
        assertThat(scheduler.delays).containsExactly(Duration.ofSeconds(60), Duration.ofSeconds(60));
        assertThat(scheduler.tasks).hasSize(2);
    }

    @Test
    void logsAndSchedulesAgainWhenTheExporterFails() {
        RecordingScheduler scheduler = new RecordingScheduler();
        PrometheusTextExporter exporter = new PrometheusTextExporter(null);
        List<String> logs = new ArrayList<>();
        PerformanceReportCycle cycle =
                new PerformanceReportCycle(
                        scheduler,
                        Duration.ofSeconds(60),
                        () -> PerformanceTestReports.sample(AlertState.CRITICAL),
                        exporter,
                        report -> {},
                        new PerformanceLogReporter(),
                        logs::add);

        cycle.start();
        scheduler.tasks.get(0).run();

        assertThat(logs).anyMatch(line -> line.contains("CRITICAL"));
        assertThat(scheduler.tasks).hasSize(2);
    }

    private static final class RecordingScheduler implements Scheduler {
        private final List<Duration> delays = new ArrayList<>();
        private final List<Runnable> tasks = new ArrayList<>();

        @Override
        public TaskHandle runAsyncDelayed(Duration delay, Runnable task) {
            delays.add(delay);
            tasks.add(task);
            return new Handle();
        }

        @Override
        public TaskHandle runSyncAtLocation(WorldPosition position, Runnable task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TaskHandle runSyncOnEntity(EntityRef entity, Runnable task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TaskHandle runSyncOnEntityDelayed(
                EntityRef entity, Duration delay, Runnable task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TaskHandle runAsync(Runnable task) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class Handle implements TaskHandle {
        private boolean cancelled;

        @Override
        public void cancel() {
            cancelled = true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }
    }
}
