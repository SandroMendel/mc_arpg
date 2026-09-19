package rpg.plugin.performance;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;

import rpg.core.performance.AlertState;
import rpg.core.performance.PerformanceReport;
import rpg.core.performance.PerformanceSnapshot;
import rpg.core.performance.SubsystemId;

/** Dependency-free Prometheus text exporter with complete-file atomic replacement. */
public final class PrometheusTextExporter {

    private final Path target;
    private long failureCount;

    public PrometheusTextExporter(Path target) {
        this.target = target;
    }

    public String render(PerformanceReport report) {
        Objects.requireNonNull(report, "report");
        StringBuilder output = new StringBuilder(2_048);
        help(output, "rpg_tick_tps", "Current overall server ticks per second");
        type(output, "rpg_tick_tps", "gauge");
        sample(output, "rpg_tick_tps{window=\"current\"}", report.currentTps());
        help(output, "rpg_target_tps_min", "Configured minimum overall TPS target");
        type(output, "rpg_target_tps_min", "gauge");
        sample(output, "rpg_target_tps_min", report.targetTps());

        help(output, "rpg_tick_mspt", "Overall tick duration in milliseconds");
        type(output, "rpg_tick_mspt", "gauge");
        for (String quantile : new String[] {"p50", "p95", "p99"}) {
            sample(
                    output,
                    "rpg_tick_mspt{quantile=\"" + quantile + "\"}",
                    report.overallMspt(quantile));
        }
        help(output, "rpg_target_mspt_max", "Configured maximum overall MSPT targets");
        type(output, "rpg_target_mspt_max", "gauge");
        sample(output, "rpg_target_mspt_max{quantile=\"p95\"}", report.targetMsptP95());
        sample(output, "rpg_target_mspt_max{quantile=\"p99\"}", report.targetMsptP99());

        help(output, "rpg_tick_duration_seconds", "Overall tick duration in seconds");
        type(output, "rpg_tick_duration_seconds", "gauge");
        for (String quantile : new String[] {"p50", "p95", "p99"}) {
            sample(
                    output,
                    "rpg_tick_duration_seconds{quantile=\"" + quantile + "\"}",
                    report.overallDurationSeconds(quantile));
        }

        help(output, "rpg_subsystem_budget_seconds", "Configured subsystem budget in seconds");
        type(output, "rpg_subsystem_budget_seconds", "gauge");
        help(output, "rpg_subsystem_duration_seconds", "Subsystem duration in seconds");
        type(output, "rpg_subsystem_duration_seconds", "gauge");
        help(output, "rpg_subsystem_budget_ratio", "Subsystem p95 duration divided by budget");
        type(output, "rpg_subsystem_budget_ratio", "gauge");
        help(output, "rpg_subsystem_samples_total", "Number of samples in the current subsystem window");
        type(output, "rpg_subsystem_samples_total", "counter");
        help(output, "rpg_subsystem_alert_state", "Subsystem alert state: normal=0 warning=1 critical=2 recovered=3");
        type(output, "rpg_subsystem_alert_state", "gauge");
        help(output, "rpg_subsystem_missing", "Whether a registered subsystem has no samples in the current window");
        type(output, "rpg_subsystem_missing", "gauge");
        help(output, "rpg_subsystem_violation_since_seconds", "Monotonic start of the active budget violation");
        type(output, "rpg_subsystem_violation_since_seconds", "gauge");

        report.snapshot().subsystems().entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().value()))
                .forEach(entry -> appendSubsystem(output, report, entry.getKey(), entry.getValue()));

        help(output, "rpg_active_players", "Active player count");
        type(output, "rpg_active_players", "gauge");
        sample(output, "rpg_active_players", report.activePlayers());
        help(output, "rpg_active_custom_mobs", "Active Custom-Mob count");
        type(output, "rpg_active_custom_mobs", "gauge");
        sample(output, "rpg_active_custom_mobs", report.activeCustomMobs());

        help(output, "rpg_performance_reports_total", "Generated performance reports");
        type(output, "rpg_performance_reports_total", "counter");
        sample(
                output,
                "rpg_performance_reports_total{status=\"success\"}",
                report.successfulReports());
        sample(
                output,
                "rpg_performance_reports_total{status=\"failure\"}",
                report.failedReports());
        help(output, "rpg_performance_export_errors_total", "Failed metric exports");
        type(output, "rpg_performance_export_errors_total", "counter");
        sample(output, "rpg_performance_export_errors_total", report.exportErrors() + failureCount);

        long timestampSeconds = report.generatedAt().getEpochSecond();
        output.append("# generated_at ").append(timestampSeconds).append('\n');
        sample(
                output,
                "rpg_performance_snapshot_timestamp_seconds",
                timestampSeconds);
        return output.toString();
    }

    public ExportResult export(PerformanceReport report) {
        Objects.requireNonNull(report, "report");
        if (target == null) {
            return failure("export target is not configured");
        }

        Path absoluteTarget = target.toAbsolutePath();
        Path parent = absoluteTarget.getParent();
        Path temporary = null;
        try {
            if (parent != null) {
                Files.createDirectories(parent);
            }
            temporary =
                    Files.createTempFile(
                            parent,
                            absoluteTarget.getFileName().toString(),
                            ".tmp");
            Files.writeString(
                    temporary,
                    render(report),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
            try {
                Files.move(
                        temporary,
                        absoluteTarget,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(
                        temporary,
                        absoluteTarget,
                        StandardCopyOption.REPLACE_EXISTING);
            }
            return new ExportResult(true, absoluteTarget, null);
        } catch (IOException | RuntimeException failure) {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // The original export error is the useful operator signal.
                }
            }
            return failure(failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage());
        }
    }

    public long failureCount() {
        return failureCount;
    }

    private ExportResult failure(String message) {
        failureCount++;
        return new ExportResult(false, target, message);
    }

    private static void appendSubsystem(
            StringBuilder output,
            PerformanceReport report,
            SubsystemId id,
            PerformanceSnapshot.SubsystemSnapshot subsystem) {
        String subsystemLabel = escapeLabel(id.value());
        String ownerLabel = escapeLabel(subsystem.owner());
        String prefix = "{subsystem=\"" + subsystemLabel + "\"";
        sample(
                output,
                "rpg_subsystem_budget_seconds" + prefix + ",owner=\"" + ownerLabel + "\"}",
                report.subsystemBudgetSeconds(subsystem));
        for (String quantile : new String[] {"p50", "p95", "p99"}) {
            sample(
                    output,
                    "rpg_subsystem_duration_seconds"
                            + prefix
                            + ",quantile=\""
                            + quantile
                            + "\"}",
                    report.subsystemDurationSeconds(subsystem, quantile));
        }
        sample(
                output,
                "rpg_subsystem_budget_ratio" + prefix + "}",
                report.subsystemBudgetRatio(subsystem));
        sample(
                output,
                "rpg_subsystem_samples_total" + prefix + "}",
                subsystem.window().sampleCount());
        sample(
                output,
                "rpg_subsystem_alert_state" + prefix + "}",
                alertValue(report.alertState(id)));
        sample(
                output,
                "rpg_subsystem_missing" + prefix + ",owner=\"" + ownerLabel + "\"}",
                report.missingSubsystems().contains(id) ? 1L : 0L);
        if (report.violationSinceNanos().containsKey(id)) {
            sample(
                    output,
                    "rpg_subsystem_violation_since_seconds"
                            + prefix
                            + ",owner=\""
                            + ownerLabel
                            + "\"}",
                    report.violationSinceNanos().get(id) / 1_000_000_000.0d);
        }
    }

    private static void help(StringBuilder output, String name, String description) {
        output.append("# HELP ").append(name).append(' ').append(description).append('\n');
    }

    private static void type(StringBuilder output, String name, String type) {
        output.append("# TYPE ").append(name).append(' ').append(type).append('\n');
    }

    private static void sample(StringBuilder output, String name, double value) {
        output.append(name).append(' ').append(format(value)).append('\n');
    }

    private static void sample(StringBuilder output, String name, long value) {
        output.append(name).append(' ').append(value).append('\n');
    }

    private static String format(double value) {
        return Double.isNaN(value)
                ? "NaN"
                : String.format(Locale.ROOT, "%.6f", value);
    }

    private static double alertValue(AlertState state) {
        return switch (state) {
            case NORMAL -> 0.0d;
            case WARNING -> 1.0d;
            case CRITICAL -> 2.0d;
            case RECOVERED -> 3.0d;
        };
    }

    private static String escapeLabel(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    public record ExportResult(boolean success, Path target, String error) {}
}
