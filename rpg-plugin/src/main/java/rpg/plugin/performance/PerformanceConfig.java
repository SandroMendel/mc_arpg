package rpg.plugin.performance;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

import rpg.core.performance.SubsystemId;

/** Validated operator settings for measurement, alerting, reporting and export. */
public record PerformanceConfig(
        boolean measurementEnabled,
        int windowSamples,
        double warningRatio,
        Duration criticalAfter,
        Duration reportInterval,
        boolean exportEnabled,
        Path exportPath,
        double targetTps,
        double targetMsptP95,
        double targetMsptP99,
        Map<SubsystemId, ConfiguredBudget> budgets) {

    public PerformanceConfig {
        Objects.requireNonNull(criticalAfter, "criticalAfter");
        Objects.requireNonNull(reportInterval, "reportInterval");
        Objects.requireNonNull(exportPath, "exportPath");
        Objects.requireNonNull(budgets, "budgets");
        if (windowSamples <= 0) {
            throw new IllegalArgumentException("windowSamples must be positive");
        }
        if (!(warningRatio > 0.0d && warningRatio <= 1.0d)) {
            throw new IllegalArgumentException("warningRatio must be in (0, 1]");
        }
        if (criticalAfter.isZero() || criticalAfter.isNegative()) {
            throw new IllegalArgumentException("criticalAfter must be positive");
        }
        if (reportInterval.isZero() || reportInterval.isNegative()) {
            throw new IllegalArgumentException("reportInterval must be positive");
        }
        if (exportPath.toString().isBlank()) {
            throw new IllegalArgumentException("exportPath must not be blank");
        }
        if (!(targetTps > 0.0d && Double.isFinite(targetTps))) {
            throw new IllegalArgumentException("targetTps must be finite and positive");
        }
        if (!(targetMsptP95 > 0.0d && Double.isFinite(targetMsptP95))) {
            throw new IllegalArgumentException("targetMsptP95 must be finite and positive");
        }
        if (!(targetMsptP99 > 0.0d && Double.isFinite(targetMsptP99))) {
            throw new IllegalArgumentException("targetMsptP99 must be finite and positive");
        }
        budgets = Map.copyOf(budgets);
    }

    public record ConfiguredBudget(Duration budget, String owner, boolean enabled) {

        public ConfiguredBudget {
            Objects.requireNonNull(budget, "budget");
            Objects.requireNonNull(owner, "owner");
            owner = owner.trim();
            if (budget.isZero() || budget.isNegative()) {
                throw new IllegalArgumentException("budget must be positive");
            }
            if (owner.isBlank()) {
                throw new IllegalArgumentException("owner must not be blank");
            }
        }
    }
}
