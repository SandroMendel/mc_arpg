package rpg.core.combat;

import java.time.Duration;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

import rpg.core.config.ConfigSchema;
import rpg.core.config.ConfigView;
import rpg.core.config.FieldType;

/**
 * The schema for {@code combat.yml} (Principle V).
 *
 * <p>Every field is declared explicitly rather than read as a free-form map, for the same reason as
 * B04's {@code stats.yml}: B01's loader can only name a field it was told about, and "invalid
 * configuration" without a location means reading the whole file to find the one wrong line.
 *
 * <p>Mob types are the exception - which creatures exist is not something this schema can know
 * ahead of time, so they are read as a map and validated on binding.
 */
public final class CombatConfigSchema {

    /** Raised when the shape of {@code combat.yml} changes incompatibly. */
    public static final int SCHEMA_VERSION = 1;

    private CombatConfigSchema() {}

    public static ConfigSchema<CombatConfig> schema() {
        ConfigSchema.Builder<CombatConfig> builder = ConfigSchema.builder(SCHEMA_VERSION);

        builder.required("combat.combat-timeout-seconds", FieldType.INTEGER);
        builder.required("combat.attribution.max-attackers", FieldType.INTEGER);
        builder.required("combat.attribution.timeout-seconds", FieldType.INTEGER);
        builder.required("combat.feedback.aggregation-window-millis", FieldType.INTEGER);
        builder.required("combat.feedback.knockback-strength", FieldType.DOUBLE);

        builder.required("environment.fall.safe-blocks", FieldType.DOUBLE);
        builder.required("environment.fall.damage-per-block", FieldType.DOUBLE);
        builder.required("environment.fall.max-damage", FieldType.DOUBLE);

        // ADR-003 wants a decision per source, so every flat hazard is a required field. A missing
        // one stops the start instead of quietly dealing zero damage.
        for (EnvironmentSource source : EnvironmentSource.all()) {
            if (!source.isComputed()) {
                builder.required("environment." + source.key(), FieldType.DOUBLE);
            }
        }

        builder.required("mobs.default.health", FieldType.DOUBLE);
        builder.required("mobs.default.defense", FieldType.DOUBLE);
        builder.required("mobs.default.physical-damage", FieldType.DOUBLE);
        builder.required("mobs.by-type", FieldType.MAP);

        return builder.boundTo(CombatConfigSchema::bind).build();
    }

    private static CombatConfig bind(ConfigView view) {
        EnumMap<EnvironmentSource, Double> environment = new EnumMap<>(EnvironmentSource.class);
        for (EnvironmentSource source : EnvironmentSource.all()) {
            if (!source.isComputed()) {
                environment.put(source, view.getDouble("environment." + source.key()));
            }
        }

        Map<String, CombatConfig.MobStats> mobs = new LinkedHashMap<>();
        Map<?, ?> byType = view.getMap("mobs.by-type");
        byType.forEach(
                (key, value) -> {
                    String type = String.valueOf(key);
                    if (!(value instanceof Map<?, ?> fields)) {
                        throw new IllegalArgumentException(
                                "mobs.by-type." + type + " must be a mapping of stat fields");
                    }
                    mobs.put(
                            type,
                            new CombatConfig.MobStats(
                                    requireDouble(fields, type, "health"),
                                    requireDouble(fields, type, "defense"),
                                    requireDouble(fields, type, "physical-damage")));
                });

        // Every remaining rule lives in the record's constructor, so validation is in one place
        // rather than half here and half there.
        return new CombatConfig(
                Duration.ofSeconds(view.getInt("combat.combat-timeout-seconds")),
                view.getInt("combat.attribution.max-attackers"),
                Duration.ofSeconds(view.getInt("combat.attribution.timeout-seconds")),
                Duration.ofMillis(view.getInt("combat.feedback.aggregation-window-millis")),
                view.getDouble("combat.feedback.knockback-strength"),
                new FallDamageConfig(
                        view.getDouble("environment.fall.safe-blocks"),
                        view.getDouble("environment.fall.damage-per-block"),
                        view.getDouble("environment.fall.max-damage")),
                environment,
                mobs,
                new CombatConfig.MobStats(
                        view.getDouble("mobs.default.health"),
                        view.getDouble("mobs.default.defense"),
                        view.getDouble("mobs.default.physical-damage")));
    }

    private static double requireDouble(Map<?, ?> fields, String mobType, String field) {
        Object raw = fields.get(field);
        if (raw instanceof Number number) {
            return number.doubleValue();
        }
        throw new IllegalArgumentException(
                "mobs.by-type." + mobType + "." + field + " is required and must be a number");
    }
}
