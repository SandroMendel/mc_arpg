package rpg.core.ability;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import rpg.core.classes.AbilityKind;
import rpg.core.combat.DamageType;
import rpg.core.config.ConfigSchema;
import rpg.core.config.ConfigView;
import rpg.core.config.FieldType;
import rpg.core.message.MessageKey;
import rpg.core.stats.Attribute;
import rpg.core.stats.UnknownAttributeException;

/**
 * Declaration and binding of {@code abilities.yml}.
 *
 * <p><b>{@code abilities} is one map field, not a field per ability.</b> Same reasoning B06 used for
 * its xp curve and B07 for its classes: the interesting promises cannot be written as a list of
 * required paths, so they are checked here and the first violation stops the start naming the
 * ability and the field.
 *
 * <p><b>Most rules are not in this class.</b> They sit in the constructors of {@link Ability},
 * {@link EffectSpec} and {@link TargetSpec}, so that an object which exists is valid no matter
 * whether it came from YAML or from a test. What lives here is only the part that is about the
 * <em>document</em>: types, missing keys, duplicate ids, and translating names into enums.
 *
 * <p>Two promises deliberately live elsewhere. <b>V12</b> (the material exists in the running server
 * version) needs Bukkit and belongs to {@code rpg-platform}. <b>V25 to V30</b> (the cross-check
 * against the class bindings) need {@code classes.yml} and therefore run after both files are
 * loaded - see {@code AbilityBindingCheck}, which US6 adds.
 */
public final class AbilityConfigSchema {

    private AbilityConfigSchema() {}

    public static ConfigSchema<AbilityConfig> schema() {
        ConfigSchema.Builder<AbilityConfig> builder =
                ConfigSchema.builder(AbilityConfig.SCHEMA_VERSION);
        builder.required("runtime.global-cooldown-ms", FieldType.LONG);
        builder.required("runtime.regeneration.health-combat-factor", FieldType.DOUBLE);
        builder.required("runtime.regeneration.mana-combat-factor", FieldType.DOUBLE);
        builder.required("abilities", FieldType.MAP);
        return builder.boundTo(AbilityConfigSchema::bind).build();
    }

    private static AbilityConfig bind(ConfigView view) {
        Map<?, ?> raw = view.getMap("abilities");
        Map<String, Ability> abilities = new LinkedHashMap<>();
        raw.forEach(
                (key, value) -> {
                    String id = String.valueOf(key);
                    if (id.isBlank()) {
                        throw new IllegalArgumentException("abilities: an id must not be blank");
                    }
                    // V3. A LinkedHashMap from YAML cannot actually hold a duplicate key, but a
                    // configuration assembled in code can - and the loader is the one place that sees
                    // both routes.
                    if (abilities.containsKey(id)) {
                        throw new IllegalArgumentException("abilities: '" + id + "' appears twice");
                    }
                    abilities.put(id, readAbility(id, asMap("abilities." + id, value)));
                });
        return new AbilityConfig(
                abilities,
                Duration.ofMillis(view.getLong("runtime.global-cooldown-ms")),
                view.getDouble("runtime.regeneration.health-combat-factor"),
                view.getDouble("runtime.regeneration.mana-combat-factor"));
    }

    private static Ability readAbility(String id, Map<?, ?> block) {
        String where = "abilities." + id;
        AbilityKind kind = readEnum(AbilityKind.class, requireString(block, "kind", where + ".kind"));
        return new Ability(
                id,
                kind,
                MessageKey.of(requireString(block, "display-name-key", where + ".display-name-key")),
                optionalDouble(block, "mana-cost", where + ".mana-cost", 0.0),
                millis(block, "cooldown-ms", where + ".cooldown-ms"),
                millis(block, "cast-time-ms", where + ".cast-time-ms"),
                optionalBoolean(block, "sustained"),
                optionalMillis(block, "duration-ms", where + ".duration-ms"),
                (int) optionalDouble(block, "charges", where + ".charges", 1.0),
                optionalMillis(block, "charge-window-ms", where + ".charge-window-ms"),
                optionalBoolean(block, "requires-behind-target"),
                optionalBoolean(block, "open-world-only"),
                optionalBoolean(block, "player-toggle"),
                optionalBoolean(block, "interrupt-on-move"),
                readTrigger(block, where),
                optionalDouble(block, "chance", where + ".chance", 1.0),
                readTarget(requireMap(block, "target", where + ".target"), where + ".target"),
                readEffects(block, where),
                (int) optionalDouble(block, "max-rank", where + ".max-rank", 1.0),
                optionalString(block, "item", where + ".item"));
    }

    private static AbilityTrigger readTrigger(Map<?, ?> block, String where) {
        String name = optionalString(block, "trigger", where + ".trigger");
        return name == null ? null : readEnum(AbilityTrigger.class, name);
    }

    private static TargetSpec readTarget(Map<?, ?> block, String where) {
        TargetMode mode = readEnum(TargetMode.class, requireString(block, "mode", where + ".mode"));
        return new TargetSpec(
                mode,
                optionalDouble(block, "range", where + ".range", 0.0),
                optionalBoxedDouble(block, "angle", where + ".angle"),
                // 0 = nicht angegeben. Kein Vorgabewert von 1: der wuerde V23 erfuellen und eine
                // vergessene Zeile von einer Entscheidung ununterscheidbar machen.
                (int) optionalDouble(block, "max-targets", where + ".max-targets", 0.0),
                optionalBoxedDouble(block, "hop-range", where + ".hop-range"),
                optionalBoxedDouble(block, "area-radius", where + ".area-radius"));
    }

    private static List<EffectSpec> readEffects(Map<?, ?> block, String where) {
        Object raw = block.get("effects");
        if (!(raw instanceof List<?> list)) {
            throw new IllegalArgumentException(where + ".effects must be a list, but was " + describe(raw));
        }
        List<EffectSpec> effects = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            effects.add(readEffect(asMap(where + ".effects[" + i + "]", list.get(i)), where, i));
        }
        return effects;
    }

    private static EffectSpec readEffect(Map<?, ?> block, String where, int index) {
        String at = where + ".effects[" + index + "]";
        return new EffectSpec(
                readEnum(EffectType.class, requireString(block, "type", at + ".type")),
                optionalDouble(block, "amount", at + ".amount", 0.0),
                optionalDouble(block, "per-rank", at + ".per-rank", 0.0),
                optionalMillis(block, "duration-ms", at + ".duration-ms"),
                optionalMillis(block, "interval-ms", at + ".interval-ms"),
                (int) optionalDouble(block, "max-stacks", at + ".max-stacks", 1.0),
                optionalBoxedDouble(block, "stack-cap", at + ".stack-cap"),
                readAttribute(block, at),
                readDamageType(block, at),
                optionalString(block, "status-effect", at + ".status-effect"),
                optionalBoxedDouble(block, "build-per-hit", at + ".build-per-hit"),
                optionalMillis(block, "idle-before-ms", at + ".idle-before-ms"),
                optionalBoxedDouble(block, "decay-per-second", at + ".decay-per-second"),
                optionalBoolean(block, "as-fraction"));
    }

    private static Attribute readAttribute(Map<?, ?> block, String at) {
        String key = optionalString(block, "attribute", at + ".attribute");
        if (key == null) {
            return null;
        }
        try {
            return Attribute.byKey(key);
        } catch (UnknownAttributeException unknown) {
            // Rethrown so the message carries the path; the bare exception names the key but not the
            // effect it sits in.
            throw new IllegalArgumentException(at + ".attribute: unknown attribute '" + key + "'", unknown);
        }
    }

    private static DamageType readDamageType(Map<?, ?> block, String at) {
        String name = optionalString(block, "damage-type", at + ".damage-type");
        return name == null ? null : readEnum(DamageType.class, name);
    }

    // ---- raw reading ------------------------------------------------------------------------------

    private static <E extends Enum<E>> E readEnum(Class<E> type, String name) {
        try {
            return Enum.valueOf(type, name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            throw new IllegalArgumentException(
                    "unknown "
                            + type.getSimpleName()
                            + " '"
                            + name
                            + "' - the permitted values are: "
                            + String.join(", ", names(type)));
        }
    }

    private static <E extends Enum<E>> List<String> names(Class<E> type) {
        List<String> names = new ArrayList<>();
        for (E constant : type.getEnumConstants()) {
            names.add(constant.name());
        }
        return names;
    }

    private static Map<?, ?> asMap(String path, Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException(path + " must be a section, but was " + describe(value));
        }
        return map;
    }

    private static Map<?, ?> requireMap(Map<?, ?> block, String key, String path) {
        Object value = block.get(key);
        if (value == null) {
            throw new IllegalArgumentException(path + " is missing");
        }
        return asMap(path, value);
    }

    private static String requireString(Map<?, ?> block, String key, String path) {
        String text = optionalString(block, key, path);
        if (text == null) {
            throw new IllegalArgumentException(path + " is missing");
        }
        return text;
    }

    private static String optionalString(Map<?, ?> block, String key, String path) {
        Object value = block.get(key);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        if (text.isBlank()) {
            throw new IllegalArgumentException(
                    path + " is present but blank - remove the key instead of leaving it empty");
        }
        return text;
    }

    private static boolean optionalBoolean(Map<?, ?> block, String key) {
        Object value = block.get(key);
        return value instanceof Boolean flag && flag;
    }

    private static double optionalDouble(Map<?, ?> block, String key, String path, double fallback) {
        Double value = optionalBoxedDouble(block, key, path);
        return value == null ? fallback : value;
    }

    private static Double optionalBoxedDouble(Map<?, ?> block, String key, String path) {
        Object value = block.get(key);
        if (value == null) {
            return null;
        }
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException(path + " must be a number, but was " + value);
        }
        return number.doubleValue();
    }

    private static Duration millis(Map<?, ?> block, String key, String path) {
        Duration value = optionalMillis(block, key, path);
        return value == null ? Duration.ZERO : value;
    }

    private static Duration optionalMillis(Map<?, ?> block, String key, String path) {
        Double value = optionalBoxedDouble(block, key, path);
        return value == null ? null : Duration.ofMillis(value.longValue());
    }

    private static String describe(Object value) {
        return value == null ? "missing" : value.getClass().getSimpleName();
    }
}
