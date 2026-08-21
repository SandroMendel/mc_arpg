package rpg.core.progression;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import rpg.core.config.ConfigSchema;
import rpg.core.config.ConfigView;
import rpg.core.config.FieldType;
import rpg.core.stats.Attribute;

/**
 * Schema for {@code progression.yml} (FR-002, FR-005).
 *
 * <p><b>The curve is one map field, not fifty-nine required keys.</b> Fifty-nine keys would enforce
 * that each level exists but could not express the other two promises - positive values and strict
 * monotonicity - and monotonicity is the one whose absence pins a player on a level forever. Same
 * shape as {@code mobs.by-type} in {@code combat.yml}, so two files side by side read alike.
 *
 * <p><b>All eight growth fields are required, including the three that are zero.</b> The same
 * argument that makes every environment source in B05 a required field: a missing field should stop
 * the start, not quietly become zero. Otherwise "movement speed does not grow" is
 * indistinguishable from "somebody forgot the line".
 */
public final class ProgressionConfigSchema {

    public static final int SCHEMA_VERSION = 1;

    private ProgressionConfigSchema() {}

    public static ConfigSchema<ProgressionConfig> schema() {
        ConfigSchema.Builder<ProgressionConfig> builder = ConfigSchema.builder(SCHEMA_VERSION);
        builder.required("xp-curve", FieldType.MAP);
        for (Attribute attribute : Attribute.all()) {
            builder.required("level-growth." + attribute.key(), FieldType.DOUBLE);
        }
        // LONG, not INTEGER: the curve is freely configurable, and a threshold above two billion is
        // unlikely but an overflow would be a silent arithmetic error instead of a startup error.
        builder.required("mob-xp.default", FieldType.LONG);
        builder.required("mob-xp.by-type", FieldType.MAP);
        builder.required("party.max-size", FieldType.INTEGER);
        builder.required("party.range-blocks", FieldType.DOUBLE);
        builder.required("party.bonus-per-member", FieldType.DOUBLE);
        builder.required("party.bonus-cap", FieldType.DOUBLE);
        builder.required("party.invite-timeout-seconds", FieldType.INTEGER);
        builder.required("progress-event.window-millis", FieldType.INTEGER);
        return builder.boundTo(ProgressionConfigSchema::bind).build();
    }

    private static ProgressionConfig bind(ConfigView view) {
        XpCurve curve = XpCurve.of(readCurve(view.getMap("xp-curve")));

        double[] growth = new double[Attribute.count()];
        Attribute[] attributes = Attribute.all();
        for (int i = 0; i < attributes.length; i++) {
            growth[i] = view.getDouble("level-growth." + attributes[i].key());
        }

        return new ProgressionConfig(
                curve,
                LevelGrowth.of(growth),
                view.getLong("mob-xp.default"),
                readMobXp(view.getMap("mob-xp.by-type")),
                view.getInt("party.max-size"),
                view.getDouble("party.range-blocks"),
                view.getDouble("party.bonus-per-member"),
                view.getDouble("party.bonus-cap"),
                Duration.ofSeconds(view.getInt("party.invite-timeout-seconds")),
                Duration.ofMillis(view.getInt("progress-event.window-millis")));
    }

    /**
     * Turns the raw map into level-to-threshold pairs. {@link XpCurve#of} does the three real
     * checks; this only rejects keys and values that are not numbers at all, because a typo there
     * should name the offending key rather than surface as a missing level.
     */
    private static Map<Integer, Long> readCurve(Map<?, ?> raw) {
        Map<Integer, Long> table = new LinkedHashMap<>();
        raw.forEach(
                (key, value) -> {
                    int level = parseLevel(key);
                    if (!(value instanceof Number number)) {
                        throw new IllegalArgumentException(
                                "progression.xp-curve: level "
                                        + level
                                        + " must be a number, but was "
                                        + value);
                    }
                    table.put(level, number.longValue());
                });
        return table;
    }

    private static int parseLevel(Object key) {
        if (key instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(key).trim());
        } catch (NumberFormatException notANumber) {
            throw new IllegalArgumentException(
                    "progression.xp-curve: '" + key + "' is not a level", notANumber);
        }
    }

    private static Map<String, Long> readMobXp(Map<?, ?> raw) {
        Map<String, Long> amounts = new LinkedHashMap<>();
        raw.forEach(
                (key, value) -> {
                    String type = String.valueOf(key);
                    if (!(value instanceof Number number)) {
                        throw new IllegalArgumentException(
                                "progression.mob-xp.by-type."
                                        + type
                                        + " must be a number, but was "
                                        + value);
                    }
                    long amount = number.longValue();
                    if (amount < 1L) {
                        throw new IllegalArgumentException(
                                "progression.mob-xp.by-type."
                                        + type
                                        + " must be at least 1, but was "
                                        + amount);
                    }
                    amounts.put(type, amount);
                });
        return amounts;
    }
}
