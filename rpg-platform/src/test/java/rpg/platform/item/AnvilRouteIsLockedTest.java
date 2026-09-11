package rpg.platform.item;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import rpg.core.classes.BoundEquipment;
import rpg.core.classes.LadderSlot;
import rpg.core.message.MapMessages;
import rpg.core.session.CharacterClass;
import rpg.platform.classes.BoundItemFactory;

/**
 * FR-056, R10 — <b>alle drei Vanilla-Wege einzeln nachgewiesen.</b>
 *
 * <p>Der bezahlte Weg beim Händler soll die <em>einzige</em> Instandsetzung sein (FR-052). Bliebe
 * einer dieser drei Blöcke offen, wäre er der billigere — und niemand ginge je zum Händler. Die
 * Coin-Senke aus ADR-017 hätte dann kein Wasser, und das fiele erst beim Balancing auf, wenn die
 * Coins sich stapeln und niemand weiß, warum.
 *
 * <p><b>Einzeln geprüft und nicht als Menge.</b> Ein Test, der „mindestens einer ist gesperrt" zeigt,
 * bliebe grün, während zwei davon offen stehen.
 *
 * <p><b>Und die Gegenprobe gehört dazu:</b> ein gewöhnlicher Vanilla-Gegenstand darf durch jeden
 * dieser Blöcke. Das Spiel bleibt Minecraft.
 */
class AnvilRouteIsLockedTest {

    private ServerMock server;
    private PlayerMock player;
    private RepairRouteLockListener listener;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        player = server.addPlayer();
        listener = new RepairRouteLockListener(messages());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @ParameterizedTest(name = "{0} ist fuer gebundene Ausruestung gesperrt")
    @EnumSource(value = InventoryType.class, names = {"ANVIL", "ENCHANTING", "GRINDSTONE"})
    void everyVanillaRouteIsLockedForBoundGear(InventoryType type) {
        InventoryClickEvent event = clickIn(type, boundChestplate());

        listener.onClick(event);

        assertThat(event.isCancelled())
                .as(
                        "%s waere sonst der billigere Weg zur Instandsetzung - und niemand ginge je"
                                + " zum Haendler (FR-052, FR-056)",
                        type)
                .isTrue();
    }

    @ParameterizedTest(name = "{0} bleibt fuer gewoehnliche Gegenstaende offen")
    @EnumSource(value = InventoryType.class, names = {"ANVIL", "ENCHANTING", "GRINDSTONE"})
    void everyVanillaRouteStaysOpenForOrdinaryItems(InventoryType type) {
        InventoryClickEvent event = clickIn(type, new ItemStack(Material.IRON_CHESTPLATE));

        listener.onClick(event);

        assertThat(event.isCancelled())
                .as("das Spiel bleibt Minecraft - gesperrt ist nur, was am Charakter haengt")
                .isFalse();
    }

    @Test
    @DisplayName("ein gewoehnliches Kistenfenster ist ueberhaupt nicht betroffen")
    void anordinaryChestIsNotAffectedAtAll() {
        InventoryClickEvent event = clickIn(InventoryType.CHEST, boundChestplate());

        listener.onClick(event);

        assertThat(event.isCancelled())
                .as("gebundene Ausruestung darf sich bewegen - sie darf nur nicht umgebaut werden")
                .isFalse();
    }

    @Test
    @DisplayName("auch der Gegenstand auf dem Zeiger wird geprueft, nicht nur der im Slot")
    void theitemOnTheCursorIsCheckedToo() {
        // Der Weg, den ein Spieler tatsaechlich nimmt: Stueck aufnehmen, in den Amboss klicken.
        // Waere nur der Slot geprueft, waere die Sperre offen fuer genau diese Bewegung.
        InventoryClickEvent event = clickIn(InventoryType.ANVIL, null);
        event.getWhoClicked().setItemOnCursor(boundChestplate());

        listener.onClick(event);

        assertThat(event.isCancelled()).isTrue();
    }

    // --- Hilfsmittel ---------------------------------------------------------------

    private InventoryClickEvent clickIn(InventoryType type, ItemStack inSlot) {
        player.openInventory(server.createInventory(null, type));
        if (inSlot != null) {
            player.getOpenInventory().getTopInventory().setItem(0, inSlot);
        }
        return new InventoryClickEvent(
                player.getOpenInventory(),
                InventoryType.SlotType.CONTAINER,
                0,
                ClickType.LEFT,
                InventoryAction.PICKUP_ALL);
    }

    private static ItemStack boundChestplate() {
        ItemStack stack = new ItemStack(Material.IRON_CHESTPLATE);
        ItemMeta meta = stack.getItemMeta();
        BoundItemFactory.markBound(
                meta,
                BoundEquipment.tagFor(UUID.randomUUID(), CharacterClass.WARRIOR, LadderSlot.ARMOR));
        stack.setItemMeta(meta);
        return stack;
    }

    private static MapMessages messages() {
        Map<String, String> texts = new LinkedHashMap<>();
        texts.put("item.repair.route-locked", "locked");
        return new MapMessages(texts);
    }
}
