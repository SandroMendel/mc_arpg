package rpg.platform.classes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.EnumSet;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import rpg.core.session.CharacterClass;

/** T051 - die Auswahl zeigt Vanilla-Materialien und Anzeigenamen aus der Konfiguration (US1.1, FR-040). */
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
    @DisplayName("mit allen drei Klassen offen liegen drei Angebote in der Reihe")
    void threeOffersForAFreshAccount() {
        Inventory inventory = menu.build(EnumSet.allOf(CharacterClass.class));

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
        Inventory inventory = menu.build(EnumSet.allOf(CharacterClass.class));

        assertThat(inventory.getItem(2)).isNotNull();
        assertThat(inventory.getItem(2).getType()).isEqualTo(Material.NETHERITE_SWORD);
        assertThat(inventory.getItem(4).getType()).isEqualTo(Material.NETHERITE_SPEAR);
        assertThat(inventory.getItem(6).getType()).isEqualTo(Material.GOLDEN_SWORD);
    }

    @Test
    @DisplayName("der Anzeigename kommt aus der Nachrichtendatei - Warrior heißt Berserker (ADR-019)")
    void displayNameComesFromMessages() {
        Inventory inventory = menu.build(EnumSet.allOf(CharacterClass.class));

        ItemStack warrior = inventory.getItem(2);
        assertThat(warrior).isNotNull();
        assertThat(warrior.getItemMeta()).isNotNull();
        assertThat(
                        net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                                .plainText()
                                .serialize(warrior.getItemMeta().displayName()))
                .isEqualTo("Berserker");
    }

    @Test
    @DisplayName("belegte Klassen erscheinen nicht - der Rest rückt auf (FR-035)")
    void takenClassesAreNotOffered() {
        Set<CharacterClass> open = EnumSet.of(CharacterClass.MAGE, CharacterClass.ROGUE);

        Inventory inventory = menu.build(open);

        assertThat(inventory.getItem(2)).isNotNull();
        assertThat(inventory.getItem(2).getType()).isEqualTo(Material.NETHERITE_SPEAR);
        assertThat(inventory.getItem(4)).isNotNull();
        assertThat(inventory.getItem(4).getType()).isEqualTo(Material.GOLDEN_SWORD);
        assertThat(inventory.getItem(6)).as("nur zwei Angebote").isNull();
    }

    @Test
    @DisplayName("die Reihenfolge ist stabil - dieselbe Klasse liegt immer im selben Slot")
    void slotOrderIsStable() {
        Set<CharacterClass> all = EnumSet.allOf(CharacterClass.class);

        assertThat(menu.classAt(all, 2)).hasValue(CharacterClass.WARRIOR);
        assertThat(menu.classAt(all, 4)).hasValue(CharacterClass.MAGE);
        assertThat(menu.classAt(all, 6)).hasValue(CharacterClass.ROGUE);
    }

    @Test
    @DisplayName("ein Klick neben ein Angebot bedeutet keine Klasse")
    void clickBesideAnOfferMeansNothing() {
        Set<CharacterClass> all = EnumSet.allOf(CharacterClass.class);

        assertThat(menu.classAt(all, 0)).isEmpty();
        assertThat(menu.classAt(all, 3)).isEmpty();
        assertThat(menu.classAt(all, 8)).isEmpty();
    }

    @Test
    @DisplayName("eine leere Auswahl ergibt ein leeres Menü, keinen Absturz")
    void noOffersIsAnEmptyMenu() {
        Inventory inventory = menu.build(EnumSet.noneOf(CharacterClass.class));

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

        assertThatThrownBy(() -> broken.build(EnumSet.of(CharacterClass.WARRIOR)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not exist in this server version");
    }
}
