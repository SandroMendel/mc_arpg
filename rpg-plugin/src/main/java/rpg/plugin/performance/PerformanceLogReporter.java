package rpg.plugin.performance;

import java.util.Comparator;
import java.util.Objects;
import java.util.stream.Collectors;

import rpg.core.performance.AlertState;
import rpg.core.performance.PerformanceReport;
import rpg.core.performance.PerformanceSnapshot;

/** Structured English report text for the local server log. */
public final class PerformanceLogReporter {

    public String render(PerformanceReport report) {
        Objects.requireNonNull(report, "report");
        String state =
                report.alertStates().values().stream()
                        .max(Comparator.comparingInt(PerformanceLogReporter::severity))
                        .map(Enum::name)
                        .orElse(AlertState.NORMAL.name());
        String subsystems =
                report.alertStates().entrySet().stream()
                        .filter(entry -> entry.getValue() != AlertState.NORMAL)
                        .sorted(Comparator.comparing(entry -> entry.getKey().value()))
                        .map(entry -> alertDetail(report, entry.getKey(), entry.getValue()))
                        .collect(Collectors.joining(","));
        String missing =
                report.missingSubsystems().stream()
                        .map(id -> id.value())
                        .sorted()
                        .collect(Collectors.joining(","));
        String targetStatus = targetStatus(report);
        return "[performance] phase=REPORT state="
                + state
                + " generated_at="
                + report.generatedAt()
                + " tps="
                + format(report.currentTps())
                + " mspt_p95="
                + format(report.overallMspt("p95"))
                + " mspt_p99="
                + format(report.overallMspt("p99"))
                + " period_samples="
                + report.snapshot().overall().sampleCount()
                + " target_tps_min="
                + format(report.targetTps())
                + " target_mspt_p95_max="
                + format(report.targetMsptP95())
                + " target_mspt_p99_max="
                + format(report.targetMsptP99())
                + " target_status="
                + targetStatus
                + " active_players="
                + report.activePlayers()
                + " active_custom_mobs="
                + report.activeCustomMobs()
                + " missing_sources="
                + (missing.isBlank() ? "none" : missing)
                + " alerts="
                + (subsystems.isBlank() ? "none" : subsystems);
    }

    private static String alertDetail(
            PerformanceReport report,
            rpg.core.performance.SubsystemId id,
            AlertState state) {
        PerformanceSnapshot.SubsystemSnapshot subsystem = report.snapshot().subsystems().get(id);
        String observed =
                subsystem == null
                        ? "missing"
                        : format(report.subsystemDurationSeconds(subsystem, "p95"));
        String budget = subsystem == null ? "missing" : format(report.subsystemBudgetSeconds(subsystem));
        String since =
                report.violationSinceNanos().containsKey(id)
                        ? Long.toString(report.violationSinceNanos().get(id))
                        : "none";
        return id.value()
                + ":"
                + state.name()
                + "(p95_seconds="
                + observed
                + ",budget_seconds="
                + budget
                + ",violation_since_nanos="
                + since
                + ")";
    }

    private static String targetStatus(PerformanceReport report) {
        double tps = report.currentTps();
        double p95 = report.overallMspt("p95");
        double p99 = report.overallMspt("p99");
        if (Double.isNaN(tps) || Double.isNaN(p95) || Double.isNaN(p99)) {
            return "missing";
        }
        return tps >= report.targetTps()
                        && p95 <= report.targetMsptP95()
                        && p99 <= report.targetMsptP99()
                ? "ok"
                : "violation";
    }

    private static int severity(AlertState state) {
        return switch (state) {
            case NORMAL -> 0;
            case RECOVERED -> 1;
            case WARNING -> 2;
            case CRITICAL -> 3;
        };
    }

    private static String format(double value) {
        return Double.isNaN(value) ? "missing" : String.format(java.util.Locale.ROOT, "%.3f", value);
    }
}
