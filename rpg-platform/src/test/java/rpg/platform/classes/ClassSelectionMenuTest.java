package rpg.platform.classes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import rpg.core.classes.ClassSlot;
import rpg.core.session.CharacterClass;
import rpg.core.session.PlayerCharacter;

/**
 * T051 - die Auswahl zeigt Vanilla-Materialien, Anzeigenamen aus der Konfiguration und den Stand jedes
 * Charakters (US1.1, US1.4, FR-040).
 */
class ClassSelectionMenuTest {

    private ServerMock server;
    private ClassSelectionMenu menu;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        menu = new ClassSelectionMenu(PlatformClassFixture.registry(), PlatformClassFixture.messages());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("alle drei Klassen liegen in der Reihe - auch die bereits bespielten (US1.4)")
    void everyClassIsShown() {
        Inventory inventory = menu.build(PlatformClassFixture.freeSlots());

        assertThat(inventory.getSize()).isEqualTo(ClassSelectionMenu.SIZE);
        int offers = 0;
        for (ItemStack item : inventory.getContents()) {
            if (item != null) {
                offers++;
            }
        }
        assertThat(offers).isEqualTo(3);
    }

    @Test
    @DisplayName("jedes Angebot trägt das Material aus der Konfiguration (ADR-005)")
    void everyOfferCarriesItsConfiguredMaterial() {
        Inventory inventory = menu.build(PlatformClassFixture.freeSlots());

        assertThat(inventory.getItem(2)).isNotNull();
        assertThat(inventory.getItem(2).getType()).isEqualTo(Material.NETHERITE_SWORD);
        assertThat(inventory.getItem(4).getType()).isEqualTo(Material.NETHERITE_SPEAR);
        assertThat(inventory.getItem(6).getType()).isEqualTo(Material.GOLDEN_SWORD);
    }

    @Test
    @DisplayName("der Anzeigename kommt aus der Nachrichtendatei - Warrior heißt Berserker (ADR-019)")
    void displayNameComesFromMessages() {
        Inventory inventory = menu.build(PlatformClassFixture.freeSlots());

        assertThat(nameOf(inventory.getItem(2))).isEqualTo("Berserker");
    }

    @Test
    @DisplayName("ein bespielter Slot nennt Level und beide Stufen (US1.4)")
    void aPlayedSlotShowsWhatItReached() {
        Inventory inventory = menu.build(playedWarrior(42, 3, 5));

        List<String> lore = loreOf(inventory.getItem(2));

        assertThat(lore).anySatisfy(line -> assertThat(line).contains("42"));
        assertThat(lore)
                .as("beide Leitern, jeweils mit ihrer Länge aus der Konfiguration")
                .anySatisfy(
                        line ->
                                assertThat(line)
                                        .contains("3")
                                        .contains("5")
                                        .contains("2")); // die Fixture-Leitern haben zwei Stufen
    }

    @Test
    @DisplayName("ein freier Slot sagt, dass er frei ist - und nennt keine Zahlen")
    void anEmptySlotSaysSo() {
        Inventory inventory = menu.build(PlatformClassFixture.freeSlots());

        List<String> lore = loreOf(inventory.getItem(2));

        assertThat(lore).isNotEmpty();
        assertThat(lore)
                .as("kein Level und keine Stufe, denn es gibt noch keinen Charakter")
                .noneSatisfy(line -> assertThat(line).containsPattern("\\d"));
    }

    @Test
    @DisplayName("die Reihenfolge ist stabil - dieselbe Klasse liegt immer im selben Slot")
    void slotOrderIsStable() {
        List<ClassSlot> slots = PlatformClassFixture.freeSlots();

        assertThat(menu.classAt(slots, 2)).hasValue(CharacterClass.WARRIOR);
        assertThat(menu.classAt(slots, 4)).hasValue(CharacterClass.MAGE);
        assertThat(menu.classAt(slots, 6)).hasValue(CharacterClass.ROGUE);
    }

    @Test
    @DisplayName("ein bespielter Charakter verschiebt nichts - der Platz bleibt derselbe")
    void aPlayedCharacterDoesNotMoveTheOthers() {
        List<ClassSlot> slots = playedWarrior(10, 1, 1);

        assertThat(menu.classAt(slots, 2)).hasValue(CharacterClass.WARRIOR);
        assertThat(menu.classAt(slots, 4)).hasValue(CharacterClass.MAGE);
        assertThat(menu.classAt(slots, 6)).hasValue(CharacterClass.ROGUE);
    }

    @Test
    @DisplayName("ein Klick neben ein Angebot bedeutet keine Klasse")
    void clickBesideAnOfferMeansNothing() {
        List<ClassSlot> slots = PlatformClassFixture.freeSlots();

        assertThat(menu.classAt(slots, 0)).isEmpty();
        assertThat(menu.classAt(slots, 3)).isEmpty();
        assertThat(menu.classAt(slots, 8)).isEmpty();
    }

    @Test
    @DisplayName("eine leere Liste ergibt ein leeres Menü, keinen Absturz")
    void noSlotsIsAnEmptyMenu() {
        Inventory inventory = menu.build(List.of());

        for (ItemStack item : inventory.getContents()) {
            assertThat(item).isNull();
        }
    }

    @Test
    @DisplayName("ein unbekanntes Menü-Material bricht ab - das ist V12 (Prinzip III.1)")
    void unknownMenuMaterialFails() {
        ClassSelectionMenu broken =
                new ClassSelectionMenu(
                        PlatformClassFixture.registryWithMenuMaterial("DEFINITELY_NOT_A_MATERIAL"),
                        PlatformClassFixture.messages());

        assertThatThrownBy(() -> broken.build(List.of(ClassSlot.empty(CharacterClass.WARRIOR))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not exist in this server version");
    }

    // --- fixtures ---

    private static List<ClassSlot> playedWarrior(int level, int armorTier, int weaponTier) {
        PlayerCharacter character =
                PlayerCharacter.create(UUID.randomUUID(), CharacterClass.WARRIOR, Instant.now());
        return PlatformClassFixture.slotsWithPlayed(
                CharacterClass.WARRIOR, character, level, armorTier, weaponTier);
    }

    private static String nameOf(ItemStack item) {
        assertThat(item).isNotNull();
        assertThat(item.getItemMeta()).isNotNull();
        return PlainTextComponentSerializer.plainText().serialize(item.getItemMeta().displayName());
    }

    private static List<String> loreOf(ItemStack item) {
        assertThat(item).isNotNull();
        assertThat(item.getItemMeta()).isNotNull();
        List<Component> lore = item.getItemMeta().lore();
        assertThat(lore).as("jeder Slot beschreibt sich selbst").isNotNull();
        return lore.stream().map(PlainTextComponentSerializer.plainText()::serialize).toList();
    }
}
