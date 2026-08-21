package rpg.core.classes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import rpg.core.config.ConfigSchema;
import rpg.core.config.SchemaValidator;
import rpg.core.session.CharacterClass;
import rpg.core.stats.Attribute;

/**
 * Builds the raw nested map that {@code classes.yml} produces, so a test can mutate one thing and
 * assert that the binding refuses it.
 *
 * <p>Deliberately a mutable map of plain values, not a builder of typed objects: the promises under
 * test are exactly the ones that stand between untyped configuration and typed definition.
 */
final class ClassConfigFixture {

    private ClassConfigFixture() {}

    private static final java.nio.file.Path SOURCE = java.nio.file.Path.of("classes.yml");

    /**
     * Goes through {@code SchemaValidator} and then the binder - exactly the route the loader takes.
     * Testing the binder alone would skip the "field missing" case, which is the one an operator hits
     * most often. Same reasoning as {@code ProgressionConfigSchemaTest} in B06.
     */
    static ClassConfig bind(Map<String, Object> classes) throws Exception {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("classes", classes);
        ConfigSchema<ClassConfig> schema = ClassConfigSchema.schema();
        return schema.bind(SchemaValidator.validate(SOURCE, document, schema));
    }

    /** A configuration that must load: three classes, ladders of 5/6, 6/6 and 7/7. */
    static Map<String, Object> valid() {
        Map<String, Object> classes = new LinkedHashMap<>();
        classes.put("WARRIOR", warrior());
        classes.put("ROGUE", rogue());
        classes.put("MAGE", mage());
        return classes;
    }

    static Map<String, Object> warrior() {
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("display-name-key", "class.warrior.name");
        block.put("menu-material", "NETHERITE_SWORD");
        block.put("base-stats", eight(40.0, 4.0, 12.0, 2.0, 0.5, 0.0, 0.0, 0.0));
        block.put("growth", eight(9.7, 1.5, 0.9, 0.7, 0.1, 0.0, 0.0, 0.0));
        List<Object> armor = new ArrayList<>();
        armor.add(armorTier("LEATHER", null, 1, 60.0, 6.0, 18.0, 0.000));
        armor.add(armorTier("COPPER", null, 15, 280.0, 40.0, 35.0, 0.001));
        armor.add(armorTier("IRON", null, 29, 600.0, 90.0, 65.0, 0.002));
        armor.add(armorTier("DIAMOND", null, 42, 975.0, 150.0, 95.0, 0.004));
        armor.add(armorTier("NETHERITE", null, 55, 1385.0, 205.0, 130.0, 0.005));
        block.put("armor-ladder", armor);
        List<Object> weapon = new ArrayList<>();
        weapon.add(weaponTier("WOODEN_SWORD", 1, 3.0, 0.5, 0.00, 0.00));
        weapon.add(weaponTier("STONE_SWORD", 13, 16.0, 3.0, 0.12, 0.04));
        weapon.add(weaponTier("COPPER_SWORD", 24, 35.0, 6.0, 0.24, 0.08));
        weapon.add(weaponTier("IRON_SWORD", 34, 60.0, 10.0, 0.36, 0.12));
        weapon.add(weaponTier("DIAMOND_SWORD", 45, 85.0, 14.0, 0.48, 0.16));
        weapon.add(weaponTier("NETHERITE_SWORD", 55, 105.0, 19.0, 0.60, 0.20));
        block.put("weapon-ladder", weapon);
        block.put("abilities", warriorAbilities());
        return block;
    }

    static List<Object> warriorAbilities() {
        List<Object> abilities = new ArrayList<>();
        abilities.add(ability("warrior.rage", "PASSIVE", false, 1));
        abilities.add(ability("warrior.shield", "ACTIVE", false, 5));
        abilities.add(ability("warrior.leap", "ACTIVE", false, 15));
        abilities.add(ability("warrior.lifesteal", "PASSIVE", false, 25));
        abilities.add(ability("warrior.whirl", "ACTIVE", false, 35));
        abilities.add(ability("warrior.call-of-the-berserker", "ACTIVE", true, 45));
        return abilities;
    }

    static Map<String, Object> rogue() {
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("display-name-key", "class.rogue.name");
        block.put("menu-material", "GOLDEN_SWORD");
        block.put("base-stats", eight(35.0, 2.0, 16.0, 2.0, 1.0, 0.0, 0.0, 0.0));
        block.put("growth", eight(7.2, 0.9, 1.4, 0.6, 0.2, 0.0, 0.0, 0.0));
        List<Object> armor = new ArrayList<>();
        armor.add(armorTier("LEATHER", null, 1, 50.0, 3.0, 24.0, 0.000));
        armor.add(armorTier("GOLDEN", null, 13, 170.0, 19.0, 50.0, 0.006));
        armor.add(armorTier("CHAINMAIL", null, 24, 350.0, 40.0, 85.0, 0.012));
        armor.add(trimmedArmorTier("CHAINMAIL", "COPPER", "RIB", 34, 575.0, 70.0, 120.0, 0.018));
        armor.add(trimmedArmorTier("CHAINMAIL", "AMETHYST", "SILENCE", 45, 800.0, 100.0, 170.0, 0.024));
        armor.add(trimmedArmorTier("CHAINMAIL", "NETHERITE", "VEX", 55, 1050.0, 130.0, 220.0, 0.030));
        block.put("armor-ladder", armor);
        List<Object> weapon = new ArrayList<>();
        weapon.add(weaponTier("WOODEN_SWORD", 1, 3.0, 1.0, 0.00, 0.00));
        weapon.add(weaponTier("STONE_SWORD", 13, 13.0, 5.0, 0.40, 0.06));
        weapon.add(weaponTier("GOLDEN_SWORD", 24, 30.0, 10.0, 0.80, 0.12));
        weapon.add(weaponTier("IRON_SWORD", 34, 45.0, 17.0, 1.20, 0.18));
        weapon.add(weaponTier("DIAMOND_SWORD", 45, 65.0, 25.0, 1.60, 0.24));
        weapon.add(weaponTier("NETHERITE_SWORD", 55, 85.0, 32.0, 2.00, 0.30));
        block.put("weapon-ladder", weapon);
        block.put("abilities", new ArrayList<>());
        return block;
    }

    static Map<String, Object> mage() {
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("display-name-key", "class.mage.name");
        block.put("menu-material", "NETHERITE_SPEAR");
        block.put("base-stats", eight(30.0, 2.0, 20.0, 1.0, 2.0, 0.0, 0.0, 0.0));
        block.put("growth", eight(6.0, 0.8, 2.3, 0.2, 0.7, 0.0, 0.0, 0.0));
        List<Object> armor = new ArrayList<>();
        armor.add(armorTier("LEATHER", 0x4a4a52, 1, 40.0, 3.0, 30.0, 0.000));
        armor.add(armorTier("LEATHER", 0x1f3a93, 11, 120.0, 13.0, 60.0, 0.002));
        armor.add(armorTier("LEATHER", 0x6b3fa0, 20, 240.0, 30.0, 100.0, 0.005));
        armor.add(armorTier("LEATHER", 0xb5299b, 29, 380.0, 45.0, 160.0, 0.007));
        armor.add(armorTier("LEATHER", 0xe8952f, 38, 525.0, 65.0, 210.0, 0.010));
        armor.add(armorTier("LEATHER", 0x21d4c4, 46, 700.0, 85.0, 270.0, 0.012));
        armor.add(armorTier("LEATHER", 0xf5f2e8, 55, 875.0, 110.0, 340.0, 0.015));
        block.put("armor-ladder", armor);
        List<Object> weapon = new ArrayList<>();
        weapon.add(weaponTier("WOODEN_SPEAR", 1, 2.0, 3.0, 0.00, 0.00));
        weapon.add(weaponTier("STONE_SPEAR", 11, 5.0, 13.0, 0.04, 0.07));
        weapon.add(weaponTier("COPPER_SPEAR", 20, 9.0, 30.0, 0.08, 0.13));
        weapon.add(weaponTier("GOLDEN_SPEAR", 29, 14.0, 45.0, 0.12, 0.20));
        weapon.add(weaponTier("IRON_SPEAR", 38, 20.0, 65.0, 0.16, 0.27));
        weapon.add(weaponTier("DIAMOND_SPEAR", 46, 25.0, 85.0, 0.20, 0.33));
        weapon.add(weaponTier("NETHERITE_SPEAR", 55, 32.0, 105.0, 0.24, 0.40));
        block.put("weapon-ladder", weapon);
        block.put("abilities", new ArrayList<>());
        return block;
    }

    // ---- builders ------------------------------------------------------------------------------

    static Map<String, Object> eight(
            double health,
            double defense,
            double mana,
            double physical,
            double magic,
            double attackSpeed,
            double movementSpeed,
            double cooldown) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(Attribute.HEALTH.key(), health);
        map.put(Attribute.DEFENSE.key(), defense);
        map.put(Attribute.MANA.key(), mana);
        map.put(Attribute.PHYSICAL_DAMAGE.key(), physical);
        map.put(Attribute.MAGIC_DAMAGE.key(), magic);
        map.put(Attribute.ATTACK_SPEED.key(), attackSpeed);
        map.put(Attribute.MOVEMENT_SPEED.key(), movementSpeed);
        map.put(Attribute.ABILITY_COOLDOWN.key(), cooldown);
        return map;
    }

    static Map<String, Object> armorTier(
            String material,
            Integer color,
            int requiredLevel,
            double health,
            double defense,
            double mana,
            double movementSpeed) {
        Map<String, Object> tier = new LinkedHashMap<>();
        tier.put("material", material);
        if (color != null) {
            tier.put("color", color);
        }
        tier.put("required-level", requiredLevel);
        Map<String, Object> values = new LinkedHashMap<>();
        values.put(Attribute.HEALTH.key(), health);
        values.put(Attribute.DEFENSE.key(), defense);
        values.put(Attribute.MANA.key(), mana);
        values.put(Attribute.MOVEMENT_SPEED.key(), movementSpeed);
        tier.put("values", values);
        tier.put("cost", new LinkedHashMap<String, Object>());
        return tier;
    }

    static Map<String, Object> trimmedArmorTier(
            String material,
            String trimMaterial,
            String trimPattern,
            int requiredLevel,
            double health,
            double defense,
            double mana,
            double movementSpeed) {
        Map<String, Object> tier =
                armorTier(material, null, requiredLevel, health, defense, mana, movementSpeed);
        tier.put("trim-material", trimMaterial);
        tier.put("trim-pattern", trimPattern);
        return tier;
    }

    static Map<String, Object> weaponTier(
            String material,
            int requiredLevel,
            double physical,
            double magic,
            double attackSpeed,
            double cooldown) {
        Map<String, Object> tier = new LinkedHashMap<>();
        tier.put("material", material);
        tier.put("required-level", requiredLevel);
        Map<String, Object> values = new LinkedHashMap<>();
        values.put(Attribute.PHYSICAL_DAMAGE.key(), physical);
        values.put(Attribute.MAGIC_DAMAGE.key(), magic);
        values.put(Attribute.ATTACK_SPEED.key(), attackSpeed);
        values.put(Attribute.ABILITY_COOLDOWN.key(), cooldown);
        tier.put("values", values);
        tier.put("cost", new LinkedHashMap<String, Object>());
        return tier;
    }

    static Map<String, Object> ability(String id, String kind, boolean unique, int unlockLevel) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("id", id);
        entry.put("kind", kind);
        if (unique) {
            entry.put("unique", true);
        }
        entry.put("unlock-level", unlockLevel);
        return entry;
    }

    /** Convenience: the armour ladder of one class inside a raw configuration. */
    @SuppressWarnings("unchecked")
    static List<Object> armorLadderOf(Map<String, Object> classes, CharacterClass id) {
        Map<String, Object> block = (Map<String, Object>) classes.get(id.name());
        return (List<Object>) block.get("armor-ladder");
    }

    @SuppressWarnings("unchecked")
    static List<Object> weaponLadderOf(Map<String, Object> classes, CharacterClass id) {
        Map<String, Object> block = (Map<String, Object>) classes.get(id.name());
        return (List<Object>) block.get("weapon-ladder");
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> blockOf(Map<String, Object> classes, CharacterClass id) {
        return (Map<String, Object>) classes.get(id.name());
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> tierAt(List<Object> ladder, int index) {
        return (Map<String, Object>) ladder.get(index - 1);
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> valuesOf(Map<String, Object> tier) {
        return (Map<String, Object>) tier.get("values");
    }
}
