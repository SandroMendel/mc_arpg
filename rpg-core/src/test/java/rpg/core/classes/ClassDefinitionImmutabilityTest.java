package rpg.core.classes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.session.CharacterClass;
import rpg.core.stats.Attribute;

/**
 * T135: eine geladene Definition ist unveränderlich und wird von allen geteilt.
 *
 * <p>Drei Objekte für den ganzen Server, nicht drei je Spieler. Bei 200 gleichzeitigen Spielern ist das
 * der Unterschied zwischen drei Definitionen und sechshundert - und weil sie geteilt sind, wäre jede
 * Veränderlichkeit ein Datenrennen, das erst unter Last auffällt (Prinzip I).
 *
 * <p>Geprüft wird die Zusage, nicht die Umsetzung: der Aufrufer bekommt keinen Griff, mit dem er die
 * Konfiguration von innen ändern könnte.
 */
class ClassDefinitionImmutabilityTest {

    @Test
    @DisplayName("jede Abfrage liefert dasselbe Objekt - nicht eine Kopie je Spieler")
    void everyLookupReturnsTheSameInstance() {
        ClassConfig config = config();

        CharacterClassDefinition first = config.definitions().get(CharacterClass.WARRIOR);
        CharacterClassDefinition second = config.definitions().get(CharacterClass.WARRIOR);

        assertThat(first).isSameAs(second);
    }

    @Test
    @DisplayName("die Definitionskarte lässt sich von außen nicht verändern")
    void theDefinitionMapIsUnmodifiable() {
        ClassConfig config = config();
        Map<CharacterClass, CharacterClassDefinition> definitions = config.definitions();

        assertThatThrownBy(() -> definitions.remove(CharacterClass.WARRIOR))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(
                        () ->
                                definitions.put(
                                        CharacterClass.WARRIOR,
                                        config.definitions().get(CharacterClass.MAGE)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("die Stufen einer Leiter lassen sich nicht von außen austauschen")
    void ladderTiersAreUnmodifiable() {
        EquipmentLadder ladder =
                config().definitions().get(CharacterClass.WARRIOR).ladder(LadderSlot.ARMOR);

        assertThatThrownBy(() -> ladder.tiers().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("die Fähigkeitsbindungen lassen sich nicht von außen erweitern")
    void abilityBindingsAreUnmodifiable() {
        List<AbilityBinding> abilities =
                config().definitions().get(CharacterClass.WARRIOR).unlockedAt(60);

        assertThatThrownBy(
                        () ->
                                abilities.add(
                                        new AbilityBinding("smuggled", AbilityKind.ACTIVE, false, 1)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("eine Werteliste, die der Aufrufer nachträglich ändert, ändert die Stufe nicht")
    void aTierKeepsItsOwnCopyOfTheValues() {
        // Der teuerste Fall wäre kein Wurf, sondern ein stiller: der Aufrufer behält die Karte, die er
        // hineingegeben hat, ändert sie später - und die Stufe ändert sich mit.
        Map<Attribute, Double> values = new java.util.EnumMap<>(Attribute.class);
        for (Attribute carried : LadderSlot.ARMOR.carried()) {
            values.put(carried, 10.0);
        }
        EquipmentTier tier =
                EquipmentTier.of(
                        1,
                        LadderSlot.ARMOR,
                        values,
                        TierAppearance.ofMaterial("LEATHER"),
                        1,
                        Map.of());
        double before = tier.valueOf(Attribute.HEALTH);

        values.put(Attribute.HEALTH, 9999.0);

        assertThat(tier.valueOf(Attribute.HEALTH))
                .as("die Stufe hält ihre eigene Kopie")
                .isEqualTo(before);
    }

    @Test
    @DisplayName("die Registry gibt allen Charakteren dieselbe Definition")
    void theRegistrySharesOneDefinitionAcrossCharacters() {
        ClassRegistry registry = new ClassRegistry(config(), id -> 1);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        assertThat(registry.definition(CharacterClass.WARRIOR))
                .as("drei Objekte für den ganzen Server, nicht drei je Spieler")
                .isSameAs(registry.definition(CharacterClass.WARRIOR));
        assertThat(registry.unlockedFor(CharacterClass.WARRIOR, first))
                .isEqualTo(registry.unlockedFor(CharacterClass.WARRIOR, second));
    }

    // --- fixtures ---

    private static ClassConfig config() {
        Map<CharacterClass, CharacterClassDefinition> definitions =
                new java.util.EnumMap<>(CharacterClass.class);
        for (CharacterClass id : CharacterClass.values()) {
            definitions.put(id, definition(id));
        }
        return ClassConfig.of(definitions);
    }

    private static CharacterClassDefinition definition(CharacterClass id) {
        return new CharacterClassDefinition(
                id,
                switch (id) {
                    case WARRIOR -> ClassMessageKeys.WARRIOR_NAME;
                    case MAGE -> ClassMessageKeys.MAGE_NAME;
                    case ROGUE -> ClassMessageKeys.ROGUE_NAME;
                },
                "STONE",
                ClassBaseStats.of(new double[Attribute.count()]),
                ClassGrowth.of(new double[Attribute.count()]),
                ladder(LadderSlot.ARMOR, armorFamilyOf(id)),
                ladder(LadderSlot.WEAPON, new String[] {"WOODEN_SWORD", "IRON_SWORD"}),
                id == CharacterClass.WARRIOR
                        ? List.of(
                                new AbilityBinding("rage", AbilityKind.PASSIVE, false, 1),
                                new AbilityBinding("lifesteal", AbilityKind.PASSIVE, false, 10),
                                new AbilityBinding("shield", AbilityKind.ACTIVE, false, 1),
                                new AbilityBinding("leap", AbilityKind.ACTIVE, false, 5),
                                new AbilityBinding("whirl", AbilityKind.ACTIVE, false, 20),
                                new AbilityBinding("call", AbilityKind.ACTIVE, true, 30))
                        : List.of());
    }

    /** Außer dem gemeinsamen Einstiegsmaterial gehört ein Rüstungssatz einer Klasse (V11). */
    private static String[] armorFamilyOf(CharacterClass id) {
        return switch (id) {
            case WARRIOR -> new String[] {"LEATHER", "IRON"};
            case ROGUE -> new String[] {"LEATHER", "GOLDEN"};
            case MAGE -> new String[] {"LEATHER", "TURTLE"};
        };
    }

    private static EquipmentLadder ladder(LadderSlot slot, String[] materials) {
        List<EquipmentTier> tiers = new java.util.ArrayList<>(materials.length);
        for (int index = 1; index <= materials.length; index++) {
            Map<Attribute, Double> values = new java.util.EnumMap<>(Attribute.class);
            for (Attribute carried : slot.carried()) {
                values.put(carried, index * 10.0);
            }
            tiers.add(
                    EquipmentTier.of(
                            index,
                            slot,
                            values,
                            TierAppearance.ofMaterial(materials[index - 1]),
                            index == 1 ? 1 : index * 5,
                            Map.of()));
        }
        return EquipmentLadder.of(slot, tiers);
    }
}
