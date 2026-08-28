package rpg.plugin;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.yaml.snakeyaml.Yaml;

import rpg.core.ability.Ability;
import rpg.core.ability.AbilityConfig;
import rpg.core.ability.AbilityConfigSchema;
import rpg.core.classes.AbilityBinding;
import rpg.core.classes.ClassConfig;
import rpg.core.classes.ClassConfigSchema;
import rpg.core.config.ConfigSchema;
import rpg.core.config.SchemaValidator;
import rpg.core.message.MessageKey;
import rpg.core.message.Messages;
import rpg.core.session.CharacterClass;
import rpg.platform.ability.AbilityHotbar;
import rpg.platform.ability.AbilityItemTag;

/**
 * T115 - die Hotbar der drei <b>ausgelieferten</b> Loadouts (FR-055, FR-056).
 *
 * <p>Gegen die echten Dateien, denn die Zusage ist keine über den Algorithmus, sondern über das
 * Ergebnis: <b>Slot 0 bleibt der Waffe</b>, darüber die aktiven Fähigkeiten in Freischaltreihenfolge,
 * danach die Marker. Wie viele das sind, ist je Klasse verschieden (ADR-025) - vier beim Warrior und
 * beim Mage, drei beim Rogue -, deshalb steht hier eine Regel und keine Tabelle.
 *
 * <p><b>Warum in rpg-plugin und nicht in rpg-platform:</b> die ausgelieferten YAML-Dateien liegen
 * hier. Ein Test in rpg-platform faende sie nicht auf dem Klassenpfad - derselbe Grund, aus dem
 * {@code ShippedClassConfigTest} hier liegt.
 *
 * <p><b>Ein nicht freigeschalteter Slot bleibt leer</b>, nicht ausgegraut: ein Platzhalter wäre ein
 * Gegenstand, und einen Gegenstand in der Hotbar versucht ein Spieler zu benutzen.
 */
class AbilityHotbarTest {

    private ServerMock server;
    private PlayerMock player;
    private AbilityHotbar hotbar;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        MockBukkit.createMockPlugin("HotbarProbe");
        player = server.addPlayer();
        Logger logger = Logger.getLogger(AbilityHotbarTest.class.getName());
        logger.setLevel(Level.OFF);
        hotbar = new AbilityHotbar(new KeyAsText(), logger);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Nested
    @DisplayName("Die drei ausgelieferten Loadouts, voll freigeschaltet")
    class FullyUnlocked {

        @Test
        @DisplayName("Warrior: vier aktive und zwei Marker - die Waffe bleibt unberührt")
        void warrior() throws Exception {
            hotbar.layOut(player, allOf(CharacterClass.WARRIOR));

            // Vier aktive, dahinter Raserei und Lebensraub. Der Warrior trug frueher KEINEN Marker:
            // seine beiden Passiven hatten kein Item und waren fuer den Spieler damit unsichtbar.
            assertThat(occupied()).isEqualTo(6);
            assertThat(player.getInventory().getItem(AbilityHotbar.WEAPON_SLOT))
                    .as("Slot 0 gehört der Waffe aus B07 und wird hier nicht angefasst")
                    .isNull();
            assertThat(materialsFrom(1))
                    .as("die Marker stehen hinter den aktiven, nach Id sortiert")
                    .endsWith(Material.GHAST_TEAR, Material.BLAZE_POWDER);
        }

        @Test
        @DisplayName("Rogue: drei aktive und drei Marker - das Totem steht zuletzt")
        void rogue() throws Exception {
            hotbar.layOut(player, allOf(CharacterClass.ROGUE));

            assertThat(occupied()).isEqualTo(6);
            assertThat(materialsFrom(1))
                    .as("das Totem ist der letzte, nicht der erste")
                    .endsWith(Material.FLINT, Material.SPIDER_EYE, Material.TOTEM_OF_UNDYING);
        }

        @Test
        @DisplayName("Mage: vier aktive und drei Marker - sieben belegte Slots von acht")
        void mage() throws Exception {
            hotbar.layOut(player, allOf(CharacterClass.MAGE));

            // Aufstieg & Fall trägt zwei: die Wind Charge für den Sprung, den Trank für den Fall.
            // Ein Slot je Marker, nicht je Fähigkeit - sonst wäre die dreistufige Einstellung
            // (an / aus / nur Sprung) für den Spieler nicht ablesbar.
            assertThat(occupied()).isEqualTo(7);
            assertThat(materialsFrom(1))
                    .endsWith(
                            Material.GLISTERING_MELON_SLICE, Material.WIND_CHARGE, Material.POTION);
        }
    }

    @Nested
    @DisplayName("FR-056 - was nicht freigeschaltet ist, belegt nichts")
    class NotYetUnlocked {

        @Test
        @DisplayName("eine Passive belegt einen Slot - UMGEKEHRT: vorher belegte sie keinen")
        void aPassiveNowTakesASlot() throws Exception {
            // Gegen eine EIGENE Auswahl, nicht gegen die ausgelieferte Datei. Diese Tests handeln von
            // der Mechanik - was nicht freigeschaltet ist, belegt nichts -, und die haengt nicht
            // davon ab, welche Freischaltstufen gerade konfiguriert sind. Als sie noch aus
            // classes.yml lasen, sind sie an einer reinen Balancing-Aenderung zerbrochen.
            //
            // Umgekehrt statt geloescht: hier stand, dass eine Passive die Hotbar leer laesst. Das war
            // richtig, solange keine ein Item trug - und genau der Zustand war das Problem. Wer eine
            // Passive besitzt, sah davon nichts.
            hotbar.layOut(player, only("warrior.rage"));

            assertThat(occupied()).isEqualTo(1);
            assertThat(player.getInventory().getItem(1)).isNotNull();
        }

        @Test
        @DisplayName("eine freigeschaltete Aktive belegt genau einen Slot, direkt neben der Waffe")
        void oneActiveTakesExactlyOneSlot() throws Exception {
            hotbar.layOut(player, only("warrior.shield"));

            assertThat(occupied()).isEqualTo(1);
            assertThat(player.getInventory().getItem(1)).isNotNull();
            assertThat(player.getInventory().getItem(2)).isNull();
        }

        @Test
        @DisplayName("was nicht dabei ist, belegt nichts - eine von vier Fähigkeiten, ein Slot")
        void whatIsNotUnlockedOccupiesNothing() throws Exception {
            // Das ist die Zusage von FR-056, seit der Marker die alte Formulierung ueberholt hat: die
            // Belegung folgt aus der uebergebenen Liste, nicht aus dem, was die Klasse insgesamt kann.
            hotbar.layOut(player, only("warrior.leap"));

            assertThat(occupied()).isEqualTo(1);
        }

        @Test
        @DisplayName("ein zweiter Aufbau lässt keine Reste stehen")
        void layingOutTwiceLeavesNoLeftovers() throws Exception {
            hotbar.layOut(player, allOf(CharacterClass.MAGE));
            assertThat(occupied()).isEqualTo(7);

            // Neu aufbauen statt nachbessern - die Zusage ist, dass der Stand allein aus dem folgt,
            // was freigeschaltet ist. Ein Rest aus dem vorherigen Aufbau würde genau das brechen.
            hotbar.layOut(player, only("mage.magic-life", "mage.lightning"));

            // Zwei statt sieben: der Blitz und der Marker von Magisches Leben. Haette der vorherige
            // Aufbau etwas stehen lassen, stuende hier mehr.
            assertThat(occupied()).isEqualTo(2);
        }
    }

    @Test
    @DisplayName("jeder Gegenstand trägt eine Zeile darunter, die sagt was die Fähigkeit tut")
    void everyItemCarriesItsDescription() throws Exception {
        // Der Name allein beantwortet "was ist das" nicht: "Block", "Wirbel", "Aufstieg & Fall" sind
        // Namen, keine Erklärungen. Die achtzehn Beschreibungen standen längst in messages.yml und
        // wurden von nichts gelesen - der Gegenstand trug nur seinen Namen.
        hotbar.layOut(player, allOf(CharacterClass.WARRIOR));

        for (int slot = 1; slot < 9; slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (item == null) {
                continue;
            }
            assertThat(item.getItemMeta().lore())
                    .as("Slot %d", slot)
                    .isNotNull()
                    .hasSize(1);
        }
    }

    @Test
    @DisplayName("die Gegenstände tragen beide Kennzeichen - das von B07 und das von B08")
    void theItemsCarryBothTags() throws Exception {
        hotbar.layOut(player, allOf(CharacterClass.WARRIOR));

        ItemStack first = player.getInventory().getItem(1);
        assertThat(AbilityItemTag.isAbilityItem(first))
                .as("B08 muss den Slot einer Fähigkeit zuordnen können")
                .isTrue();
        assertThat(AbilityItemTag.read(first)).isPresent();
    }

    // --- helpers ---

    private int occupied() {
        int count = 0;
        for (int slot = 1; slot < 9; slot++) {
            if (player.getInventory().getItem(slot) != null) {
                count++;
            }
        }
        return count;
    }

    private Material[] materialsFrom(int firstSlot) {
        List<Material> found = new java.util.ArrayList<>();
        for (int slot = firstSlot; slot < 9; slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (item != null) {
                found.add(item.getType());
            }
        }
        return found.toArray(new Material[0]);
    }

    /** Genau diese Faehigkeiten, unabhaengig von jeder konfigurierten Freischaltstufe. */
    private static List<Ability> only(String... abilityIds) throws Exception {
        AbilityConfig abilities = shippedAbilities();
        return java.util.Arrays.stream(abilityIds).map(abilities::require).toList();
    }

    private static List<Ability> allOf(CharacterClass id) throws Exception {
        return unlockedAt(id, 60);
    }

    private static List<Ability> unlockedAt(CharacterClass id, int level) throws Exception {
        AbilityConfig abilities = shippedAbilities();
        return shippedClasses().definition(id).abilities().stream()
                .filter(binding -> binding.unlockLevel() <= level)
                .map(AbilityBinding::abilityId)
                .map(abilities::require)
                .toList();
    }

    private static AbilityConfig shippedAbilities() throws Exception {
        ConfigSchema<AbilityConfig> schema = AbilityConfigSchema.schema();
        return schema.bind(
                SchemaValidator.validate(Path.of("abilities.yml"), load("/abilities.yml"), schema));
    }

    private static ClassConfig shippedClasses() throws Exception {
        ConfigSchema<ClassConfig> schema = ClassConfigSchema.schema();
        return schema.bind(
                SchemaValidator.validate(Path.of("classes.yml"), load("/classes.yml"), schema));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> load(String resource) throws Exception {
        try (InputStream stream = AbilityHotbarTest.class.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalStateException(resource + " is not on the classpath");
            }
            return new Yaml().load(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    /** Gibt den Schlüssel zurück. Der Text ist hier gleichgültig - geprüft wird die Belegung. */
    private static final class KeyAsText implements Messages {
        @Override
        public String get(MessageKey key) {
            return key.value();
        }

        @Override
        public String get(MessageKey key, Map<String, String> placeholders) {
            return key.value();
        }

        @Override
        public boolean contains(MessageKey key) {
            return true;
        }
    }
}
