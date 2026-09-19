package rpg.plugin.performance;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import rpg.core.config.ConfigSchema;
import rpg.core.config.ConfigView;
import rpg.core.config.FieldDefinition;
import rpg.core.config.FieldType;
import rpg.core.performance.SubsystemId;

/** Schema and binding for the fixed-shape part of {@code performance.yml}. */
public final class PerformanceConfigSchema {

    private PerformanceConfigSchema() {}

    public static ConfigSchema<PerformanceConfig> schema() {
        return ConfigSchema.<PerformanceConfig>builder(1)
                .field(FieldDefinition.required("measurement.enabled", FieldType.BOOLEAN))
                .field(
                        FieldDefinition.required("measurement.window-samples", FieldType.INTEGER)
                                .withRange(1, 10_000))
                .field(
                        FieldDefinition.required("alerts.warning-ratio", FieldType.DOUBLE)
                                .withRange(0.000001d, 1.0d))
                .field(
                        FieldDefinition.required("alerts.critical-after-seconds", FieldType.INTEGER)
                                .withRange(1, 86_400))
                .field(
                        FieldDefinition.required("report.interval-seconds", FieldType.INTEGER)
                                .withRange(1, 86_400))
                .field(FieldDefinition.required("export.enabled", FieldType.BOOLEAN))
                .field(FieldDefinition.required("export.path", FieldType.STRING))
                .field(
                        FieldDefinition.required("targets.mean-tps-min", FieldType.DOUBLE)
                                .withRange(0.000001d, 20.0d))
                .field(
                        FieldDefinition.required("targets.mspt-p95-max", FieldType.DOUBLE)
                                .withRange(0.000001d, 60_000.0d))
                .field(
                        FieldDefinition.required("targets.mspt-p99-max", FieldType.DOUBLE)
                                .withRange(0.000001d, 60_000.0d))
                .field(FieldDefinition.optional("budgets", FieldType.MAP, Map.of()))
                .boundTo(PerformanceConfigSchema::bind)
                .build();
    }

    private static PerformanceConfig bind(ConfigView view) {
        String exportPath = view.getString("export.path").trim();
        if (exportPath.isBlank()) {
            throw new IllegalArgumentException("export.path must not be blank");
        }

        Path path;
        try {
            path = Path.of(exportPath);
        } catch (InvalidPathException invalid) {
            throw new IllegalArgumentException("export.path is invalid: " + exportPath, invalid);
        }

        return new PerformanceConfig(
                view.getBoolean("measurement.enabled"),
                view.getInt("measurement.window-samples"),
                view.getDouble("alerts.warning-ratio"),
                Duration.ofSeconds(view.getInt("alerts.critical-after-seconds")),
                Duration.ofSeconds(view.getInt("report.interval-seconds")),
                view.getBoolean("export.enabled"),
                path,
                view.getDouble("targets.mean-tps-min"),
                view.getDouble("targets.mspt-p95-max"),
                view.getDouble("targets.mspt-p99-max"),
                bindBudgets(view.getMap("budgets")));
    }

    private static Map<SubsystemId, PerformanceConfig.ConfiguredBudget> bindBudgets(
            Map<?, ?> rawBudgets) {
        Map<SubsystemId, PerformanceConfig.ConfiguredBudget> budgets = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawBudgets.entrySet()) {
            if (!(entry.getKey() instanceof String id) || !(entry.getValue() instanceof Map<?, ?> raw)) {
                throw new IllegalArgumentException(
                        "budgets must map subsystem ids to budget mappings");
            }
            Object budgetMillis = raw.get("budget-ms");
            Object owner = raw.get("owner");
            Object enabled = raw.containsKey("enabled") ? raw.get("enabled") : Boolean.TRUE;
            if (!(budgetMillis instanceof Number number)
                    || !(owner instanceof String ownerText)
                    || !(enabled instanceof Boolean enabledValue)) {
                throw new IllegalArgumentException(
                        "budget '" + id + "' requires budget-ms, owner and enabled values");
            }
            long nanos = Math.round(number.doubleValue() * 1_000_000.0d);
            budgets.put(
                    new SubsystemId(id),
                    new PerformanceConfig.ConfiguredBudget(
                            Duration.ofNanos(nanos), ownerText, enabledValue));
        }
        return budgets;
    }
}
