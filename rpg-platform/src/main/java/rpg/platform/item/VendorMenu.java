package rpg.platform.item;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import rpg.core.classes.LadderSlot;
import rpg.core.item.ItemMessageKeys;
import rpg.core.item.VendorStock;
import rpg.core.message.MessageKey;
import rpg.core.message.Messages;

/**
 * Das Fenster des Händlers — aus Vanilla-Materialien, nach dem Muster von
 * {@code ClassSelectionMenu} und {@code CurrencyMenu} (ADR-005).
 *
 * <p><b>Diese Klasse kennt keine Regeln.</b> Was ein Klick bewirkt, entscheidet
 * {@code VendorTransaction} in {@code rpg-core}; hier wird nur gezeichnet und gesagt, welcher Slot
 * welche Vorlage meint. Dieselbe Aufteilung wie bei {@code CurrencyMenu}, und aus demselben Grund:
 * eine Regel, die im Fenster steht, gilt nur, solange man durch das Fenster geht.
 *
 * <p><b>Vorläufig, wie alles Sichtbare vor B13</b> (ADR-028). Wenn die Anzeige einen eigenen Block
 * bekommt, zieht das hier um; bis dahin sind es Vanilla-Materialien und Message-Schlüssel.
 */
public final class VendorMenu {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    /** Drei Reihen: der Bestand oben, die Dienste unten. */
    private static final int SIZE = 27;

    /** Wo die Ware liegt — die erste und zweite Reihe. */
    private static final int STOCK_SLOTS = 18;

    /**
     * Die Dienste in der letzten Reihe — Verkauf, und je Leiter Reparatur und Aufstieg.
     *
     * <p>B07 kennt genau zwei Leitern, und sie verschleißen getrennt (FR-040, FR-041). Ein
     * gemeinsamer Knopf müsste raten, welche gemeint ist; zwei Knöpfe müssen nichts raten.
     */
    public static final int SLOT_SELL = 19;

    private static final int SLOT_REPAIR_FIRST = 21;

    private static final int SLOT_UPGRADE_FIRST = 24;

    private final ItemStackFactory factory;
    private final Messages messages;

    public VendorMenu(ItemStackFactory factory, Messages messages) {
        this.factory = Objects.requireNonNull(factory, "factory");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    /**
     * Baut das Fenster für den Händler einer Region.
     *
     * <p>Der Bestand steht in Konfigurationsreihenfolge — deshalb hält {@code VendorStock} eine
     * {@code LinkedHashMap} und keine {@code Map.copyOf}. Ein Fenster, das nach jedem Neustart
     * anders aussieht, ist ein Fenster, in dem niemand etwas wiederfindet.
     */
    public Inventory build(VendorStock stock, long balance) {
        Objects.requireNonNull(stock, "stock");

        Inventory inventory =
                Bukkit.createInventory(null, SIZE, text(ItemMessageKeys.VENDOR_TITLE, "Vendor"));

        int slot = 0;
        for (String templateKey : stock.templateKeys()) {
            if (slot >= STOCK_SLOTS) {
                break;
            }
            Optional<ItemStack> shown = factory.create(templateKey, 1);
            if (shown.isEmpty()) {
                continue;
            }
            ItemStack entry = shown.get();
            stock.priceOf(templateKey)
                    .ifPresent(price -> appendPrice(entry, price, balance));
            inventory.setItem(slot++, entry);
        }

        inventory.setItem(SLOT_SELL, service(Material.HOPPER, ItemMessageKeys.VENDOR_SELL));
        for (LadderSlot ladder : LadderSlot.values()) {
            inventory.setItem(
                    repairSlot(ladder), service(Material.ANVIL, ItemMessageKeys.vendorRepair(ladder)));
            inventory.setItem(
                    upgradeSlot(ladder),
                    service(Material.SMITHING_TABLE, ItemMessageKeys.vendorUpgrade(ladder)));
        }
        return inventory;
    }

    /** Wo die Reparatur dieser Leiter liegt. */
    public static int repairSlot(LadderSlot slot) {
        return SLOT_REPAIR_FIRST + slot.ordinal();
    }

    /** Wo der Aufstieg dieser Leiter liegt. */
    public static int upgradeSlot(LadderSlot slot) {
        return SLOT_UPGRADE_FIRST + slot.ordinal();
    }

    /**
     * Welche Leiter ein Klick auf einen Reparaturplatz meint. Leer für alles andere.
     *
     * <p>Damit steht die Zuordnung Platz → Leiter an <b>einer</b> Stelle. Ein zweiter Ort, der
     * dieselbe Rechnung anstellt, wäre der Ort, an dem beim nächsten Verschieben einer Reihe die
     * Rüstung repariert wird, weil auf die Waffe geklickt wurde.
     */
    public static Optional<LadderSlot> repairAt(int slot) {
        return ladderAt(slot, SLOT_REPAIR_FIRST);
    }

    /** Welche Leiter ein Klick auf einen Aufstiegsplatz meint. Leer für alles andere. */
    public static Optional<LadderSlot> upgradeAt(int slot) {
        return ladderAt(slot, SLOT_UPGRADE_FIRST);
    }

    private static Optional<LadderSlot> ladderAt(int slot, int first) {
        int index = slot - first;
        LadderSlot[] slots = LadderSlot.values();
        return index >= 0 && index < slots.length ? Optional.of(slots[index]) : Optional.empty();
    }

    /**
     * Welche Vorlage ein Klick auf {@code slot} meint.
     *
     * @return leer für die Dienste und für leere Slots — der Aufrufer unterscheidet das selbst
     */
    public Optional<String> templateAt(VendorStock stock, int slot) {
        if (slot < 0 || slot >= STOCK_SLOTS) {
            return Optional.empty();
        }
        List<String> keys = new ArrayList<>(stock.templateKeys());
        return slot < keys.size() ? Optional.of(keys.get(slot)) : Optional.empty();
    }

    /**
     * Hängt den Preis an die Lore — und sagt, ob er reicht.
     *
     * <p><b>Angezeigt, nicht zugesagt.</b> Der Kontostand kann sich ändern, während das Fenster
     * offen steht; geprüft wird beim Abschluss (FR-064). Was hier steht, ist eine Auskunft.
     */
    private void appendPrice(ItemStack entry, long price, long balance) {
        ItemMeta meta = entry.getItemMeta();
        if (meta == null) {
            return;
        }
        List<Component> lore = new ArrayList<>();
        if (meta.lore() != null) {
            lore.addAll(meta.lore());
        }
        MessageKey key =
                balance >= price ? ItemMessageKeys.VENDOR_PRICE : ItemMessageKeys.VENDOR_PRICE_SHORT;
        if (messages.contains(key)) {
            lore.add(LEGACY.deserialize(messages.get(key, Map.of("price", String.valueOf(price)))));
        }
        meta.lore(lore);
        entry.setItemMeta(meta);
    }

    private ItemStack service(Material material, MessageKey nameKey) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(text(nameKey, material.name()));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private Component text(MessageKey key, String fallback) {
        return messages.contains(key)
                ? LEGACY.deserialize(messages.get(key, Map.of()))
                : Component.text(fallback);
    }
}
