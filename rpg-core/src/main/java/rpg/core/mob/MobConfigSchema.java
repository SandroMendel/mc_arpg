package rpg.core.mob;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import rpg.core.config.ConfigSchema;
import rpg.core.config.ConfigView;
import rpg.core.config.FieldDefinition;
import rpg.core.config.FieldType;
import rpg.core.stats.Attribute;

/**
 * Schema für {@code mobs.yml} (FR-002).
 *
 * <p><b>Nur die oberste Ebene lässt sich als Feld deklarieren.</b> Arten und Horden sind Abbildungen
 * mit freien Schlüsseln — dieselbe Form, die {@code zones.yml} und {@code drops.by-type} benutzen —,
 * also wird die Tiefe im Binder geprüft. Alles, was der Binder ablehnt, wirft mit einer Meldung, die
 * <b>Datei, Schlüssel und Grund</b> nennt, und der Loader macht daraus den Fail-Fast, den Prinzip V
 * verlangt.
 *
 * <p><b>Es gibt keine Regel, die zur Laufzeit auflöst.</b> Ein Bereich, den es nicht gibt, eine Art,
 * die es nicht gibt, zwei Bosse in einer Zone, eine Bossart ohne {@code boss: true} — alles wird
 * beim Start abgelehnt statt später stillschweigend zurechtgebogen. Was nicht passieren darf,
 * braucht keinen Vorrang.
 *
 * <p><b>Was hier absichtlich NICHT geprüft wird:</b> ob {@code base} ein Vanilla-Entity-Typ ist, und
 * ob der Bereichsschlüssel in {@code zones.yml} existiert. Beides braucht Wissen, das dieses Paket
 * nicht hat — das eine ist Bukkit, das andere B09. Beide Prüfungen laufen beim Start in
 * {@link MobModule} beziehungsweise in der Plattformschicht, und beide brechen den Start genauso ab.
 * Der Unterschied ist nur, wo die Antwort herkommt.
 */
public final class MobConfigSchema {

    public static final int SCHEMA_VERSION = 1;

    private MobConfigSchema() {}

    public static ConfigSchema<MobConfig> schema() {
        ConfigSchema.Builder<MobConfig> builder = ConfigSchema.builder(SCHEMA_VERSION);
        builder.required("budget", FieldType.MAP);
        builder.required("horde", FieldType.MAP);
        builder.required("kinds", FieldType.MAP);
        builder.required("hordes", FieldType.MAP);
        builder.field(
                FieldDefinition.optional(
                                "admin-spawn-limit", FieldType.INTEGER, MobConfig.DEFAULT_ADMIN_SPAWN_LIMIT)
                        .withRange(1, Integer.MAX_VALUE));
        return builder.boundTo(MobConfigSchema::bind).build();
    }

    private static MobConfig bind(ConfigView view) {
        Map<?, ?> budgetBody = view.getMap("budget");
        Budget budget =
                new Budget(
                        requireInt(budgetBody, "server-wide", "budget"),
                        requireInt(budgetBody, "per-zone", "budget"),
                        requireInt(budgetBody, "per-chunk", "budget"),
                        requireInt(budgetBody, "per-player", "budget"));

        Map<?, ?> hordeBody = view.getMap("horde");
        Duration respawn = Duration.ofMillis(requireInt(hordeBody, "respawn-interval-ms", "horde"));
        double density = requireDouble(hordeBody, "density-per-player", "horde");
        Duration cleanupAfter =
                Duration.ofSeconds(requireInt(hordeBody, "cleanup-after-seconds", "horde"));
        double cleanupRadius = requireDouble(hordeBody, "cleanup-radius", "horde");
        Duration retarget = Duration.ofMillis(requireInt(hordeBody, "retarget-interval-ms", "horde"));

        Map<String, MobKind> kinds = readKinds(view.getMap("kinds"));
        Map<String, HordeSpec> hordes = readHordes(view.getMap("hordes"), kinds);

        return new MobConfig(
                budget,
                respawn,
                density,
                cleanupAfter,
                cleanupRadius,
                retarget,
                kinds,
                hordes,
                view.getInt("admin-spawn-limit"));
    }

    // ----------------------------------------------------------------- kinds

    private static Map<String, MobKind> readKinds(Map<?, ?> raw) {
        Map<String, MobKind> kinds = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            String key = String.valueOf(entry.getKey());
            if (key.isBlank()) {
                throw new IllegalArgumentException("kinds: a mob kind key must not be blank");
            }
            kinds.put(key, readKind(key, section(entry.getValue(), "kinds." + key)));
        }
        if (kinds.isEmpty()) {
            throw new IllegalArgumentException("kinds: at least one mob kind is required");
        }
        return kinds;
    }

    private static MobKind readKind(String key, Map<?, ?> body) {
        String where = "kinds." + key;
        return new MobKind(
                key,
                requireString(body, "base", where).toUpperCase(Locale.ROOT),
                requireInt(body, "level", where),
                readAttributes(body.get("attributes"), where + ".attributes"),
                requireDouble(body, "follow-range", where),
                MobMessageKeys.nameOf(key),
                requireLong(body, "xp", where),
                requireLong(body, "coins", where),
                optionalBoolean(body, "boss", false, where));
    }

    /**
     * Die Attributwerte einer Art.
     *
     * <p>Die Schlüssel sind die Konfigurationsnamen aus B04 ({@code health}, {@code defense},
     * {@code physicalDamage} …) und nicht die Enum-Namen — dieselbe Schreibweise, die {@code
     * stats.yml} und {@code classes.yml} schon benutzen. Ein unbekannter Name bricht den Start ab
     * statt still ignoriert zu werden: ein Tippfehler wäre sonst eine Kreatur ohne Leben.
     */
    private static Map<Attribute, Double> readAttributes(Object raw, String where) {
        Map<?, ?> body = section(raw, where);
        Map<Attribute, Double> values = new EnumMap<>(Attribute.class);
        for (Map.Entry<?, ?> entry : body.entrySet()) {
            String name = String.valueOf(entry.getKey());
            Attribute attribute = attributeByConfigName(name, where);
            Object value = entry.getValue();
            if (!(value instanceof Number number)) {
                throw new IllegalArgumentException(
                        where + "." + name + " must be a number, but was " + describe(value));
            }
            values.put(attribute, number.doubleValue());
        }
        if (values.isEmpty()) {
            throw new IllegalArgumentException(
                    where + " is empty - a creature without values is a creature without a fight");
        }
        return values;
    }

    private static Attribute attributeByConfigName(String name, String where) {
        for (Attribute attribute : Attribute.values()) {
            if (attribute.key().equals(name)) {
                return attribute;
            }
        }
        List<String> known = new ArrayList<>();
        for (Attribute attribute : Attribute.values()) {
            known.add(attribute.key());
        }
        throw new IllegalArgumentException(
                where + ": no such attribute '" + name + "'. Known: " + known);
    }

    // ---------------------------------------------------------------- hordes

    private static Map<String, HordeSpec> readHordes(Map<?, ?> raw, Map<String, MobKind> kinds) {
        Map<String, HordeSpec> hordes = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            String zoneKey = String.valueOf(entry.getKey());
            if (zoneKey.isBlank()) {
                throw new IllegalArgumentException("hordes: a zone key must not be blank");
            }
            hordes.put(
                    zoneKey,
                    readHorde(zoneKey, section(entry.getValue(), "hordes." + zoneKey), kinds));
        }
        return hordes;
    }

    private static HordeSpec readHorde(
            String zoneKey, Map<?, ?> body, Map<String, MobKind> kinds) {
        String where = "hordes." + zoneKey;
        Map<?, ?> areas = section(body.get("areas"), where + ".areas");

        List<HordeSpec.Entry> entries = new ArrayList<>();
        for (Map.Entry<?, ?> areaEntry : areas.entrySet()) {
            String areaKey = String.valueOf(areaEntry.getKey());
            String areaWhere = where + ".areas." + areaKey;
            for (Object raw : list(areaEntry.getValue(), areaWhere)) {
                Map<?, ?> line = section(raw, areaWhere);
                String kindKey = requireString(line, "kind", areaWhere);
                if (!kinds.containsKey(kindKey)) {
                    throw new IllegalArgumentException(
                            areaWhere
                                    + ".kind: no such mob kind '"
                                    + kindKey
                                    + "' - a typo here would be a silently empty horde");
                }
                entries.add(
                        new HordeSpec.Entry(areaKey, kindKey, requireInt(line, "weight", areaWhere)));
            }
        }

        BossSpec boss = null;
        if (body.get("boss") != null) {
            boss = readBoss(section(body.get("boss"), where + ".boss"), where + ".boss", kinds);
        }
        return new HordeSpec(zoneKey, entries, boss);
    }

    private static BossSpec readBoss(Map<?, ?> body, String where, Map<String, MobKind> kinds) {
        String kindKey = requireString(body, "kind", where);
        MobKind kind = kinds.get(kindKey);
        if (kind == null) {
            throw new IllegalArgumentException(where + ".kind: no such mob kind '" + kindKey + "'");
        }
        if (!kind.boss()) {
            throw new IllegalArgumentException(
                    where
                            + ".kind: '"
                            + kindKey
                            + "' is not marked 'boss: true' - a label that means nothing is worse"
                            + " than none");
        }
        Map<?, ?> offset = body.get("offset") == null ? Map.of() : section(body.get("offset"), where + ".offset");
        return new BossSpec(
                kindKey,
                requireString(body, "area", where),
                optionalDouble(offset, "x", 0.0, where + ".offset"),
                optionalDouble(offset, "y", 0.0, where + ".offset"),
                optionalDouble(offset, "z", 0.0, where + ".offset"),
                Duration.ofMinutes(requireInt(body, "respawn-minutes", where)));
    }

    // ---------------------------------------------------------------- helpers

    private static Map<?, ?> section(Object value, String path) {
        if (value instanceof Map<?, ?> map) {
            return map;
        }
        if (value == null) {
            throw new IllegalArgumentException(path + " is missing");
        }
        throw new IllegalArgumentException(path + " must be a section, but was " + describe(value));
    }

    private static List<?> list(Object value, String path) {
        if (value instanceof List<?> found) {
            return found;
        }
        if (value == null) {
            throw new IllegalArgumentException(path + " is missing");
        }
        throw new IllegalArgumentException(path + " must be a list, but was " + describe(value));
    }

    private static String requireString(Map<?, ?> body, String field, String where) {
        Object value = body.get(field);
        if (value == null) {
            throw new IllegalArgumentException(where + "." + field + " is missing");
        }
        String text = String.valueOf(value);
        if (text.isBlank()) {
            throw new IllegalArgumentException(where + "." + field + " must not be blank");
        }
        return text;
    }

    private static int requireInt(Map<?, ?> body, String field, String where) {
        return (int) requireNumber(body, field, where).longValue();
    }

    private static long requireLong(Map<?, ?> body, String field, String where) {
        return requireNumber(body, field, where).longValue();
    }

    private static double requireDouble(Map<?, ?> body, String field, String where) {
        return requireNumber(body, field, where).doubleValue();
    }

    private static Number requireNumber(Map<?, ?> body, String field, String where) {
        Object value = body.get(field);
        if (value == null) {
            throw new IllegalArgumentException(where + "." + field + " is missing");
        }
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException(
                    where + "." + field + " must be a number, but was " + describe(value));
        }
        return number;
    }

    private static double optionalDouble(
            Map<?, ?> body, String field, double fallback, String where) {
        Object value = body.get(field);
        if (value == null) {
            return fallback;
        }
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException(
                    where + "." + field + " must be a number, but was " + describe(value));
        }
        return number.doubleValue();
    }

    private static boolean optionalBoolean(
            Map<?, ?> body, String field, boolean fallback, String where) {
        Object value = body.get(field);
        if (value == null) {
            return fallback;
        }
        if (!(value instanceof Boolean flag)) {
            throw new IllegalArgumentException(
                    where + "." + field + " must be true or false, but was " + describe(value));
        }
        return flag;
    }

    private static String describe(Object value) {
        return value == null ? "nothing" : value.getClass().getSimpleName();
    }
}
