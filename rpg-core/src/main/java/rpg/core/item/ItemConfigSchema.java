package rpg.core.item;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import rpg.core.config.ConfigSchema;
import rpg.core.config.ConfigView;
import rpg.core.config.FieldType;
import rpg.core.session.CharacterClass;
import rpg.core.stats.Attribute;

/**
 * Schema für {@code items.yml} (FR-012).
 *
 * <p><b>Nur die oberste Ebene lässt sich als Feld deklarieren.</b> Vorlagen, Beutetabellen und
 * Händlerbestände sind Abbildungen mit freien Schlüsseln — dieselbe Form, die {@code mobs.yml} und
 * {@code zones.yml} benutzen —, also wird die Tiefe im Binder geprüft. Alles, was der Binder
 * ablehnt, wirft mit einer Meldung, die <b>Datei, Schlüssel und Grund</b> nennt, und der Loader
 * macht daraus den Fail-Fast, den Prinzip V verlangt.
 *
 * <p><b>Es gibt keine Regel, die zur Laufzeit auflöst.</b> Eine unbekannte Raritätsstufe, ein
 * Verbrauchbares ohne Wirkung, eine Beutetabelle, die eine Vorlage nennt, die es nicht gibt — alles
 * wird beim Start abgelehnt statt später stillschweigend zurechtgebogen.
 *
 * <p><b>Die wichtigste Prüfung ist die, die niemand erwartet:</b> {@code per-death} muss um den
 * konfigurierten Faktor über den Schadensraten liegen (FR-044). Ohne sie könnte ein späteres
 * Balancing die Todesstrafe aus ADR-017 aushebeln, ohne dass irgendetwas kaputtginge — und genau
 * solche Verstöße fallen erst Monate später auf. Sie steht in {@link WearCurve#validate}.
 *
 * <p><b>Was hier absichtlich NICHT geprüft wird:</b> ob {@code material} ein Vanilla-Material ist,
 * ob eine Zone in {@code zones.yml} existiert und ob eine Art in {@code mobs.yml} existiert. Alle
 * drei brauchen Wissen, das dieses Paket nicht hat — das eine ist Bukkit, die anderen sind B09 und
 * B10. Sie laufen beim Start in {@link ItemModule} und brechen ihn genauso ab; der Unterschied ist
 * nur, woher die Antwort kommt. Dieselbe Aufteilung, die {@code MobConfigSchema} trifft.
 */
public final class ItemConfigSchema {

    public static final int SCHEMA_VERSION = 1;

    private static final String FILE = "items.yml";

    private ItemConfigSchema() {}

    public static ConfigSchema<ItemConfig> schema() {
        return ConfigSchema.<ItemConfig>builder(SCHEMA_VERSION)
                .required("wear", FieldType.MAP)
                .required("repair", FieldType.MAP)
                .required("templates", FieldType.MAP)
                .required("loot", FieldType.MAP)
                .required("vendors", FieldType.MAP)
                .boundTo(ItemConfigSchema::bind)
                .build();
    }

    private static ItemConfig bind(ConfigView view) {
        WearCurve wear = readWear(view.getMap("wear"));
        wear.validate(FILE + ".wear");

        RepairPricing repair = readRepair(view.getMap("repair"));

        Map<String, ItemTemplate> templates = readTemplates(view.getMap("templates"));
        LootTables loot = readLoot(view.getMap("loot"));
        Map<String, VendorStock> vendors = readVendors(view.getMap("vendors"));

        verifyTemplatesExist(templates, loot, vendors);

        return new ItemConfig(templates, wear, repair, loot, vendors);
    }

    // ---------------------------------------------------------------------------------------
    // wear
    // ---------------------------------------------------------------------------------------

    private static WearCurve readWear(Map<?, ?> body) {
        String where = FILE + ".wear";
        List<Double> warnAt = new ArrayList<>();
        Object rawWarn = body.get("warn-at");
        if (rawWarn != null) {
            if (!(rawWarn instanceof List<?> list)) {
                throw new IllegalArgumentException(
                        where + ".warn-at must be a list, but was " + describe(rawWarn));
            }
            for (Object element : list) {
                if (!(element instanceof Number number)) {
                    throw new IllegalArgumentException(
                            where + ".warn-at must hold numbers, but held " + describe(element));
                }
                warnAt.add(number.doubleValue());
            }
        }
        return new WearCurve(
                requireDouble(body, "threshold", where),
                requireDouble(body, "floor", where),
                requireDouble(body, "per-damage-taken", where),
                requireDouble(body, "per-damage-dealt", where),
                requireDouble(body, "per-death", where),
                requireDouble(body, "death-factor-min", where),
                warnAt,
                Duration.ofMillis(requireLong(body, "warn-cooldown-ms", where)));
    }

    // ---------------------------------------------------------------------------------------
    // repair
    // ---------------------------------------------------------------------------------------

    private static RepairPricing readRepair(Map<?, ?> body) {
        String where = FILE + ".repair";
        Object raw = body.get("base-per-tier");
        if (!(raw instanceof List<?> list)) {
            throw new IllegalArgumentException(
                    where + ".base-per-tier must be a list, but was " + describe(raw));
        }
        List<Long> prices = new ArrayList<>(list.size());
        for (Object element : list) {
            if (!(element instanceof Number number)) {
                throw new IllegalArgumentException(
                        where + ".base-per-tier must hold numbers, but held " + describe(element));
            }
            prices.add(number.longValue());
        }
        RepairPricing pricing = new RepairPricing(prices);
        // Wie lang die laengste Leiter ist, weiss B07 - hier wird nur die Form geprueft. Den
        // Abgleich macht ItemModule beim Start.
        pricing.validate(prices.size(), where);
        return pricing;
    }

    // ---------------------------------------------------------------------------------------
    // templates
    // ---------------------------------------------------------------------------------------

    private static Map<String, ItemTemplate> readTemplates(Map<?, ?> body) {
        Map<String, ItemTemplate> templates = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : body.entrySet()) {
            String key = String.valueOf(entry.getKey());
            String where = FILE + ".templates." + key;
            if (!(entry.getValue() instanceof Map<?, ?> spec)) {
                throw new IllegalArgumentException(
                        where + " must be a map, but was " + describe(entry.getValue()));
            }
            templates.put(key, readTemplate(key, spec, where));
        }
        if (templates.isEmpty()) {
            throw new IllegalArgumentException(
                    FILE + ".templates is empty - a block without a single item is a block that"
                            + " cannot do anything");
        }
        return templates;
    }

    private static ItemTemplate readTemplate(String key, Map<?, ?> spec, String where) {
        ItemCategory category = readCategory(spec, where);
        String material = requireString(spec, "material", where);

        Rarity rarity =
                Rarity.fromConfig(requireString(spec, "rarity", where))
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                where
                                                        + ".rarity is not one of the eight: "
                                                        + spec.get("rarity")
                                                        + ". Allowed: "
                                                        + allRarities()));

        Integer minLevel = optionalInt(spec, "min-level", where);
        CharacterClass boundClass = readClass(spec, where);
        Long sellPrice = optionalLong(spec, "sell-price", where);
        Integer modelData = optionalInt(spec, "model-data", where);

        ConsumableEffect effect =
                spec.containsKey("effect") ? readEffect(spec.get("effect"), where + ".effect") : null;
        CosmeticAppearance appearance =
                spec.containsKey("appearance")
                        ? readAppearance(spec.get("appearance"), where + ".appearance")
                        : null;

        try {
            return new ItemTemplate(
                    key,
                    category,
                    material,
                    rarity,
                    minLevel,
                    boundClass,
                    sellPrice,
                    modelData,
                    effect,
                    appearance);
        } catch (IllegalArgumentException failure) {
            // Die Meldung des Records nennt den Schluessel, aber nicht die Datei. Beides gehoert
            // hinein, sonst sucht ein Betreiber in sechs YAMLs.
            throw new IllegalArgumentException(FILE + ".templates." + failure.getMessage(), failure);
        }
    }

    private static ItemCategory readCategory(Map<?, ?> spec, String where) {
        String raw = requireString(spec, "category", where);
        for (ItemCategory category : ItemCategory.values()) {
            if (category.name().equalsIgnoreCase(raw.trim())) {
                return category;
            }
        }
        throw new IllegalArgumentException(
                where
                        + ".category is '"
                        + raw
                        + "', but only CONSUMABLE and COSMETIC exist. Equipment is class"
                        + " progression and lives in classes.yml (ADR-017); upgrade material was"
                        + " dropped when the tier advance became payable in coins (ADR-039)");
    }

    private static CharacterClass readClass(Map<?, ?> spec, String where) {
        Object raw = spec.get("bound-class");
        if (raw == null) {
            return null;
        }
        String name = String.valueOf(raw).trim();
        for (CharacterClass candidate : CharacterClass.values()) {
            if (candidate.name().equalsIgnoreCase(name)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException(
                where + ".bound-class is not a known class: " + raw);
    }

    private static ConsumableEffect readEffect(Object raw, String where) {
        if (!(raw instanceof Map<?, ?> body)) {
            throw new IllegalArgumentException(where + " must be a map, but was " + describe(raw));
        }
        Double heal = optionalDouble(body, "heal", where);
        Double mana = optionalDouble(body, "mana", where);

        Map<Attribute, Double> buff = new EnumMap<>(Attribute.class);
        Object rawBuff = body.get("buff");
        if (rawBuff != null) {
            if (!(rawBuff instanceof Map<?, ?> buffBody)) {
                throw new IllegalArgumentException(
                        where + ".buff must be a map, but was " + describe(rawBuff));
            }
            for (Map.Entry<?, ?> entry : buffBody.entrySet()) {
                buff.put(
                        readAttribute(String.valueOf(entry.getKey()), where + ".buff"),
                        toDouble(entry.getValue(), where + ".buff." + entry.getKey()));
            }
        }
        Long durationMs = optionalLong(body, "duration-ms", where);
        Long cooldownMs = optionalLong(body, "cooldown-ms", where);

        try {
            return new ConsumableEffect(
                    heal,
                    mana,
                    buff,
                    durationMs == null ? null : Duration.ofMillis(durationMs),
                    cooldownMs == null ? null : Duration.ofMillis(cooldownMs));
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException(where + ": " + failure.getMessage(), failure);
        }
    }

    private static Attribute readAttribute(String name, String where) {
        String normalised = name.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        for (Attribute attribute : Attribute.values()) {
            if (attribute.name().equals(normalised)) {
                return attribute;
            }
        }
        throw new IllegalArgumentException(where + " names an unknown attribute: " + name);
    }

    private static CosmeticAppearance readAppearance(Object raw, String where) {
        if (!(raw instanceof Map<?, ?> body)) {
            throw new IllegalArgumentException(where + " must be a map, but was " + describe(raw));
        }
        try {
            return new CosmeticAppearance(
                    requireString(body, "trim-material", where),
                    requireString(body, "trim-pattern", where));
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException(where + ": " + failure.getMessage(), failure);
        }
    }

    // ---------------------------------------------------------------------------------------
    // loot
    // ---------------------------------------------------------------------------------------

    private static LootTables readLoot(Map<?, ?> body) {
        return new LootTables(
                readLootLevel(body.get("by-kind"), FILE + ".loot.by-kind"),
                readLootLevel(body.get("by-zone"), FILE + ".loot.by-zone"),
                readLootLevel(body.get("by-boss"), FILE + ".loot.by-boss"));
    }

    private static Map<String, LootTable> readLootLevel(Object raw, String where) {
        if (raw == null) {
            return Map.of();
        }
        if (!(raw instanceof Map<?, ?> body)) {
            throw new IllegalArgumentException(where + " must be a map, but was " + describe(raw));
        }
        Map<String, LootTable> tables = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : body.entrySet()) {
            String key = String.valueOf(entry.getKey());
            String tableWhere = where + "." + key;
            if (!(entry.getValue() instanceof List<?> list)) {
                throw new IllegalArgumentException(
                        tableWhere + " must be a list of entries, but was " + describe(entry.getValue()));
            }
            List<LootEntry> entries = new ArrayList<>(list.size());
            for (Object element : list) {
                if (!(element instanceof Map<?, ?> spec)) {
                    throw new IllegalArgumentException(
                            tableWhere + " must hold maps, but held " + describe(element));
                }
                try {
                    entries.add(
                            new LootEntry(
                                    requireString(spec, "template", tableWhere),
                                    requireDouble(spec, "chance", tableWhere),
                                    (int) requireLong(spec, "min", tableWhere),
                                    (int) requireLong(spec, "max", tableWhere)));
                } catch (IllegalArgumentException failure) {
                    throw new IllegalArgumentException(
                            tableWhere + ": " + failure.getMessage(), failure);
                }
            }
            tables.put(key, new LootTable(entries));
        }
        return tables;
    }

    // ---------------------------------------------------------------------------------------
    // vendors
    // ---------------------------------------------------------------------------------------

    private static Map<String, VendorStock> readVendors(Map<?, ?> body) {
        Map<String, VendorStock> vendors = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : body.entrySet()) {
            String zoneKey = String.valueOf(entry.getKey());
            String where = FILE + ".vendors." + zoneKey;
            if (!(entry.getValue() instanceof Map<?, ?> spec)) {
                throw new IllegalArgumentException(
                        where + " must be a map, but was " + describe(entry.getValue()));
            }
            Object rawStock = spec.get("stock");
            if (!(rawStock instanceof List<?> list)) {
                throw new IllegalArgumentException(
                        where + ".stock must be a list, but was " + describe(rawStock));
            }
            Map<String, Long> prices = new LinkedHashMap<>();
            for (Object element : list) {
                if (!(element instanceof Map<?, ?> item)) {
                    throw new IllegalArgumentException(
                            where + ".stock must hold maps, but held " + describe(element));
                }
                String template = requireString(item, "template", where + ".stock");
                long price = requireLong(item, "price", where + ".stock." + template);
                if (prices.put(template, price) != null) {
                    throw new IllegalArgumentException(
                            where + ".stock names " + template + " twice");
                }
            }
            vendors.put(zoneKey, new VendorStock(prices));
        }
        return vendors;
    }

    // ---------------------------------------------------------------------------------------
    // Querbezuege
    // ---------------------------------------------------------------------------------------

    /**
     * Jede genannte Vorlage muss es geben (FR-024).
     *
     * <p>Und damit ist die Ausrüstungssperre miterledigt: es gibt keine Ausrüstungsvorlagen, also
     * ist jede Ausrüstungskennung eine unbekannte, und der Start bricht ab. Eine eigene Regel
     * dafür wäre eine zweite Wahrheit darüber, was Ausrüstung ist.
     */
    private static void verifyTemplatesExist(
            Map<String, ItemTemplate> templates, LootTables loot, Map<String, VendorStock> vendors) {
        List<String> missing = new ArrayList<>();
        for (String key : loot.referencedTemplates()) {
            if (!templates.containsKey(key)) {
                missing.add("loot -> " + key);
            }
        }
        for (Map.Entry<String, VendorStock> entry : vendors.entrySet()) {
            for (String key : entry.getValue().templateKeys()) {
                if (!templates.containsKey(key)) {
                    missing.add("vendors." + entry.getKey() + " -> " + key);
                }
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException(
                    FILE
                            + " names item templates that do not exist: "
                            + missing
                            + " (FR-024). Note that equipment has no template here at all - armour"
                            + " and weapon are class progression and live in classes.yml"
                            + " (ADR-017), so naming one always lands in this message");
        }
    }

    // ---------------------------------------------------------------------------------------
    // Lesehilfen - jede Meldung nennt Datei, Schluessel und Grund (FR-012)
    // ---------------------------------------------------------------------------------------

    private static String requireString(Map<?, ?> body, String field, String where) {
        Object value = body.get(field);
        if (value == null) {
            throw new IllegalArgumentException(where + "." + field + " is missing");
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException(where + "." + field + " must not be blank");
        }
        return text;
    }

    private static double requireDouble(Map<?, ?> body, String field, String where) {
        return requireNumber(body, field, where).doubleValue();
    }

    private static long requireLong(Map<?, ?> body, String field, String where) {
        return requireNumber(body, field, where).longValue();
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

    private static Integer optionalInt(Map<?, ?> body, String field, String where) {
        Long value = optionalLong(body, field, where);
        return value == null ? null : value.intValue();
    }

    private static Long optionalLong(Map<?, ?> body, String field, String where) {
        Object value = body.get(field);
        if (value == null) {
            return null;
        }
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException(
                    where + "." + field + " must be a number, but was " + describe(value));
        }
        return number.longValue();
    }

    private static Double optionalDouble(Map<?, ?> body, String field, String where) {
        Object value = body.get(field);
        if (value == null) {
            return null;
        }
        return toDouble(value, where + "." + field);
    }

    private static double toDouble(Object value, String where) {
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException(
                    where + " must be a number, but was " + describe(value));
        }
        return number.doubleValue();
    }

    private static String allRarities() {
        List<String> names = new ArrayList<>();
        for (Rarity rarity : Rarity.values()) {
            names.add(rarity.name());
        }
        return String.join(", ", names);
    }

    private static String describe(Object value) {
        return value == null ? "absent" : value.getClass().getSimpleName() + " " + value;
    }
}
