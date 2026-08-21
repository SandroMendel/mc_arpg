package rpg.platform.classes;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import rpg.core.classes.CharacterClassDefinition;
import rpg.core.classes.ClassBaseStats;
import rpg.core.classes.ClassConfig;
import rpg.core.classes.ClassGrowth;
import rpg.core.classes.ClassMessageKeys;
import rpg.core.classes.ClassRegistry;
import rpg.core.classes.EquipmentLadder;
import rpg.core.classes.EquipmentTier;
import rpg.core.classes.LadderSlot;
import rpg.core.classes.TierAppearance;
import rpg.core.message.MapMessages;
import rpg.core.message.MessageKey;
import rpg.core.message.Messages;
import rpg.core.session.CharacterClass;
import rpg.core.stats.Attribute;

/**
 * A minimal but valid class configuration for platform tests.
 *
 * <p>Deliberately not a copy of the shipped {@code classes.yml}: these tests are about menus and
 * listeners, and a two-tier ladder exercises them exactly as well as a seven-tier one. The values
 * themselves are covered by the core tests.
 */
final class PlatformClassFixture {

    private PlatformClassFixture() {}

    static ClassRegistry registry() {
        return new ClassRegistry(config(), id -> 1);
    }

    /**
     * A registry whose warrior points at a material that does not exist - the V12 case.
     *
     * <p>V12 cannot live in {@code rpg-core}: only the running server knows its materials, and
     * Constitution III.1 forbids asking Bukkit from the core. So the check sits where the item is
     * built, and this is how it is provoked.
     */
    static ClassRegistry registryWithMenuMaterial(String menuMaterial) {
        Map<CharacterClass, CharacterClassDefinition> definitions =
                new EnumMap<>(CharacterClass.class);
        definitions.put(
                CharacterClass.WARRIOR,
                definition(CharacterClass.WARRIOR, menuMaterial, "COPPER", "IRON_SWORD"));
        definitions.put(
                CharacterClass.ROGUE,
                definition(CharacterClass.ROGUE, "GOLDEN_SWORD", "GOLDEN", "GOLDEN_SWORD"));
        definitions.put(
                CharacterClass.MAGE,
                definition(CharacterClass.MAGE, "NETHERITE_SPEAR", "CHAINMAIL", "IRON_SPEAR"));
        return new ClassRegistry(ClassConfig.of(definitions), id -> 1);
    }

    static ClassConfig config() {
        Map<CharacterClass, CharacterClassDefinition> definitions =
                new EnumMap<>(CharacterClass.class);
        definitions.put(
                CharacterClass.WARRIOR,
                definition(CharacterClass.WARRIOR, "NETHERITE_SWORD", "COPPER", "IRON_SWORD"));
        definitions.put(
                CharacterClass.ROGUE,
                definition(CharacterClass.ROGUE, "GOLDEN_SWORD", "GOLDEN", "GOLDEN_SWORD"));
        definitions.put(
                CharacterClass.MAGE,
                definition(CharacterClass.MAGE, "NETHERITE_SPEAR", "CHAINMAIL", "IRON_SPEAR"));
        return ClassConfig.of(definitions);
    }

    private static CharacterClassDefinition definition(
            CharacterClass id, String menuMaterial, String armorTop, String weaponTop) {
        return new CharacterClassDefinition(
                id,
                displayNameKeyOf(id),
                menuMaterial,
                ClassBaseStats.of(new double[Attribute.count()]),
                ClassGrowth.of(new double[Attribute.count()]),
                ladder(LadderSlot.ARMOR, "LEATHER", armorTop),
                ladder(LadderSlot.WEAPON, "WOODEN_SWORD", weaponTop),
                List.of());
    }

    private static MessageKey displayNameKeyOf(CharacterClass id) {
        return switch (id) {
            case WARRIOR -> ClassMessageKeys.WARRIOR_NAME;
            case MAGE -> ClassMessageKeys.MAGE_NAME;
            case ROGUE -> ClassMessageKeys.ROGUE_NAME;
        };
    }

    private static EquipmentLadder ladder(LadderSlot slot, String first, String second) {
        List<EquipmentTier> tiers = new ArrayList<>(2);
        tiers.add(tier(1, slot, 10.0, first, 1));
        tiers.add(tier(2, slot, 20.0, second, 10));
        return EquipmentLadder.of(slot, tiers);
    }

    private static EquipmentTier tier(
            int index, LadderSlot slot, double value, String material, int requiredLevel) {
        Map<Attribute, Double> values = new EnumMap<>(Attribute.class);
        for (Attribute carried : slot.carried()) {
            values.put(carried, value);
        }
        return EquipmentTier.of(
                index,
                slot,
                values,
                TierAppearance.ofMaterial(material),
                requiredLevel,
                Map.of());
    }

    /** Every key this block can emit, so a menu never renders a blank name. */
    static Messages messages() {
        Map<String, String> texts = new HashMap<>();
        for (MessageKey key : ClassMessageKeys.all()) {
            texts.put(key.value(), key.value());
        }
        texts.put(ClassMessageKeys.WARRIOR_NAME.value(), "Berserker");
        texts.put(ClassMessageKeys.MAGE_NAME.value(), "Mage");
        texts.put(ClassMessageKeys.ROGUE_NAME.value(), "Rogue");
        texts.put(ClassMessageKeys.SELECTION_TITLE.value(), "Choose your class");
        return new MapMessages(texts);
    }
}
