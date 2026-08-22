package rpg.plugin;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import rpg.core.ability.Ability;
import rpg.core.ability.AbilityConfig;
import rpg.core.ability.AbilityConfigSchema;
import rpg.core.classes.AbilityBinding;
import rpg.core.classes.AbilityKind;
import rpg.core.classes.ClassConfig;
import rpg.core.classes.ClassConfigSchema;
import rpg.core.config.ConfigSchema;
import rpg.core.config.SchemaValidator;
import rpg.core.session.CharacterClass;

/**
 * T112 und T113 - die <b>ausgelieferten</b> {@code abilities.yml} und {@code classes.yml} (SC-006).
 *
 * <p>Gegen die echten Dateien, nicht gegen eine Fixtur. Eine Fixtur belegt, dass das Schema tut, was
 * es soll; dieser Test belegt, dass die Datei, die der Server liest, die achtzehn Fähigkeiten
 * tatsächlich enthält und zueinander passt. Beides ist nötig, und nur das zweite hätte den
 * Tippfehler gefunden.
 *
 * <p><b>Warum in rpg-plugin und nicht in rpg-core:</b> die ausgelieferte Datei und SnakeYAML liegen
 * beide ausserhalb - derselbe Grund, aus dem {@code ShippedClassConfigTest} hier liegt.
 */
class ShippedAbilityConfigTest {

    /** Das Raster, dem alle drei Klassen folgen. Die Unique ist immer die letzte. */
    /**
     * Die ausgelieferte Freischaltung: <b>alles ab Stufe 1</b> (Entscheidung vom 2026-08-22).
     *
     * <p>Vorher stand hier die Leiter 1, 5, 15, 25, 35, 45. Sie war Balancing, keine Mechanik - und
     * die Mechanik „was nicht freigeschaltet ist, belegt nichts" ist davon unberuehrt und wird in
     * {@code AbilityHotbarTest} weiterhin gegen eine gestaffelte Testkonfiguration geprueft. Was hier
     * steht, ist eine Aussage ueber die <em>ausgelieferte Datei</em>, und die hat sich geaendert.
     */
    private static final List<Integer> UNLOCK_LEVELS = List.of(1, 1, 1, 1, 1, 1);

    @Test
    @DisplayName("die ausgelieferte abilities.yml besteht das Schema und enthält achtzehn")
    void theShippedFileHoldsEighteen() throws Exception {
        AbilityConfig config = shippedAbilities();

        assertThat(config.size())
                .as("sechs je Klasse, drei Klassen - keine geht vergessen")
                .isEqualTo(18);
    }

    @Nested
    @DisplayName("SC-006 - je Klasse sechs, darunter genau eine Unique")
    class SixPerClass {

        @Test
        @DisplayName("jede Klasse hat genau sechs Bindungen")
        void everyClassHasSix() throws Exception {
            for (CharacterClass id : CharacterClass.values()) {
                assertThat(bindings(id)).as(id + " hat sechs").hasSize(6);
            }
        }

        @Test
        @DisplayName("jede Klasse hat genau eine Unique")
        void everyClassHasExactlyOneUnique() throws Exception {
            for (CharacterClass id : CharacterClass.values()) {
                List<AbilityBinding> unique =
                        bindings(id).stream().filter(AbilityBinding::unique).toList();

                // Genau eine je Klasse bleibt die Zusage (ADR-022, SC-006). Dass sie die LETZTE
                // Freischaltung war, stand ebenfalls hier - das war eine Aussage ueber die Leiter,
                // und die gibt es seit dem 2026-08-22 nicht mehr.
                assertThat(unique).as(id + " hat genau eine Unique").hasSize(1);
            }
        }

        @Test
        @DisplayName("ADR-025: die Aufteilung ist Inhalt - Warrior und Mage 4+2, der Rogue 3+3")
        void theSplitIsContentAndDiffers() throws Exception {
            assertThat(activeCount(CharacterClass.WARRIOR)).isEqualTo(4);
            assertThat(activeCount(CharacterClass.MAGE)).isEqualTo(4);
            assertThat(activeCount(CharacterClass.ROGUE))
                    .as("die Ausnahme, für die die Zählregel gelockert wurde")
                    .isEqualTo(3);
        }

        @Test
        @DisplayName("die Unique darf passiv sein, und bei zwei von drei Klassen ist sie es")
        void twoOfThreeUniquesArePassive() throws Exception {
            List<AbilityKind> uniqueKinds = new ArrayList<>();
            for (CharacterClass id : CharacterClass.values()) {
                bindings(id).stream()
                        .filter(AbilityBinding::unique)
                        .forEach(binding -> uniqueKinds.add(binding.kind()));
            }

            assertThat(uniqueKinds).filteredOn(kind -> kind == AbilityKind.PASSIVE).hasSize(2);
        }
    }

    @Nested
    @DisplayName("T113 - das Freischaltraster")
    class UnlockLevels {

        @Test
        @DisplayName("alle drei Klassen schalten alles ab Stufe 1 frei")
        void allThreeFollowTheSameGrid() throws Exception {
            for (CharacterClass id : CharacterClass.values()) {
                assertThat(bindings(id).stream().map(AbilityBinding::unlockLevel).sorted().toList())
                        .as(id + " folgt dem Raster")
                        .isEqualTo(UNLOCK_LEVELS);
            }
        }

        @Test
        @DisplayName("auf Stufe 1 sind alle sechs verfügbar, und es kommt spaeter keine dazu")
        void allSixAtLevelOne() throws Exception {
            for (CharacterClass id : CharacterClass.values()) {
                assertThat(unlockedAt(id, 1)).as(id + " auf Stufe 1").hasSize(6);
                assertThat(unlockedAt(id, 45))
                        .as(id + ": auf Stufe 45 ist nichts hinzugekommen")
                        .hasSize(6);
            }
        }
    }

    @Nested
    @DisplayName("Die beiden Dateien passen zueinander")
    class TheTwoFilesAgree {

        @Test
        @DisplayName("jede in classes.yml genannte ID ist in abilities.yml definiert (V25)")
        void everyBoundIdIsDefined() throws Exception {
            AbilityConfig abilities = shippedAbilities();

            for (CharacterClass id : CharacterClass.values()) {
                for (AbilityBinding binding : bindings(id)) {
                    assertThat(abilities.find(binding.abilityId()))
                            .as(id + " nennt " + binding.abilityId())
                            .isPresent();
                }
            }
        }

        @Test
        @DisplayName("die Arten stimmen in beiden Dateien überein (V26)")
        void theKindsAgree() throws Exception {
            AbilityConfig abilities = shippedAbilities();

            for (CharacterClass id : CharacterClass.values()) {
                for (AbilityBinding binding : bindings(id)) {
                    Ability defined = abilities.require(binding.abilityId());
                    assertThat(defined.kind())
                            .as(binding.abilityId() + " ist in beiden Dateien dieselbe Art")
                            .isEqualTo(binding.kind());
                }
            }
        }

        @Test
        @DisplayName("keine definierte Fähigkeit ist ungebunden - achtzehn Definitionen, achtzehn Bindungen")
        void nothingIsDefinedButUnbound() throws Exception {
            List<String> bound = new ArrayList<>();
            for (CharacterClass id : CharacterClass.values()) {
                bindings(id).forEach(binding -> bound.add(binding.abilityId()));
            }

            // Eine definierte, aber nirgends gebundene Fähigkeit wäre kein Startfehler - sie wäre
            // schlimmer: toter Inhalt, den niemand vermisst, weil nichts ihn meldet.
            assertThat(bound).containsExactlyInAnyOrderElementsOf(shippedAbilities().abilities().keySet());
        }
    }

    @Nested
    @DisplayName("Die Hotbar-Belegung folgt aus den Bindungen, nicht aus einer Tabelle")
    class Hotbar {

        @Test
        @DisplayName("Warrior sieben belegte Slots, Rogue sieben, Mage acht")
        void theOccupiedSlotsFollowFromTheLoadout() throws Exception {
            // Waffe + aktive Fähigkeiten + Marker passiver Fähigkeiten. Seit JEDE Passive einen
            // Marker trägt, sind das mehr als vorher: ein Spieler soll seinen Fähigkeiten ansehen
            // können, dass er sie hat - auch denen, die er nie anklickt.
            assertThat(occupiedSlots(CharacterClass.WARRIOR)).isEqualTo(7);
            assertThat(occupiedSlots(CharacterClass.ROGUE)).isEqualTo(7);
            assertThat(occupiedSlots(CharacterClass.MAGE)).isEqualTo(8);
        }

        @Test
        @DisplayName("keine Klasse belegt mehr als neun Slots - die Hotbar hat nicht mehr")
        void nobodyOverrunsTheHotbar() throws Exception {
            for (CharacterClass id : CharacterClass.values()) {
                assertThat(occupiedSlots(id)).as(id + " passt in die Hotbar").isLessThanOrEqualTo(9);
            }
        }
    }

    // --- helpers ---

    private static int occupiedSlots(CharacterClass id) throws Exception {
        AbilityConfig abilities = shippedAbilities();
        int slots = 1; // die gebundene Waffe aus B07
        for (AbilityBinding binding : bindings(id)) {
            Ability ability = abilities.require(binding.abilityId());
            // Ein Slot je Marker, nicht je Fähigkeit: Aufstieg & Fall trägt zwei.
            slots += ability.isActive() ? 1 : ability.items().size();
        }
        return slots;
    }

    private static long activeCount(CharacterClass id) throws Exception {
        return bindings(id).stream().filter(binding -> binding.kind() == AbilityKind.ACTIVE).count();
    }

    private static List<AbilityBinding> unlockedAt(CharacterClass id, int level) throws Exception {
        return bindings(id).stream().filter(binding -> binding.unlockLevel() <= level).toList();
    }

    private static List<AbilityBinding> bindings(CharacterClass id) throws Exception {
        return shippedClasses().definition(id).abilities();
    }

    private static AbilityConfig shippedAbilities() throws Exception {
        Map<String, Object> document = load("/abilities.yml");
        ConfigSchema<AbilityConfig> schema = AbilityConfigSchema.schema();
        return schema.bind(SchemaValidator.validate(Path.of("abilities.yml"), document, schema));
    }

    private static ClassConfig shippedClasses() throws Exception {
        Map<String, Object> document = load("/classes.yml");
        ConfigSchema<ClassConfig> schema = ClassConfigSchema.schema();
        return schema.bind(SchemaValidator.validate(Path.of("classes.yml"), document, schema));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> load(String resource) throws Exception {
        try (InputStream stream = ShippedAbilityConfigTest.class.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalStateException(resource + " is not on the classpath");
            }
            return new Yaml().load(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
