package rpg.plugin.performance;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

import rpg.core.performance.PerformanceReport;
import rpg.core.scheduler.Scheduler;
import rpg.core.scheduler.TaskHandle;

/** One server-wide asynchronous report/export cycle, rearmed as a one-shot. */
public final class PerformanceReportCycle {

    private final Scheduler scheduler;
    private final Duration interval;
    private final Supplier<PerformanceReport> reportSupplier;
    private final PrometheusTextExporter exporter;
    private final Consumer<PerformanceReport> reportSink;
    private final PerformanceLogReporter logReporter;
    private final Consumer<String> logSink;
    private final Consumer<PrometheusTextExporter.ExportResult> exportResultSink;
    private final boolean exportEnabled;

    private TaskHandle scheduled;
    private boolean running;

    public PerformanceReportCycle(
            Scheduler scheduler,
            Duration interval,
            Supplier<PerformanceReport> reportSupplier,
            PrometheusTextExporter exporter,
            Consumer<PerformanceReport> reportSink,
            PerformanceLogReporter logReporter,
            Consumer<String> logSink) {
        this(
                scheduler,
                interval,
                reportSupplier,
                exporter,
                reportSink,
                logReporter,
                logSink,
                true,
                result -> {});
    }

    public PerformanceReportCycle(
            Scheduler scheduler,
            Duration interval,
            Supplier<PerformanceReport> reportSupplier,
            PrometheusTextExporter exporter,
            Consumer<PerformanceReport> reportSink,
            PerformanceLogReporter logReporter,
            Consumer<String> logSink,
            boolean exportEnabled) {
        this(
                scheduler,
                interval,
                reportSupplier,
                exporter,
                reportSink,
                logReporter,
                logSink,
                exportEnabled,
                result -> {});
    }

    public PerformanceReportCycle(
            Scheduler scheduler,
            Duration interval,
            Supplier<PerformanceReport> reportSupplier,
            PrometheusTextExporter exporter,
            Consumer<PerformanceReport> reportSink,
            PerformanceLogReporter logReporter,
            Consumer<String> logSink,
            boolean exportEnabled,
            Consumer<PrometheusTextExporter.ExportResult> exportResultSink) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.interval = Objects.requireNonNull(interval, "interval");
        this.reportSupplier = Objects.requireNonNull(reportSupplier, "reportSupplier");
        this.exporter = Objects.requireNonNull(exporter, "exporter");
        this.reportSink = Objects.requireNonNull(reportSink, "reportSink");
        this.logReporter = Objects.requireNonNull(logReporter, "logReporter");
        this.logSink = Objects.requireNonNull(logSink, "logSink");
        this.exportEnabled = exportEnabled;
        this.exportResultSink = Objects.requireNonNull(exportResultSink, "exportResultSink");
        if (interval.isZero() || interval.isNegative()) {
            throw new IllegalArgumentException("interval must be positive");
        }
    }

    public void start() {
        if (running) {
            return;
        }
        running = true;
        scheduleNext();
    }

    public void stop() {
        running = false;
        if (scheduled != null) {
            scheduled.cancel();
            scheduled = null;
        }
    }

    private void scheduleNext() {
        if (running) {
            scheduled = scheduler.runAsyncDelayed(interval, this::runOnce);
        }
    }

    private void runOnce() {
        if (!running) {
            return;
        }
        try {
            PerformanceReport report = reportSupplier.get();
            PrometheusTextExporter.ExportResult export =
                    exportEnabled
                            ? exporter.export(report)
                            : new PrometheusTextExporter.ExportResult(true, null, null);
            exportResultSink.accept(export);
            reportSink.accept(report);
            String log = logReporter.render(report);
            if (!export.success()) {
                log += " export=FAILURE reason=" + export.error();
            }
            logSink.accept(log);
        } catch (RuntimeException failure) {
            logSink.accept(
                    "[performance] phase=REPORT state=FAILURE reason="
                            + (failure.getMessage() == null
                                    ? failure.getClass().getSimpleName()
                                    : failure.getMessage()));
        } finally {
            scheduleNext();
        }
    }
}
