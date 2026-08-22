package rpg.core.classes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import rpg.core.session.CharacterClass;
import rpg.core.stats.Attribute;
import rpg.core.stats.AttributeDefinition;

/**
 * T021 bis T038 - die Zusagen V1 bis V18 aus {@code contracts/class-config.md}.
 *
 * <p>Jeder Fehlerfall prueft die <b>Meldung</b>. Ein Betreiber, der sechs Leitern mit bis zu sieben
 * Stufen bearbeitet, muss erfahren, welche Stufe gemeint ist - „ungueltige Konfiguration" schickt ihn
 * durch alle sechs.
 *
 * <p>Jeder Lauf geht durch {@code SchemaValidator} und dann den Binder, also genau den Weg des
 * Laders.
 */
class ClassConfigValidationTest {

    @Test
    @DisplayName("die gueltige Konfiguration laedt: drei Klassen, Leitern 5/6, 6/6, 7/7")
    void validConfigurationLoads() throws Exception {
        ClassConfig config = ClassConfigFixture.bind(ClassConfigFixture.valid());

        assertThat(config.definitions()).hasSize(3);
        assertThat(config.definition(CharacterClass.WARRIOR).armorLadder().length()).isEqualTo(5);
        assertThat(config.definition(CharacterClass.WARRIOR).weaponLadder().length()).isEqualTo(6);
        assertThat(config.definition(CharacterClass.ROGUE).armorLadder().length()).isEqualTo(6);
        assertThat(config.definition(CharacterClass.MAGE).armorLadder().length()).isEqualTo(7);
        assertThat(config.definition(CharacterClass.MAGE).weaponLadder().length()).isEqualTo(7);
    }

    @Nested
    @DisplayName("V1 - die Menge der Klassen steht im Code")
    class ClassSet {

        @Test
        @DisplayName("eine vierte Klassen-ID bricht ab und nennt die ID (FR-005, SC-008)")
        void unknownClassIdIsNamed() {
            Map<String, Object> classes = ClassConfigFixture.valid();
            classes.put("PALADIN", ClassConfigFixture.warrior());

            assertThatThrownBy(() -> ClassConfigFixture.bind(classes))
                    .hasMessageContaining("unknown class id 'PALADIN'")
                    .hasMessageContaining("enum value plus a migration");
        }

        @Test
        @DisplayName("eine fehlende bekannte Klasse bricht ab und nennt sie")
        void missingClassIsNamed() {
            Map<String, Object> classes = ClassConfigFixture.valid();
            classes.remove("MAGE");

            assertThatThrownBy(() -> ClassConfigFixture.bind(classes))
                    .hasMessageContaining("'MAGE' is missing");
        }
    }

    @Nested
    @DisplayName("V2 - alle acht Attribute sind Pflicht, auch die mit Null")
    class EightAttributes {

        @Test
        @DisplayName("ein fehlender Basiswert nennt das Attribut (FR-001)")
        void missingBaseStatNamesTheAttribute() {
            Map<String, Object> classes = ClassConfigFixture.valid();
            eightOf(classes, CharacterClass.WARRIOR, "base-stats")
                    .remove(Attribute.MOVEMENT_SPEED.key());

            assertThatThrownBy(() -> ClassConfigFixture.bind(classes))
                    .hasMessageContaining("WARRIOR.base-stats")
                    .hasMessageContaining("missing 'movementSpeed'")
                    .hasMessageContaining("including the ones that are zero");
        }

        @Test
        @DisplayName("eine fehlende Zuwachsrate nennt das Attribut (FR-002)")
        void missingGrowthNamesTheAttribute() {
            Map<String, Object> classes = ClassConfigFixture.valid();
            eightOf(classes, CharacterClass.MAGE, "growth").remove(Attribute.ABILITY_COOLDOWN.key());

            assertThatThrownBy(() -> ClassConfigFixture.bind(classes))
                    .hasMessageContaining("MAGE.growth")
                    .hasMessageContaining("missing 'abilityCooldown'");
        }

        @Test
        @DisplayName("eine negative Zuwachsrate bricht ab - ein Aufstieg nimmt nichts weg")
        void negativeGrowthIsRejected() {
            Map<String, Object> classes = ClassConfigFixture.valid();
            eightOf(classes, CharacterClass.ROGUE, "growth").put(Attribute.HEALTH.key(), -1.0);

            assertThatThrownBy(() -> ClassConfigFixture.bind(classes))
                    .hasMessageContaining("must not be negative");
        }
    }

    @Nested
    @DisplayName("V3 bis V6 - Aufbau einer Leiter")
    class LadderShape {

        @Test
        @DisplayName("V3: eine Leiter mit einer Stufe bricht ab (FR-013)")
        void singleTierLadder() {
            Map<String, Object> classes = ClassConfigFixture.valid();
            List<Object> armor = ClassConfigFixture.armorLadderOf(classes, CharacterClass.WARRIOR);
            while (armor.size() > 1) {
                armor.remove(armor.size() - 1);
            }

            assertThatThrownBy(() -> ClassConfigFixture.bind(classes))
                    .hasMessageContaining("at least 2 tiers");
        }

        @Test
        @DisplayName("V4: ein fehlendes Attribut einer Stufe bricht ab (FR-015)")
        void missingTierAttribute() {
            Map<String, Object> classes = ClassConfigFixture.valid();
            Map<String, Object> tier =
                    ClassConfigFixture.tierAt(
                            ClassConfigFixture.armorLadderOf(classes, CharacterClass.WARRIOR), 3);
            ClassConfigFixture.valuesOf(tier).remove(Attribute.MANA.key());

            assertThatThrownBy(() -> ClassConfigFixture.bind(classes))
                    .hasMessageContaining("missing value for mana");
        }

        @Test
        @DisplayName("V4: ein Attribut des anderen Slots bricht ab (FR-015)")
        void foreignTierAttribute() {
            Map<String, Object> classes = ClassConfigFixture.valid();
            Map<String, Object> tier =
                    ClassConfigFixture.tierAt(
                            ClassConfigFixture.armorLadderOf(classes, CharacterClass.WARRIOR), 2);
            ClassConfigFixture.valuesOf(tier).put(Attribute.PHYSICAL_DAMAGE.key(), 10.0);

            assertThatThrownBy(() -> ClassConfigFixture.bind(classes))
                    .hasMessageContaining("physicalDamage")
                    .hasMessageContaining("does not belong to slot ARMOR");
        }

        @Test
        @DisplayName("V5: eine nicht steigende Leiter nennt die Stufe (FR-017)")
        void nonIncreasingLadderNamesTheTier() {
            Map<String, Object> classes = ClassConfigFixture.valid();
            Map<String, Object> tier =
                    ClassConfigFixture.tierAt(
                            ClassConfigFixture.armorLadderOf(classes, CharacterClass.WARRIOR), 3);
            ClassConfigFixture.valuesOf(tier).put(Attribute.HEALTH.key(), 60.0);

            assertThatThrownBy(() -> ClassConfigFixture.bind(classes))
                    .hasMessageContaining("tier 3")
                    .hasMessageContaining("health must exceed tier 2");
        }

        @Test
        @DisplayName("V6: nicht steigende Levelanforderungen brechen ab (FR-018)")
        void nonIncreasingLevelRequirement() {
            Map<String, Object> classes = ClassConfigFixture.valid();
            ClassConfigFixture.tierAt(
                            ClassConfigFixture.weaponLadderOf(classes, CharacterClass.ROGUE), 4)
                    .put("required-level", 13);

            assertThatThrownBy(() -> ClassConfigFixture.bind(classes))
                    .hasMessageContaining("required-level");
        }

        @Test
        @DisplayName("V6: Stufe 1 muss auf Level 1 tragbar sein")
        void firstTierMustBeWearableAtLevelOne() {
            Map<String, Object> classes = ClassConfigFixture.valid();
            ClassConfigFixture.tierAt(
                            ClassConfigFixture.armorLadderOf(classes, CharacterClass.MAGE), 1)
                    .put("required-level", 5);

            assertThatThrownBy(() -> ClassConfigFixture.bind(classes))
                    .hasMessageContaining("tier 1 must require level 1");
        }
    }

    @Nested
    @DisplayName("V7 bis V11 - Sichtbarkeit und Familientrennung")
    class Appearance {

        @Test
        @DisplayName("V7: zwei ununterscheidbare Stufen brechen ab (FR-016, SC-013)")
        void indistinguishableTiers() {
            Map<String, Object> classes = ClassConfigFixture.valid();
            // Dem Mage die Farbe der dritten Stufe auf die der zweiten setzen.
            ClassConfigFixture.tierAt(
                            ClassConfigFixture.armorLadderOf(classes, CharacterClass.MAGE), 3)
                    .put("color", 0x1f3a93);

            assertThatThrownBy(() -> ClassConfigFixture.bind(classes))
                    .hasMessageContaining("indistinguishable from tier 2")
                    .hasMessageContaining("Set a colour or a trim");
        }

        @Test
        @DisplayName("V8: zwei benachbarte Stufen ohne Marker auf gleichem Material brechen ab (FR-016a)")
        void sameMaterialWithoutMarker() {
            Map<String, Object> classes = ClassConfigFixture.valid();
            List<Object> armor = ClassConfigFixture.armorLadderOf(classes, CharacterClass.MAGE);
            // Beide Farben entfernen: eine einzelne entfernte Farbe macht die Stufe von ihrer
            // gefaerbten Vorstufe ja gerade UNTERSCHEIDBAR. Erst zwei markerlose Nachbarn auf
            // demselben Material sind der Fall, den FR-016a verbietet.
            ClassConfigFixture.tierAt(armor, 3).remove("color");
            ClassConfigFixture.tierAt(armor, 4).remove("color");

            assertThatThrownBy(() -> ClassConfigFixture.bind(classes))
                    .hasMessageContaining("indistinguishable from tier 3")
                    .hasMessageContaining("Set a colour or a trim");
        }

        @Test
        @DisplayName("V8: eine einzelne entfernte Farbe ist erlaubt - undyed Leder sieht anders aus")
        void singleMissingColourIsStillDistinguishable() throws Exception {
            Map<String, Object> classes = ClassConfigFixture.valid();
            ClassConfigFixture.tierAt(
                            ClassConfigFixture.armorLadderOf(classes, CharacterClass.MAGE), 4)
                    .remove("color");

            ClassConfig config = ClassConfigFixture.bind(classes);

            assertThat(
                            config.definition(CharacterClass.MAGE)
                                    .armorLadder()
                                    .tier(4)
                                    .appearance()
                                    .hasColor())
                    .isFalse();
        }

        @Test
        @DisplayName("V9: eine Farbe auf Kettenhemd bricht ab - es ist nicht faerbbar (FR-016b)")
        void colourOnNonDyeableMaterial() {
            Map<String, Object> classes = ClassConfigFixture.valid();
            ClassConfigFixture.tierAt(
                            ClassConfigFixture.armorLadderOf(classes, CharacterClass.ROGUE), 3)
                    .put("color", 0x112233);

            assertThatThrownBy(() -> ClassConfigFixture.bind(classes))
                    .hasMessageContaining("'CHAINMAIL' cannot be dyed")
                    .hasMessageContaining("Use a trim instead");
        }

        @Test
        @DisplayName("V9: eine Farbe auf Leder ist erlaubt - der Mage-Fall")
        void colourOnLeatherIsAllowed() throws Exception {
            ClassConfig config = ClassConfigFixture.bind(ClassConfigFixture.valid());

            assertThat(
                            config.definition(CharacterClass.MAGE)
                                    .armorLadder()
                                    .tier(2)
                                    .appearance()
                                    .hasColor())
                    .isTrue();
        }

        @Test
        @DisplayName("V9: eine Farbe auf einer Waffe bricht ab")
        void colourOnWeaponIsRejected() {
            Map<String, Object> classes = ClassConfigFixture.valid();
            ClassConfigFixture.tierAt(
                            ClassConfigFixture.weaponLadderOf(classes, CharacterClass.WARRIOR), 2)
                    .put("color", 0x112233);

            assertThatThrownBy(() -> ClassConfigFixture.bind(classes))
                    .hasMessageContaining("a weapon cannot be dyed");
        }

        @Test
        @DisplayName("V10: halber Trim bricht ab - beide Felder oder keines (FR-016)")
        void halfTrimIsRejected() {
            Map<String, Object> classes = ClassConfigFixture.valid();
            ClassConfigFixture.tierAt(
                            ClassConfigFixture.armorLadderOf(classes, CharacterClass.ROGUE), 4)
                    .remove("trim-pattern");

            assertThatThrownBy(() -> ClassConfigFixture.bind(classes))
                    .hasMessageContaining("must be set together");
        }

        @Test
        @DisplayName("V11: derselbe Ruestungssatz in zwei Klassen bricht ab (FR-016c, SC-012)")
        void sharedArmorSetIsRejected() {
            Map<String, Object> classes = ClassConfigFixture.valid();
            // Dem Rogue Kupfer geben, das dem Warrior gehoert.
            ClassConfigFixture.tierAt(
                            ClassConfigFixture.armorLadderOf(classes, CharacterClass.ROGUE), 2)
                    .put("material", "COPPER");

            assertThatThrownBy(() -> ClassConfigFixture.bind(classes))
                    .hasMessageContaining("armor set 'COPPER' appears in both")
                    .hasMessageContaining("two classes look the same");
        }

        @Test
        @DisplayName("V11: Leder als gemeinsames Einstiegsmaterial ist ausgenommen")
        void sharedEntryMaterialIsExempt() throws Exception {
            ClassConfig config = ClassConfigFixture.bind(ClassConfigFixture.valid());

            // Alle drei starten auf Leder, und der Mage bleibt dort.
            for (CharacterClass id : CharacterClass.values()) {
                assertThat(config.definition(id).armorLadder().tier(1).appearance().material())
                        .isEqualTo(ClassConfig.SHARED_ENTRY_ARMOR);
            }
        }

        @Test
        @DisplayName("V11 gilt NICHT fuer Waffen - Warrior und Rogue teilen die Schwerter")
        void weaponsMayBeShared() throws Exception {
            ClassConfig config = ClassConfigFixture.bind(ClassConfigFixture.valid());

            assertThat(
                            config.definition(CharacterClass.WARRIOR)
                                    .weaponLadder()
                                    .top()
                                    .appearance()
                                    .material())
                    .isEqualTo(
                            config.definition(CharacterClass.ROGUE)
                                    .weaponLadder()
                                    .top()
                                    .appearance()
                                    .material());
        }
    }

    @Nested
    @DisplayName("V13 und V14 - die Caps aus ADR-008")
    class Caps {

        @Test
        @DisplayName("ein Endwert ueber dem Cap bricht ab und nennt Klasse und Attribut (FR-008)")
        void valueAboveCapIsRejected() throws Exception {
            Map<String, Object> classes = ClassConfigFixture.valid();
            Map<String, Object> top =
                    ClassConfigFixture.tierAt(
                            ClassConfigFixture.armorLadderOf(classes, CharacterClass.WARRIOR), 5);
            ClassConfigFixture.valuesOf(top).put(Attribute.HEALTH.key(), 9000.0);
            ClassConfig config = ClassConfigFixture.bind(classes);

            assertThatThrownBy(() -> config.validateAgainstCaps(Caps::definitionOf, 60))
                    .hasMessageContaining("WARRIOR")
                    .hasMessageContaining("health")
                    .hasMessageContaining("above the cap");
        }

        @Test
        @DisplayName("die ausgelieferten Werte bleiben unter allen Caps")
        void shippedValuesStayUnderTheCaps() throws Exception {
            ClassConfig config = ClassConfigFixture.bind(ClassConfigFixture.valid());

            config.validateAgainstCaps(Caps::definitionOf, 60);
        }

        /** Die Werte aus {@code stats.yml}, hier als Vorgabe eingesetzt. */
        private static AttributeDefinition definitionOf(Attribute attribute) {
            return switch (attribute) {
                case HEALTH -> new AttributeDefinition(attribute, 100.0, 1.0, 2000.0, 0.0);
                case HEALTH_REGEN -> new AttributeDefinition(attribute, 0.0, 0.0, 40.0, 0.0);
                case DEFENSE -> new AttributeDefinition(attribute, 0.0, 0.0, 300.0, 0.0);
                case MANA -> new AttributeDefinition(attribute, 50.0, 0.0, 500.0, 0.0);
                case MANA_REGEN -> new AttributeDefinition(attribute, 0.0, 0.0, 20.0, 0.0);
                case PHYSICAL_DAMAGE -> new AttributeDefinition(attribute, 5.0, 0.0, 150.0, 0.0);
                case MAGIC_DAMAGE -> new AttributeDefinition(attribute, 5.0, 0.0, 150.0, 0.0);
                case ATTACK_SPEED -> new AttributeDefinition(attribute, 4.0, 0.0, 1024.0, 0.50);
                case MOVEMENT_SPEED -> new AttributeDefinition(attribute, 0.1, 0.0, 1.0, 0.30);
                case ABILITY_COOLDOWN -> new AttributeDefinition(attribute, 0.0, 0.0, 0.40, 0.0);
            };
        }
    }

    @Nested
    @DisplayName("V15 bis V18 - Fähigkeiten und Kosten")
    class AbilitiesAndCost {

        @Test
        @DisplayName("V15: fuenf Faehigkeiten brechen ab (FR-041)")
        void fiveAbilitiesAreRejected() {
            Map<String, Object> classes = ClassConfigFixture.valid();
            List<Object> abilities = ClassConfigFixture.warriorAbilities();
            abilities.remove(0);
            ClassConfigFixture.blockOf(classes, CharacterClass.WARRIOR).put("abilities", abilities);

            assertThatThrownBy(() -> ClassConfigFixture.bind(classes))
                    .hasMessageContaining("must be empty or exactly 6")
                    .hasMessageContaining("forgotten line");
        }

        @Test
        @DisplayName("V15: eine leere Liste wird angenommen, solange B08 fehlt (FR-045)")
        void emptyAbilitiesAreAccepted() throws Exception {
            ClassConfig config = ClassConfigFixture.bind(ClassConfigFixture.valid());

            assertThat(config.definition(CharacterClass.ROGUE).abilities()).isEmpty();
            assertThat(config.definition(CharacterClass.WARRIOR).abilities()).hasSize(6);
        }

        @Test
        @DisplayName("V15: drei Passive statt zwei brechen ab")
        void wrongActivePassiveSplitIsRejected() {
            Map<String, Object> classes = ClassConfigFixture.valid();
            List<Object> abilities = ClassConfigFixture.warriorAbilities();
            abilities.set(1, ClassConfigFixture.ability("warrior.shield", "PASSIVE", false, 5));
            ClassConfigFixture.blockOf(classes, CharacterClass.WARRIOR).put("abilities", abilities);

            assertThatThrownBy(() -> ClassConfigFixture.bind(classes))
                    .hasMessageContaining("expected 4 active abilities including the unique");
        }

        @Test
        @DisplayName("V16: eine passive Unique bricht ab (FR-041)")
        void passiveUniqueIsRejected() {
            Map<String, Object> classes = ClassConfigFixture.valid();
            List<Object> abilities = ClassConfigFixture.warriorAbilities();
            abilities.set(
                    5, ClassConfigFixture.ability("warrior.call-of-the-berserker", "PASSIVE", true, 45));
            ClassConfigFixture.blockOf(classes, CharacterClass.WARRIOR).put("abilities", abilities);

            assertThatThrownBy(() -> ClassConfigFixture.bind(classes))
                    .hasMessageContaining("must be ACTIVE");
        }

        @Test
        @DisplayName("V16: zwei Unique brechen ab")
        void twoUniquesAreRejected() {
            Map<String, Object> classes = ClassConfigFixture.valid();
            List<Object> abilities = ClassConfigFixture.warriorAbilities();
            abilities.set(1, ClassConfigFixture.ability("warrior.shield", "ACTIVE", true, 5));
            ClassConfigFixture.blockOf(classes, CharacterClass.WARRIOR).put("abilities", abilities);

            assertThatThrownBy(() -> ClassConfigFixture.bind(classes))
                    .hasMessageContaining("at most one unique class ability");
        }

        @Test
        @DisplayName("V17: unlock-level unter 1 bricht ab (FR-042)")
        void unlockLevelBelowOneIsRejected() {
            Map<String, Object> classes = ClassConfigFixture.valid();
            List<Object> abilities = ClassConfigFixture.warriorAbilities();
            abilities.set(0, ClassConfigFixture.ability("warrior.rage", "PASSIVE", false, 0));
            ClassConfigFixture.blockOf(classes, CharacterClass.WARRIOR).put("abilities", abilities);

            assertThatThrownBy(() -> ClassConfigFixture.bind(classes))
                    .hasMessageContaining("must be at least 1");
        }

        @Test
        @DisplayName("V18: ein cost-Block mit unbekanntem Inhalt bricht NICHT ab (FR-021)")
        void unknownCostContentIsPassedThrough() throws Exception {
            Map<String, Object> classes = ClassConfigFixture.valid();
            Map<String, Object> tier =
                    ClassConfigFixture.tierAt(
                            ClassConfigFixture.armorLadderOf(classes, CharacterClass.WARRIOR), 2);
            tier.put("cost", Map.of("coins", 500, "shards", 7, "was-B11-noch-erfindet", "beliebig"));

            ClassConfig config = ClassConfigFixture.bind(classes);

            Map<String, Object> cost =
                    config.definition(CharacterClass.WARRIOR).armorLadder().tier(2).cost();
            assertThat(cost).containsEntry("coins", 500).containsEntry("shards", 7);
            assertThat(cost).containsKey("was-B11-noch-erfindet");
        }

        @Test
        @DisplayName("V18: ein cost-Block, der keine Karte ist, bricht ab")
        void nonMapCostIsRejected() {
            Map<String, Object> classes = ClassConfigFixture.valid();
            ClassConfigFixture.tierAt(
                            ClassConfigFixture.armorLadderOf(classes, CharacterClass.WARRIOR), 2)
                    .put("cost", "500 coins");

            assertThatThrownBy(() -> ClassConfigFixture.bind(classes))
                    .hasMessageContaining("cost must be a map");
        }

        @Test
        @DisplayName("eine fehlende abilities-Liste bricht ab - leer heisst 'noch nicht'")
        void missingAbilitiesSectionIsRejected() {
            Map<String, Object> classes = ClassConfigFixture.valid();
            ClassConfigFixture.blockOf(classes, CharacterClass.MAGE).remove("abilities");

            assertThatThrownBy(() -> ClassConfigFixture.bind(classes))
                    .hasMessageContaining("abilities is missing")
                    .hasMessageContaining("empty list");
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> eightOf(
            Map<String, Object> classes, CharacterClass id, String section) {
        return (Map<String, Object>) ClassConfigFixture.blockOf(classes, id).get(section);
    }
}
