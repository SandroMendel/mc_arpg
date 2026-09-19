package rpg.core.performance;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Immutable report input shared by local logs, exports and load-test artifacts. */
public record PerformanceReport(
        Instant generatedAt,
        PerformanceSnapshot snapshot,
        double targetTps,
        double targetMsptP95,
        double targetMsptP99,
        long activePlayers,
        long activeCustomMobs,
        Map<SubsystemId, AlertState> alertStates,
        List<AlertTransition> transitions,
        long successfulReports,
        long failedReports,
        long exportErrors,
        Set<SubsystemId> missingSubsystems,
        Map<SubsystemId, Long> violationSinceNanos) {

    /** Compatibility constructor for reports without explicit missing-source metadata. */
    public PerformanceReport(
            Instant generatedAt,
            PerformanceSnapshot snapshot,
            double targetTps,
            double targetMsptP95,
            double targetMsptP99,
            long activePlayers,
            long activeCustomMobs,
            Map<SubsystemId, AlertState> alertStates,
            List<AlertTransition> transitions,
            long successfulReports,
            long failedReports,
            long exportErrors) {
        this(
                generatedAt,
                snapshot,
                targetTps,
                targetMsptP95,
                targetMsptP99,
                activePlayers,
                activeCustomMobs,
                alertStates,
                transitions,
                successfulReports,
                failedReports,
                exportErrors,
                Set.of(),
                Map.of());
    }

    public static PerformanceReport fromSnapshot(
            Instant generatedAt,
            PerformanceSnapshot snapshot,
            double targetTps,
            double targetMsptP95,
            double targetMsptP99,
            long activePlayers,
            long activeCustomMobs) {
        return fromSnapshot(
                generatedAt,
                snapshot,
                targetTps,
                targetMsptP95,
                targetMsptP99,
                activePlayers,
                activeCustomMobs,
                0L,
                0L,
                0L);
    }

    public static PerformanceReport fromSnapshot(
            Instant generatedAt,
            PerformanceSnapshot snapshot,
            double targetTps,
            double targetMsptP95,
            double targetMsptP99,
            long activePlayers,
            long activeCustomMobs,
            long successfulReports,
            long failedReports,
            long exportErrors) {
        Map<SubsystemId, AlertState> states = new LinkedHashMap<>();
        snapshot.subsystems().keySet().forEach(id -> states.put(id, AlertState.NORMAL));
        return new PerformanceReport(
                generatedAt,
                snapshot,
                targetTps,
                targetMsptP95,
                targetMsptP99,
                activePlayers,
                activeCustomMobs,
                states,
                List.of(),
                successfulReports,
                failedReports,
                exportErrors,
                Set.of(),
                Map.of());
    }

    public static PerformanceReport fromSnapshot(
            Instant generatedAt,
            PerformanceSnapshot snapshot,
            double targetTps,
            double targetMsptP95,
            double targetMsptP99,
            long activePlayers,
            long activeCustomMobs,
            Map<SubsystemId, AlertState> alertStates,
            List<AlertTransition> transitions,
            long successfulReports,
            long failedReports,
            long exportErrors,
            Set<SubsystemId> missingSubsystems,
            Map<SubsystemId, Long> violationSinceNanos) {
        return new PerformanceReport(
                generatedAt,
                snapshot,
                targetTps,
                targetMsptP95,
                targetMsptP99,
                activePlayers,
                activeCustomMobs,
                alertStates,
                transitions,
                successfulReports,
                failedReports,
                exportErrors,
                missingSubsystems,
                violationSinceNanos);
    }

    public PerformanceReport {
        Objects.requireNonNull(generatedAt, "generatedAt");
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(alertStates, "alertStates");
        Objects.requireNonNull(transitions, "transitions");
        Objects.requireNonNull(missingSubsystems, "missingSubsystems");
        Objects.requireNonNull(violationSinceNanos, "violationSinceNanos");
        if (!(targetTps > 0.0d) || !Double.isFinite(targetTps)) {
            throw new IllegalArgumentException("targetTps must be finite and positive");
        }
        if (!(targetMsptP95 > 0.0d) || !Double.isFinite(targetMsptP95)) {
            throw new IllegalArgumentException("targetMsptP95 must be finite and positive");
        }
        if (!(targetMsptP99 > 0.0d) || !Double.isFinite(targetMsptP99)) {
            throw new IllegalArgumentException("targetMsptP99 must be finite and positive");
        }
        if (activePlayers < 0 || activeCustomMobs < 0) {
            throw new IllegalArgumentException("active counts must not be negative");
        }
        if (successfulReports < 0 || failedReports < 0 || exportErrors < 0) {
            throw new IllegalArgumentException("report counters must not be negative");
        }
        alertStates = Map.copyOf(alertStates);
        transitions = List.copyOf(transitions);
        missingSubsystems = Set.copyOf(missingSubsystems);
        violationSinceNanos = Map.copyOf(violationSinceNanos);
    }

    /** Returns the current TPS estimate, or {@link Double#NaN} when the overall window is missing. */
    public double currentTps() {
        PerformanceWindow window = snapshot.overall();
        if (window.sampleCount() == 0) {
            return Double.NaN;
        }
        double meanMillis = window.meanNanos() / 1_000_000.0d;
        if (meanMillis <= 0.0d) {
            return 20.0d;
        }
        return Math.min(20.0d, 1_000.0d / meanMillis);
    }

    public double overallMspt(String quantile) {
        return nanosToMillis(quantileValue(snapshot.overall(), quantile));
    }

    public double overallDurationSeconds(String quantile) {
        return nanosToSeconds(quantileValue(snapshot.overall(), quantile));
    }

    public double subsystemDurationSeconds(PerformanceSnapshot.SubsystemSnapshot subsystem, String quantile) {
        Objects.requireNonNull(subsystem, "subsystem");
        return nanosToSeconds(quantileValue(subsystem.window(), quantile));
    }

    public double subsystemBudgetSeconds(PerformanceSnapshot.SubsystemSnapshot subsystem) {
        Objects.requireNonNull(subsystem, "subsystem");
        return nanosToSeconds(subsystem.budgetNanos());
    }

    public double subsystemBudgetRatio(PerformanceSnapshot.SubsystemSnapshot subsystem) {
        Objects.requireNonNull(subsystem, "subsystem");
        if (subsystem.window().sampleCount() == 0) {
            return Double.NaN;
        }
        return (double) subsystem.window().p95Nanos() / subsystem.budgetNanos();
    }

    public AlertState alertState(SubsystemId id) {
        Objects.requireNonNull(id, "id");
        return alertStates.getOrDefault(id, AlertState.NORMAL);
    }

    private static long quantileValue(PerformanceWindow window, String quantile) {
        Objects.requireNonNull(window, "window");
        Objects.requireNonNull(quantile, "quantile");
        if (window.sampleCount() == 0) {
            return 0L;
        }
        return switch (quantile) {
            case "p50" -> window.p50Nanos();
            case "p95" -> window.p95Nanos();
            case "p99" -> window.p99Nanos();
            default -> throw new IllegalArgumentException("unknown quantile: " + quantile);
        };
    }

    private static double nanosToMillis(long nanos) {
        return nanos == 0L ? Double.NaN : nanos / 1_000_000.0d;
    }

    private static double nanosToSeconds(long nanos) {
        return nanos == 0L ? Double.NaN : nanos / 1_000_000_000.0d;
    }
}
