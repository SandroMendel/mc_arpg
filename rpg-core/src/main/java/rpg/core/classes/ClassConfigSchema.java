package rpg.core.classes;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import rpg.core.config.ConfigSchema;
import rpg.core.config.ConfigView;
import rpg.core.config.FieldType;
import rpg.core.message.MessageKey;
import rpg.core.session.CharacterClass;
import rpg.core.stats.Attribute;
import rpg.core.stats.UnknownAttributeException;

/**
 * Declaration and binding of {@code classes.yml}.
 *
 * <p><b>{@code classes} is one map field, not a field per class.</b> Same reasoning B06 used for its
 * xp curve: the interesting promises - all three classes present, no fourth one, ladders of differing
 * length - cannot be expressed as a list of required paths. They are checked in the binder, which
 * stops at the first violation and names class, ladder and tier.
 *
 * <p><b>The ladders are lists.</b> A fixed number of tier fields could not express warrior 5/6, rogue
 * 6/6 and mage 7/7; it would have forced the material lists to be trimmed rather than represented.
 *
 * <p>Two promises deliberately live elsewhere. <b>V12</b> (the material exists in the running server
 * version) needs Bukkit and therefore belongs to {@code rpg-platform} - this class only requires a
 * non-blank name. <b>V13/V14</b> (the caps from ADR-008) need the stat configuration of another block
 * and live in {@link ClassConfig#validateAgainstCaps}.
 */
public final class ClassConfigSchema {

    public static final int SCHEMA_VERSION = 1;

    private ClassConfigSchema() {}

    public static ConfigSchema<ClassConfig> schema() {
        ConfigSchema.Builder<ClassConfig> builder = ConfigSchema.builder(SCHEMA_VERSION);
        builder.required("classes", FieldType.MAP);
        return builder.boundTo(ClassConfigSchema::bind).build();
    }

    private static ClassConfig bind(ConfigView view) {
        Map<?, ?> raw = view.getMap("classes");
        Map<CharacterClass, CharacterClassDefinition> definitions =
                new EnumMap<>(CharacterClass.class);
        raw.forEach(
                (key, value) -> {
                    CharacterClass id = parseClassId(key);
                    if (definitions.containsKey(id)) {
                        throw new IllegalArgumentException("classes: '" + id + "' appears twice");
                    }
                    definitions.put(id, readClass(id, asMap("classes." + id, value)));
                });
        return ClassConfig.of(definitions);
    }

    /**
     * V1 - the set of classes lives in code. An unknown id stops the start and names the id, instead
     * of being skipped silently; that is what proves no third place decides the set of classes
     * (ADR-019).
     */
    private static CharacterClass parseClassId(Object key) {
        String name = String.valueOf(key);
        try {
            return CharacterClass.valueOf(name);
        } catch (IllegalArgumentException unknown) {
            throw new IllegalArgumentException(
                    "classes: unknown class id '"
                            + name
                            + "'. Known are "
                            + List.of(CharacterClass.values())
                            + " - the set of classes lives in code and a fourth one needs an enum"
                            + " value plus a migration, not a configuration entry",
                    unknown);
        }
    }

    private static CharacterClassDefinition readClass(CharacterClass id, Map<?, ?> block) {
        String displayNameKey = requireString(block, "display-name-key", id + ".display-name-key");
        String menuMaterial = requireString(block, "menu-material", id + ".menu-material");
        ClassBaseStats baseStats =
                ClassBaseStats.of(readEightValues(id, block, "base-stats"));
        ClassGrowth growth = ClassGrowth.of(readEightValues(id, block, "growth"));
        EquipmentLadder armor = readLadder(id, block, LadderSlot.ARMOR);
        EquipmentLadder weapon = readLadder(id, block, LadderSlot.WEAPON);
        List<AbilityBinding> abilities = readAbilities(id, block);
        return new CharacterClassDefinition(
                id,
                MessageKey.of(displayNameKey),
                menuMaterial,
                baseStats,
                growth,
                armor,
                weapon,
                abilities);
    }

    /**
     * V2 - all eight attributes are required, including the ones that are zero. A missing field must
     * not become a silent zero, because then "movement speed does not grow" could not be told from
     * "the line was forgotten". Same argument B06 made for {@code level-growth}.
     */
    private static double[] readEightValues(CharacterClass id, Map<?, ?> block, String section) {
        Map<?, ?> values = requireMap(block, section, id + "." + section);
        double[] result = new double[Attribute.count()];
        for (Attribute attribute : Attribute.all()) {
            Object value = values.get(attribute.key());
            if (value == null) {
                throw new IllegalArgumentException(
                        id
                                + "."
                                + section
                                + ": missing '"
                                + attribute.key()
                                + "'. All "
                                + Attribute.count()
                                + " attributes are required, including the ones that are zero");
            }
            if (!(value instanceof Number number)) {
                throw new IllegalArgumentException(
                        id
                                + "."
                                + section
                                + "."
                                + attribute.key()
                                + " must be a number, but was "
                                + value);
            }
            result[attribute.ordinal()] = number.doubleValue();
        }
        return result;
    }

    private static EquipmentLadder readLadder(
            CharacterClass id, Map<?, ?> block, LadderSlot slot) {
        String path = id + "." + slot.configKey();
        Object rawList = block.get(slot.configKey());
        if (!(rawList instanceof List<?> list)) {
            throw new IllegalArgumentException(
                    path + " must be a list of tiers, but was " + describe(rawList));
        }
        List<EquipmentTier> tiers = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            tiers.add(readTier(path, slot, i + 1, asMap(path + "[" + (i + 1) + "]", list.get(i))));
        }
        return EquipmentLadder.of(slot, tiers);
    }

    private static EquipmentTier readTier(
            String path, LadderSlot slot, int index, Map<?, ?> raw) {
        String where = path + " tier " + index;
        String material = requireString(raw, "material", where + ".material");
        int requiredLevel = requireInt(raw, "required-level", where + ".required-level");

        Map<?, ?> rawValues = requireMap(raw, "values", where + ".values");
        Map<Attribute, Double> values = new EnumMap<>(Attribute.class);
        rawValues.forEach(
                (key, value) -> {
                    Attribute attribute;
                    try {
                        attribute = Attribute.byKey(String.valueOf(key));
                    } catch (UnknownAttributeException unknown) {
                        // Rethrown so the message carries the path; the bare exception would name the
                        // key but not the tier it sits in.
                        throw new IllegalArgumentException(
                                where + ".values: unknown attribute '" + key + "'", unknown);
                    }
                    if (!(value instanceof Number number)) {
                        throw new IllegalArgumentException(
                                where + ".values." + key + " must be a number, but was " + value);
                    }
                    values.put(attribute, number.doubleValue());
                });

        TierAppearance appearance =
                new TierAppearance(
                        material,
                        optionalInt(raw, "color", where + ".color"),
                        optionalString(raw, "trim-material", where + ".trim-material"),
                        optionalString(raw, "trim-pattern", where + ".trim-pattern"),
                        optionalInt(raw, "model-data", where + ".model-data"));

        return EquipmentTier.of(index, slot, values, appearance, requiredLevel, readCost(where, raw));
    }

    /**
     * V18 - the cost block is checked for being a map and nothing else. B07 knows nothing about coins
     * or prices; interpreting it would couple this block to B11, which does not exist yet.
     */
    private static Map<String, Object> readCost(String where, Map<?, ?> raw) {
        Object cost = raw.get("cost");
        if (cost == null) {
            return Map.of();
        }
        if (!(cost instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException(
                    where + ".cost must be a map, but was " + describe(cost));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    /**
     * V15 to V17 - empty is allowed while B08 does not exist, a partially filled loadout is not. The
     * ids themselves are never resolved here (FR-044).
     */
    private static List<AbilityBinding> readAbilities(CharacterClass id, Map<?, ?> block) {
        Object raw = block.get("abilities");
        if (raw == null) {
            throw new IllegalArgumentException(
                    id
                            + ".abilities is missing. Use an empty list to say 'not yet', so that a"
                            + " forgotten section cannot look like a deliberate one");
        }
        if (!(raw instanceof List<?> list)) {
            throw new IllegalArgumentException(
                    id + ".abilities must be a list, but was " + describe(raw));
        }
        List<AbilityBinding> bindings = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            String where = id + ".abilities[" + (i + 1) + "]";
            Map<?, ?> entry = asMap(where, list.get(i));
            String abilityId = requireString(entry, "id", where + ".id");
            String kindName = requireString(entry, "kind", where + ".kind");
            AbilityKind kind;
            try {
                kind = AbilityKind.valueOf(kindName);
            } catch (IllegalArgumentException unknown) {
                throw new IllegalArgumentException(
                        where + ".kind must be ACTIVE or PASSIVE, but was '" + kindName + "'",
                        unknown);
            }
            boolean unique = entry.get("unique") instanceof Boolean flag && flag;
            int unlockLevel = requireInt(entry, "unlock-level", where + ".unlock-level");
            bindings.add(new AbilityBinding(abilityId, kind, unique, unlockLevel));
        }
        return bindings;
    }

    // ---- small readers, each naming the full path so a violation is findable ------------------

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
        Object value = block.get(key);
        if (value == null) {
            throw new IllegalArgumentException(path + " is missing");
        }
        String text = String.valueOf(value);
        if (text.isBlank()) {
            throw new IllegalArgumentException(path + " must not be blank");
        }
        return text;
    }

    private static int requireInt(Map<?, ?> block, String key, String path) {
        Object value = block.get(key);
        if (value == null) {
            throw new IllegalArgumentException(path + " is missing");
        }
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException(path + " must be a number, but was " + value);
        }
        return number.intValue();
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

    private static Integer optionalInt(Map<?, ?> block, String key, String path) {
        Object value = block.get(key);
        if (value == null) {
            return null;
        }
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException(path + " must be a number, but was " + value);
        }
        return number.intValue();
    }

    private static String describe(Object value) {
        return value == null ? "absent" : value.getClass().getSimpleName() + " " + value;
    }
}
