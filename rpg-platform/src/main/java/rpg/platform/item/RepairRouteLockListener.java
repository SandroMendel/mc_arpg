package rpg.platform.item;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import rpg.core.item.ItemMessageKeys;
import rpg.core.message.Messages;
import rpg.platform.classes.BoundItemTag;

/**
 * Amboss, Zauberpult und Schleifstein sind für gebundene Ausrüstung gesperrt (FR-056, research.md
 * R10).
 *
 * <p><b>Ohne diese Sperre hätte die Coin-Senke kein Wasser.</b> Der bezahlte Weg beim NPC ist die
 * einzige Instandsetzung, die es geben soll (FR-052) — ein offener Amboss wäre der billigere, und
 * niemand ginge je zum Händler. Dasselbe gilt für das Zauberpult: eine Verzauberung auf
 * Klassenausrüstung wäre eine Wertquelle außerhalb der Leitern, und die ganze Rechnung aus ADR-017
 * ginge nicht mehr auf.
 *
 * <p><b>Gesperrt wird das Hineinlegen, nicht das Ergebnis.</b> Wer den Gegenstand gar nicht erst
 * hineinbekommt, kann auch nichts damit anstellen — und der Spieler erfährt es in dem Moment, in dem
 * er es versucht, statt nachdem er Erfahrung oder Material ausgegeben hat.
 *
 * <p><b>Nur gebundene Ausrüstung.</b> Ein gewöhnlicher Vanilla-Gegenstand darf durch jeden dieser
 * Blöcke; das Spiel bleibt Minecraft. Und was gebunden ist, sagt B07 — hier wird nur der Vermerk
 * gelesen (FR-079).
 */
public final class RepairRouteLockListener implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    /**
     * Die drei Wege aus FR-056.
     *
     * <p>Der Schleifstein steht mit dabei, obwohl er nicht repariert, sondern entzaubert: er würde
     * eine Verzauberung entfernen, die B07 auf ein gebundenes Stück gelegt hat, und der Spieler
     * bekäme sie nicht zurück.
     */
    private static final Set<InventoryType> LOCKED =
            Set.of(InventoryType.ANVIL, InventoryType.ENCHANTING, InventoryType.GRINDSTONE);

    private final Messages messages;

    public RepairRouteLockListener(Messages messages) {
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!LOCKED.contains(event.getView().getTopInventory().getType())) {
            return;
        }
        if (!isBound(event.getCurrentItem()) && !isBound(event.getCursor())) {
            // Nichts Gebundenes im Spiel - das Fenster bleibt ein gewoehnliches Minecraft-Fenster.
            return;
        }
        event.setCancelled(true);
        tell(event.getWhoClicked());
    }

    private static boolean isBound(ItemStack stack) {
        return stack != null && BoundItemTag.isTagged(stack);
    }

    private void tell(HumanEntity who) {
        if (!(who instanceof Player player) || !messages.contains(ItemMessageKeys.REPAIR_ROUTE_LOCKED)) {
            return;
        }
        player.sendMessage(
                LEGACY.deserialize(messages.get(ItemMessageKeys.REPAIR_ROUTE_LOCKED, Map.of())));
    }
}
