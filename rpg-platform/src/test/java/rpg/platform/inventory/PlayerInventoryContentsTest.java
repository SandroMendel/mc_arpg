package rpg.platform.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import rpg.platform.classes.BoundItemTag;

/**
 * Was aus dem Inventar gespeichert wird und was zurückkommt.
 *
 * <p>Der Grund für die Speicherung: die Auswahl erscheint bei jedem Beitritt und ist auch der Wechsel
 * zwischen Charakteren, das Minecraft-Inventar gehört aber dem Spieler. Ohne diese Haltung musste beim
 * Eintritt geleert werden, und Gefarmtes war weg.
 */
class PlayerInventoryContentsTest {

    private static final Logger QUIET = Logger.getLogger("player-inventory-contents-test");

    private ServerMock server;

    @BeforeEach
    void setUp() {
        QUIET.setLevel(Level.OFF);
        server = MockBukkit.mock();
        server.addSimpleWorld("world");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("gefarmte Beute übersteht Speichern und Zurückspielen, samt Slot")
    void lootSurvivesTheRoundTrip() {
        PlayerMock player = server.addPlayer();
        player.getInventory().setItem(5, new ItemStack(Material.ROTTEN_FLESH, 17));
        player.getInventory().setItem(9, new ItemStack(Material.BONE, 3));

        byte[] stored = PlayerInventoryContents.capture(player, QUIET);
        player.getInventory().clear();
        boolean restored = PlayerInventoryContents.restore(player, stored, QUIET);

        assertThat(restored).isTrue();
        assertThat(player.getInventory().getItem(5)).isNotNull();
        assertThat(player.getInventory().getItem(5).getType()).isEqualTo(Material.ROTTEN_FLESH);
        assertThat(player.getInventory().getItem(5).getAmount()).isEqualTo(17);
        assertThat(player.getInventory().getItem(9).getType()).isEqualTo(Material.BONE);
    }

    @Test
    @DisplayName("gebundene Klassenausrüstung wird NICHT gespeichert (sie wird neu gebaut)")
    void boundEquipmentIsNotStored() {
        // Eine gespeicherte Kopie könnte nach einem Stufenaufstieg das alte Schwert zurückgeben, und
        // beim Zurückspielen würde sie um Slots mit dem Applier streiten.
        PlayerMock player = server.addPlayer();
        player.getInventory().setItem(0, bound(Material.NETHERITE_SWORD));
        player.getInventory().setItem(4, new ItemStack(Material.ROTTEN_FLESH));

        byte[] stored = PlayerInventoryContents.capture(player, QUIET);
        player.getInventory().clear();
        PlayerInventoryContents.restore(player, stored, QUIET);

        assertThat(player.getInventory().getItem(0)).as("die Waffe kommt aus der Leiter").isNull();
        assertThat(player.getInventory().getItem(4)).isNotNull();
    }

    @Test
    @DisplayName("die Enderchest wird eigenständig gespeichert und zurückgespielt")
    void theEnderChestIsCarriedToo() {
        PlayerMock player = server.addPlayer();
        player.getEnderChest().setItem(2, new ItemStack(Material.DIAMOND, 5));
        player.getInventory().setItem(0, new ItemStack(Material.STONE));

        byte[] backpack = PlayerInventoryContents.capture(player, QUIET);
        byte[] chest = PlayerInventoryContents.captureEnderChest(player, QUIET);
        player.getInventory().clear();
        player.getEnderChest().clear();

        PlayerInventoryContents.restore(player, backpack, QUIET);
        PlayerInventoryContents.restoreEnderChest(player, chest, QUIET);

        assertThat(player.getEnderChest().getItem(2)).isNotNull();
        assertThat(player.getEnderChest().getItem(2).getType()).isEqualTo(Material.DIAMOND);
        assertThat(player.getEnderChest().getItem(2).getAmount()).isEqualTo(5);
        assertThat(player.getInventory().getItem(0).getType())
                .as("die beiden Behälter kommen sich nicht ins Gehege")
                .isEqualTo(Material.STONE);
    }

    @Test
    @DisplayName("eine leere Enderchest ergibt ein leeres Feld")
    void anEmptyEnderChestIsCheap() {
        PlayerMock player = server.addPlayer();
        player.getInventory().setItem(0, new ItemStack(Material.STONE));

        assertThat(PlayerInventoryContents.captureEnderChest(player, QUIET)).isEmpty();
    }

    @Test
    @DisplayName("ein leeres Inventar ergibt ein leeres Feld, nicht 41 leere Slots")
    void anEmptyInventoryIsCheap() {
        PlayerMock player = server.addPlayer();

        assertThat(PlayerInventoryContents.capture(player, QUIET)).isEmpty();
    }

    @Test
    @DisplayName("ein Inventar, das nur Klassenausrüstung enthält, gilt als leer")
    void onlyBoundEquipmentCountsAsEmpty() {
        PlayerMock player = server.addPlayer();
        player.getInventory().setItem(0, bound(Material.NETHERITE_SWORD));

        assertThat(PlayerInventoryContents.capture(player, QUIET)).isEmpty();
    }

    @Test
    @DisplayName("nichts zurückzuspielen ist kein Fehler und rührt das Inventar nicht an")
    void restoringNothingLeavesTheInventoryAlone() {
        PlayerMock player = server.addPlayer();
        player.getInventory().setItem(3, new ItemStack(Material.STONE));

        boolean restored = PlayerInventoryContents.restore(player, new byte[0], QUIET);

        assertThat(restored).isFalse();
        assertThat(player.getInventory().getItem(3)).isNotNull();
    }

    @Test
    @DisplayName("ein unlesbarer Datensatz kostet die Items, nicht den Beitritt (Prinzip VI)")
    void anUnreadableBlobDoesNotThrow() {
        PlayerMock player = server.addPlayer();

        boolean restored =
                PlayerInventoryContents.restore(player, new byte[] {42, 13, 7, 99}, QUIET);

        assertThat(restored).isFalse();
    }

    private static ItemStack bound(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        BoundItemTagAccess.write(meta);
        item.setItemMeta(meta);
        assertThat(BoundItemTag.isTagged(item)).isTrue();
        return item;
    }

    /** Schreibt die Marke so, wie {@code BoundItemFactory} es tut - ohne dessen Materialprüfung. */
    private static final class BoundItemTagAccess {
        static void write(ItemMeta meta) {
            meta.getPersistentDataContainer()
                    .set(
                            org.bukkit.NamespacedKey.fromString("rpg:class_bound"),
                            org.bukkit.persistence.PersistentDataType.STRING,
                            "WARRIOR|WEAPON|" + java.util.UUID.randomUUID());
        }
    }
}
