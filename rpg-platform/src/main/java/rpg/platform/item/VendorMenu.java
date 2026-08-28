package rpg.platform.item;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import net.kyori.adventure.text.Component;

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
 * {@code VendorTransaction}, {@code GearRepair} und {@code EquipmentPurchase} in {@code rpg-core};
 * hier wird nur gezeichnet. Die Zahlen für die Beschreibungen kommen als {@link Offers} herein —
 * fertig gerechnet, damit das Fenster nicht anfängt, Preise zu bestimmen.
 *
 * <p><b>Jeder Knopf sagt, was er tut und was er kostet.</b> Ein Knopf, der nur seinen Namen trägt,
 * verlangt vom Spieler, ihn auszuprobieren — und „ausprobieren" heißt bei einem Kauf: bezahlen. Das
 * ist bei einem unumkehrbaren Vorgang keine zumutbare Art, etwas herauszufinden.
 *
 * <p><b>Vorläufig, wie alles Sichtbare vor B13</b> (ADR-028).
 */
public final class VendorMenu {

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

    /**
     * Was eine Reparatur dieser Leiter gerade kostet.
     *
     * @param condition der Zustand in {@code [0, 100]}
     * @param price der Preis; {@code 0} heißt „nichts abgenutzt" und wird als solches angezeigt
     *     (FR-054) — nicht als Gratisreparatur
     */
    public record RepairOffer(double condition, long price) {}

    /**
     * Was der nächste Stufenaufstieg dieser Leiter kostet.
     *
     * @param price leer auf der Höchststufe — dann gibt es nichts zu kaufen
     * @param requiredLevel das Level, ab dem er möglich ist
     * @param nextTier die Stufe, die man bekäme
     */
    public record UpgradeOffer(OptionalLong price, OptionalInt requiredLevel, OptionalInt nextTier) {

        /** Nichts mehr zu holen — der Spieler ist oben. */
        public static UpgradeOffer atTop() {
            return new UpgradeOffer(OptionalLong.empty(), OptionalInt.empty(), OptionalInt.empty());
        }

        public boolean isAtTop() {
            return price.isEmpty();
        }
    }

    /** Die Zahlen, die die Beschreibungen brauchen — fertig gerechnet vom Aufrufer. */
    public record Offers(Map<LadderSlot, RepairOffer> repairs, Map<LadderSlot, UpgradeOffer> upgrades) {

        /** Ohne Angaben: die Knöpfe stehen da, sagen aber nur, was sie tun. */
        public static Offers unknown() {
            return new Offers(Map.of(), Map.of());
        }
    }

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
    public Inventory build(VendorStock stock, long balance, Offers offers) {
        Objects.requireNonNull(stock, "stock");
        Objects.requireNonNull(offers, "offers");

        Inventory inventory =
                Bukkit.createInventory(
                        null,
                        SIZE,
                        ItemText.orElse(messages, ItemMessageKeys.VENDOR_TITLE, "Vendor"));

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
            stock.priceOf(templateKey).ifPresent(price -> appendPrice(entry, price, balance));
            inventory.setItem(slot++, entry);
        }

        inventory.setItem(
                SLOT_SELL,
                service(
                        Material.HOPPER,
                        ItemMessageKeys.VENDOR_SELL,
                        ItemText.of(messages, ItemMessageKeys.VENDOR_SELL_LORE, Map.of())));

        for (LadderSlot ladder : LadderSlot.values()) {
            inventory.setItem(
                    repairSlot(ladder),
                    service(
                            Material.ANVIL,
                            ItemMessageKeys.vendorRepair(ladder),
                            repairLore(ladder, offers.repairs().get(ladder))));
            inventory.setItem(
                    upgradeSlot(ladder),
                    service(
                            Material.SMITHING_TABLE,
                            ItemMessageKeys.vendorUpgrade(ladder),
                            upgradeLore(ladder, offers.upgrades().get(ladder))));
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

    // --- Beschreibungen ----------------------------------------------------------------

    private Component repairLore(LadderSlot ladder, RepairOffer offer) {
        if (offer == null) {
            return null;
        }
        String condition = String.valueOf((int) Math.floor(offer.condition()));
        if (offer.price() <= 0L) {
            // Null heisst "nichts abgenutzt" und nicht "umsonst" - das ist fuer den Spieler ein
            // Unterschied, und FR-054 macht ihn auch im Ergebnis (die Reparatur wird abgelehnt).
            return ItemText.of(
                    messages,
                    ItemMessageKeys.vendorRepairLoreIntact(ladder),
                    Map.of("condition", condition));
        }
        return ItemText.of(
                messages,
                ItemMessageKeys.vendorRepairLore(ladder),
                Map.of("condition", condition, "price", String.valueOf(offer.price())));
    }

    private Component upgradeLore(LadderSlot ladder, UpgradeOffer offer) {
        if (offer == null) {
            return null;
        }
        if (offer.isAtTop()) {
            return ItemText.of(messages, ItemMessageKeys.vendorUpgradeLoreTop(ladder), Map.of());
        }
        return ItemText.of(
                messages,
                ItemMessageKeys.vendorUpgradeLore(ladder),
                Map.of(
                        "price",
                        String.valueOf(offer.price().orElse(0L)),
                        "level",
                        String.valueOf(offer.requiredLevel().orElse(1)),
                        "tier",
                        String.valueOf(offer.nextTier().orElse(1))));
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
        Component line = ItemText.of(messages, key, Map.of("price", String.valueOf(price)));
        if (line != null) {
            lore.add(ItemText.onItem(line));
        }
        meta.lore(lore);
        entry.setItemMeta(meta);
    }

    private ItemStack service(Material material, MessageKey nameKey, Component lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(ItemText.onItem(ItemText.orElse(messages, nameKey, material.name())));
            if (lore != null) {
                meta.lore(List.of(ItemText.onItem(lore)));
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }
}
