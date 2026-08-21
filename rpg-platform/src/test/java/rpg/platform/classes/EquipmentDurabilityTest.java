package rpg.platform.classes;

import static org.assertj.core.api.Assertions.assertThat;

import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import rpg.core.classes.TierAppearance;

/**
 * Klassenausrüstung nutzt sich nicht ab.
 *
 * <p>Der Fehler dahinter war auf dem Server sichtbar: das Schwert verlor beim Schlagen Haltbarkeit und
 * ging schließlich kaputt. Danach stand der Warrior ohne Waffe da, denn die Leiter ist die einzige
 * Quelle für Waffen - anlegen, aufheben oder herstellen ist alles gesperrt (ADR-018). Nur ein Relogin
 * half, weil der Applier die Ausrüstung bei jeder Sitzung neu baut.
 *
 * <p>Der zweite Grund wiegt schwerer als der erste: die Werte hängen an der Stufe, nicht am Item. Ein
 * beschädigtes Item würde einen Charakter still schwächen, ohne dass ein Attribut das abbildet.
 */
class EquipmentDurabilityTest {

    private BoundItemFactory factory;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        factory = new BoundItemFactory(PlatformClassFixture.messages());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("die Waffe ist unzerstörbar - sonst steht die Klasse irgendwann ohne da")
    void theWeaponDoesNotWearOut() {
        ItemStack weapon = factory.weapon(TierAppearance.ofMaterial("NETHERITE_SWORD"), tag());

        assertThat(weapon.getItemMeta()).isNotNull();
        assertThat(weapon.getItemMeta().isUnbreakable()).isTrue();
    }

    @Test
    @DisplayName("jedes Rüstungsteil ebenso - Schaden trifft die Rüstung genauso")
    void everyArmorPieceDoesNotWearOut() {
        for (BoundItemFactory.ArmorPiece piece : BoundItemFactory.ArmorPiece.values()) {
            ItemStack item =
                    factory.armorPiece(TierAppearance.ofMaterial("NETHERITE"), piece, tag());

            assertThat(item.getItemMeta()).as(piece.name()).isNotNull();
            assertThat(item.getItemMeta().isUnbreakable()).as(piece.name()).isTrue();
        }
    }

    @Test
    @DisplayName("die Unzerstörbarkeit steht nicht im Tooltip - sie ist keine Eigenschaft zum Angeben")
    void theFlagIsHidden() {
        ItemStack weapon = factory.weapon(TierAppearance.ofMaterial("NETHERITE_SWORD"), tag());

        assertThat(weapon.getItemMeta().getItemFlags())
                .contains(org.bukkit.inventory.ItemFlag.HIDE_UNBREAKABLE);
    }

    private static String tag() {
        return "WARRIOR|WEAPON|" + java.util.UUID.randomUUID();
    }
}
