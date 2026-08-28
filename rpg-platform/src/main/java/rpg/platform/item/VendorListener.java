package rpg.platform.item;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Logger;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import rpg.core.classes.LadderSlot;
import rpg.core.currency.Currency;
import rpg.core.currency.EquipmentPurchase;
import rpg.core.item.CosmeticApplication;
import rpg.core.item.GearRepair;
import rpg.core.item.ItemConfig;
import rpg.core.item.ItemMessageKeys;
import rpg.core.item.VendorTransaction;
import rpg.core.message.MessageKey;
import rpg.core.message.Messages;

/**
 * Der Händler im Spiel — Rechtsklick, Fenster, Klick, Buchung (FR-060 bis FR-065).
 *
 * <p><b>Diese Klasse entscheidet nichts.</b> Ob ein Kauf zustande kommt, sagt
 * {@link VendorTransaction}; ob eine Stufe steigt, sagt {@link EquipmentPurchase} aus B08b; ob ein
 * Gegenstand am Charakter hängt, sagt {@code BoundEquipment} aus B07. Hier wird übersetzt: Klick zu
 * Aufruf, Ergebnis zu Meldung, Erfolg zu Stapel.
 *
 * <p><b>Kein zweiter Kaufmechanismus für Stufen</b> (FR-061, FR-079). Der Aufstieg geht durch
 * dieselbe Route, die B08bs Fenster benutzt. Ein zweiter Weg hätte seine eigene Reihenfolge von
 * Prüfungen, und die beiden wären nach dem ersten Balancing verschieden.
 *
 * <p><b>Jeder Klick wird abgebrochen.</b> Ein Händlerfenster, in dem sich Stapel verschieben lassen,
 * ist ein Fenster, aus dem sich Gegenstände holen lassen, die nie bezahlt wurden — und der Fall
 * fällt erst auf, wenn er ausgenutzt wurde. Was passieren soll, passiert danach im Code, nicht durch
 * Vanillas Klickverarbeitung.
 *
 * <p><b>Nichts bleibt halb gebucht</b> (FR-065). Das Fenster hält keinen Vorgang: eine Buchung ist
 * abgeschlossen, bevor der nächste Klick möglich ist. Logout, Zonenwechsel und Serverstopp
 * vergessen deshalb nur, welches Fenster offen war.
 */
public final class VendorListener implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    /**
     * Der Weg zum Stufenaufstieg — B08bs {@link EquipmentPurchase#buyNext}, als Naht.
     *
     * <p><b>Eine Naht, kein zweiter Mechanismus</b> (FR-061, FR-079). Sie gibt B08bs eigenen
     * Ergebnistyp zurück: wer sie anders bedient als mit {@code EquipmentPurchase::buyNext}, müsste
     * auch {@link EquipmentPurchase.Result} nachbauen — und das fällt auf.
     *
     * <p>Dieselbe Überlegung wie bei {@code ConsumableBuffs.BuffSink} und
     * {@code ConsumableUseListener.Resources}: eine Abhängigkeit auf die ganze Klasse zwingt jeden
     * Test, ihre Konfiguration mitzubauen — hier B07s {@code ClassConfig}, die in einem Prüfstand
     * liegt, den {@code rpg-platform} gar nicht sieht.
     */
    @FunctionalInterface
    public interface TierRoute {
        EquipmentPurchase.Result buyNext(UUID characterId, LadderSlot slot);
    }

    private final Supplier<ItemConfig> config;
    private final VendorMenu menu;
    private final VendorTransaction transactions;
    private final TierRoute tiers;

    /** Die bezahlte Instandsetzung — die Regel liegt in {@code rpg-core}, wie ueberall hier. */
    private final GearRepair repairs;

    /** Besitz und Anwendung der Trimfarben — die Regel liegt in {@code rpg-core} (US6). */
    private final CosmeticApplication cosmetics;

    /**
     * Ob dieser Vermerk eine Bindung ist — B07s {@code BoundEquipment::isBound}, als Naht.
     *
     * <p>Gefragt, nicht entschieden (FR-063, FR-079). Als ganze Klasse hereingereicht müsste jeder
     * Test B07s {@code ClassConfig} mitbauen, um eine Frage zu stellen, die ein Prädikat ist.
     */
    private final Predicate<String> bound;

    private final Currency currency;
    private final ItemStackFactory factory;
    private final Function<UUID, Optional<UUID>> characterOf;
    private final Messages messages;
    private final Logger logger;

    /** Welches Fenster gerade offen ist, je Spieler. Nur das — kein Vorgang, kein Warenkorb. */
    private final Map<UUID, String> open = new HashMap<>();

    public VendorListener(
            Supplier<ItemConfig> config,
            VendorMenu menu,
            VendorTransaction transactions,
            TierRoute tiers,
            GearRepair repairs,
            CosmeticApplication cosmetics,
            Predicate<String> bound,
            Currency currency,
            ItemStackFactory factory,
            Function<UUID, Optional<UUID>> characterOf,
            Messages messages,
            Logger logger) {
        this.config = Objects.requireNonNull(config, "config");
        this.menu = Objects.requireNonNull(menu, "menu");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.tiers = Objects.requireNonNull(tiers, "tiers");
        this.repairs = Objects.requireNonNull(repairs, "repairs");
        this.cosmetics = Objects.requireNonNull(cosmetics, "cosmetics");
        this.bound = Objects.requireNonNull(bound, "bound");
        this.currency = Objects.requireNonNull(currency, "currency");
        this.factory = Objects.requireNonNull(factory, "factory");
        this.characterOf = Objects.requireNonNull(characterOf, "characterOf");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /** Rechtsklick auf den NPC öffnet sein Fenster — mit dem Bestand <em>seiner</em> Region. */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        Optional<String> zone = VendorNpc.zoneOf(event.getRightClicked());
        if (zone.isEmpty()) {
            return;
        }
        // Abbrechen, bevor Vanilla sein eigenes Handelsfenster oeffnet.
        event.setCancelled(true);
        openFor(event.getPlayer(), zone.get());
    }

    /** Öffnet das Fenster. Auch von einem Befehl aus benutzbar. */
    public void openFor(Player player, String zoneKey) {
        Optional<UUID> character = characterOf.apply(player.getUniqueId());
        if (character.isEmpty()) {
            return;
        }
        long balance = currency.balanceOrZero(character.get());
        player.openInventory(menu.build(config.get().vendorOf(zoneKey), balance));
        open.put(player.getUniqueId(), zoneKey);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        String zoneKey = open.get(event.getWhoClicked().getUniqueId());
        if (zoneKey == null || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        // Erst abbrechen, dann handeln - in dieser Reihenfolge, damit kein frueher Rueckweg unten
        // einen beweglichen Stapel hinterlaesst.
        event.setCancelled(true);

        Optional<UUID> character = characterOf.apply(player.getUniqueId());
        if (character.isEmpty()) {
            return;
        }
        Inventory clicked = event.getClickedInventory();
        if (clicked == null) {
            return;
        }
        if (clicked.equals(event.getView().getBottomInventory())) {
            sell(player, character.get(), event.getCurrentItem());
            return;
        }
        handleServiceOrPurchase(player, character.get(), zoneKey, event.getSlot());
    }

    private void handleServiceOrPurchase(Player player, UUID characterId, String zoneKey, int slot) {
        Optional<LadderSlot> upgrade = VendorMenu.upgradeAt(slot);
        if (upgrade.isPresent()) {
            advance(player, characterId, upgrade.get());
            return;
        }
        Optional<LadderSlot> repair = VendorMenu.repairAt(slot);
        if (repair.isPresent()) {
            repair(player, characterId, repair.get());
            return;
        }
        menu.templateAt(config.get().vendorOf(zoneKey), slot)
                .ifPresent(
                        templateKey -> {
                            // Eine Farbe, die dem Charakter schon gehoert, wird nicht ein
                            // zweites Mal verkauft, sondern angezogen (FR-070).
                            if (isCosmetic(templateKey)
                                    && cosmetics.owns(characterId, templateKey)) {
                                wear(player, characterId, templateKey);
                                return;
                            }
                            buy(player, characterId, zoneKey, templateKey);
                        });
    }

    /**
     * Kaufen. Die Reihenfolge steckt in {@link VendorTransaction} — hier wird nur gegeben, was
     * bezahlt wurde.
     */
    private void buy(Player player, UUID characterId, String zoneKey, String templateKey) {
        VendorTransaction.Result result = transactions.buy(characterId, zoneKey, templateKey, 1);
        if (!result.isSuccess()) {
            tell(player, refusalOf(result.outcome()), Map.of());
            return;
        }
        if (isCosmetic(templateKey)) {
            // Eine Trimfarbe ist kein Stapel im Inventar, sondern ein Besitzvermerk am
            // Charakter (data-model.md §3). Ein Item daraus zu machen hiesse, sie verlierbar,
            // handelbar und in einer Enderchest lagerbar zu machen - drei Eigenschaften, die
            // niemand fuer sie bestellt hat.
            cosmetics.grant(characterId, templateKey);
            tell(
                    player,
                    ItemMessageKeys.VENDOR_BOUGHT,
                    Map.of("amount", String.valueOf(result.amount())));
            return;
        }
        Optional<ItemStack> given = factory.create(templateKey, 1);
        if (given.isEmpty()) {
            // Kann nur passieren, wenn zwischen Pruefung und Uebergabe neu geladen wurde. Der
            // Vorgang ist gebucht; das im Log zu haben ist besser, als es zu verschweigen.
            logger.warning(
                    () -> "[item] vendor: paid for " + templateKey + " but the template is gone");
            return;
        }
        player.getInventory().addItem(given.get());
        tell(player, ItemMessageKeys.VENDOR_BOUGHT, Map.of("amount", String.valueOf(result.amount())));
    }

    private boolean isCosmetic(String templateKey) {
        return config.get()
                .template(templateKey)
                .filter(template -> template.category() == rpg.core.item.ItemCategory.COSMETIC)
                .isPresent();
    }

    /**
     * Eine besessene Farbe anziehen (FR-069 bis FR-071).
     *
     * <p>Kostet nichts: bezahlt wurde beim Kauf. Ein Wechsel zwischen zwei gekauften Farben ist
     * frei — sonst waere die zweite Farbe eine Falle statt einer Auswahl.
     */
    private void wear(Player player, UUID characterId, String templateKey) {
        CosmeticApplication.Outcome outcome = cosmetics.apply(characterId, templateKey);
        tell(
                player,
                switch (outcome) {
                    case DONE -> ItemMessageKeys.COSMETIC_APPLIED;
                    case NOT_TOP_TIER -> ItemMessageKeys.COSMETIC_NOT_TOP_TIER;
                    case ALREADY_APPLIED -> ItemMessageKeys.COSMETIC_ALREADY_WORN;
                    case NOT_OWNED, UNKNOWN_TEMPLATE -> null;
                },
                Map.of());
    }

    /**
     * Verkaufen — und Klassenausrüstung wird abgewiesen (FR-063).
     *
     * <p><b>Die Bindung wird bei B07 erfragt</b>, nicht hier entschieden. Eine zweite Prüfung wäre
     * eine zweite Wahrheit, und die drei Entsorgungswege würden nach dem nächsten Umbau
     * unterschiedlich antworten (FR-079).
     */
    private void sell(Player player, UUID characterId, ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return;
        }
        boolean isBound =
                rpg.platform.classes.BoundItemTag.tagOf(stack).map(bound::test).orElse(false);
        Optional<String> templateKey = ItemTag.templateOf(stack);
        if (templateKey.isEmpty() && !isBound) {
            // Kein Gegenstand dieses Blocks. Der Haendler nimmt ihn nicht an, und das ist keine
            // Ablehnung, ueber die jemand etwas erfahren muss.
            return;
        }

        int amount = stack.getAmount();
        VendorTransaction.Result result =
                transactions.sell(characterId, templateKey.orElse(""), amount, isBound);
        if (!result.isSuccess()) {
            tell(player, refusalOf(result.outcome()), Map.of());
            return;
        }
        // Erst nachdem gebucht wurde. Andersherum waere der Stapel weg, wenn die Buchung scheitert.
        stack.setAmount(0);
        tell(player, ItemMessageKeys.VENDOR_SOLD, Map.of("amount", String.valueOf(result.amount())));
    }

    /**
     * Eine Leiter reparieren — die einzige Instandsetzung, die es gibt (FR-052).
     *
     * <p>Amboss, Zauberpult und Schleifstein sind für gebundene Ausrüstung gesperrt
     * ({@code RepairRouteLockListener}, FR-056). Wäre einer davon offen, wäre er der billigere Weg,
     * und die Coin-Senke aus ADR-017 hätte kein Wasser.
     */
    private void repair(Player player, UUID characterId, LadderSlot slot) {
        GearRepair.Result result = repairs.repair(characterId, slot);
        if (result.isSuccess()) {
            tell(
                    player,
                    ItemMessageKeys.REPAIR_DONE,
                    Map.of("price", String.valueOf(result.price())));
            // Das Fenster zeigt Preise, und einer davon hat sich gerade geaendert.
            openFor(player, open.get(player.getUniqueId()));
            return;
        }
        tell(
                player,
                switch (result.outcome()) {
                    case NOT_WORN -> ItemMessageKeys.REPAIR_NOT_WORN;
                    case NOT_ENOUGH_COINS -> ItemMessageKeys.VENDOR_NOT_ENOUGH;
                    case UNKNOWN_CHARACTER, DONE -> null;
                },
                Map.of("price", String.valueOf(result.price())));
    }

    /** Eine Stufe kaufen — durch B08bs Route, nicht durch eine eigene (FR-061, FR-062). */
    private void advance(Player player, UUID characterId, LadderSlot slot) {
        EquipmentPurchase.Result result = tiers.buyNext(characterId, slot);
        if (result.isSuccess()) {
            return;
        }
        if (result.outcome() == EquipmentPurchase.Outcome.NOT_ENOUGH_COINS) {
            tell(player, ItemMessageKeys.VENDOR_NOT_ENOUGH, Map.of());
        }
        // Bei REFUSED sagt B07 dem Spieler bereits, woran es lag - eine zweite Meldung waere eine
        // zweite Wahrheit ueber denselben Vorgang.
    }

    /** Fenster zu: nur vergessen, welches offen war. Es hängt nichts daran (FR-065). */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        open.remove(event.getPlayer().getUniqueId());
    }

    /**
     * Vergisst das Fenster dieses Spielers.
     *
     * <p><b>Kein eigener {@code PlayerQuitEvent}-Zuhoerer</b>, und das ist keine Stilfrage:
     * {@code NoCompetingSessionListenersTest} in B01 verbietet einen zweiten Weg in den
     * Sitzungslebenszyklus, weil der zweite Weg keine der Absicherungen des ersten hat. Der erste
     * Anlauf hier hatte genau diesen Zuhoerer, und der Waechter hat ihn gefunden.
     *
     * <p>Aufgerufen wird das vom Sitzungsende aus, genau wie B09s {@code waypointMenu.forget}.
     */
    public void forget(UUID playerId) {
        open.remove(playerId);
    }

    /** Beim Herunterfahren — dieselbe Zusage, nur für alle auf einmal. */
    public void clear() {
        open.clear();
    }

    /** Ob für diesen Spieler ein Händlerfenster offen ist. Für Tests und für das Herunterfahren. */
    public boolean hasOpenWindow(UUID playerId) {
        return open.containsKey(playerId);
    }

    private static MessageKey refusalOf(VendorTransaction.Outcome outcome) {
        return switch (outcome) {
            case NOT_SELLABLE, UNKNOWN_TEMPLATE -> ItemMessageKeys.VENDOR_NOT_SELLABLE;
            case BOUND_EQUIPMENT -> ItemMessageKeys.VENDOR_BOUND;
            case NOT_IN_STOCK -> ItemMessageKeys.VENDOR_NOT_IN_STOCK;
            case NOT_ENOUGH_COINS -> ItemMessageKeys.VENDOR_NOT_ENOUGH;
            case NO_ROOM -> ItemMessageKeys.VENDOR_NO_ROOM;
            case DONE -> null;
        };
    }

    private void tell(Player player, MessageKey key, Map<String, String> placeholders) {
        if (key == null || !messages.contains(key)) {
            return;
        }
        player.sendMessage(LEGACY.deserialize(messages.get(key, placeholders)));
    }
}
