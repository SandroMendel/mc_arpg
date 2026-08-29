package rpg.platform.item;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import rpg.core.classes.LadderSlot;
import rpg.core.item.ConsumableEffect;
import rpg.core.item.ItemCategory;
import rpg.core.item.ItemConfig;
import rpg.core.item.ItemTemplate;
import rpg.core.item.Items;
import rpg.core.item.LootTables;
import rpg.core.item.Rarity;
import rpg.core.item.RepairPricing;
import rpg.core.item.VendorStock;
import rpg.core.item.WearCurve;
import rpg.core.message.MapMessages;
import rpg.core.message.Messages;

/**
 * <b>Jeder Knopf sagt, was er tut und was er kostet.</b>
 *
 * <p>Ein Knopf, der nur seinen Namen trägt, verlangt vom Spieler, ihn auszuprobieren — und
 * „ausprobieren" heißt bei einem Kauf: bezahlen. Bei einem unumkehrbaren Vorgang ist das keine
 * zumutbare Art, etwas herauszufinden.
 *
 * <p><b>Warum es diesen Test überhaupt gibt.</b> Jedes andere Fenster im Projekt hat einen —
 * {@code ClassSelectionMenuTest}, {@code WaypointMenuTest}, {@code CurrencyMenuTest} — und sie alle
 * sehen sich das gebaute Inventar an. B11s Händlerfenster war die Ausnahme: es wurde in einem Test
 * nur <em>konstruiert</em>, damit der Zuhörer bauen konnte, und was es zeichnete, hat nie jemand
 * geprüft.
 */
class VendorMenuTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private VendorMenu menu;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        Messages messages = messages();
        menu = new VendorMenu(new ItemStackFactory(new ConfiguredItems(), messages), messages);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("der Bestand steht in KONFIGURATIONSREIHENFOLGE")
    void thestockIsInConfigurationOrder() {
        // Ein Fenster, das nach jedem Neustart anders aussieht, ist ein Fenster, in dem niemand
        // etwas wiederfindet - dafuer haelt VendorStock eine LinkedHashMap.
        Inventory inventory = menu.build(stock(), 10_000L, VendorMenu.Offers.unknown());

        assertThat(name(inventory.getItem(0))).isEqualTo("Minor Healing Potion");
        assertThat(name(inventory.getItem(1))).isEqualTo("Mana Potion");
    }

    @Test
    @DisplayName("an der Ware steht der PREIS")
    void thepriceIsOnTheGoods() {
        Inventory inventory = menu.build(stock(), 10_000L, VendorMenu.Offers.unknown());

        assertThat(lore(inventory.getItem(0))).contains("12 coins");
    }

    @Test
    @DisplayName("und ein Preis, den der Kontostand nicht deckt, sieht anders aus")
    void andapriceTheBalanceCannotCoverLooksDifferent() {
        // Angezeigt, nicht zugesagt: geprueft wird beim Abschluss (FR-064). Was hier steht, ist
        // eine Auskunft - aber eine, die dem Spieler den Klick erspart.
        //
        // Der Unterschied ist eine FARBE und kein Text: beide Zeilen sagen "12 coins", die eine
        // in Gold, die andere in Rot. Ein Vergleich der reinen Zeichen faende ihn nicht - der
        // erste Anlauf dieses Tests tat genau das und meldete richtiges Verhalten als Fehler.
        Inventory rich = menu.build(stock(), 10_000L, VendorMenu.Offers.unknown());
        Inventory poor = menu.build(stock(), 0L, VendorMenu.Offers.unknown());

        assertThat(priceColour(poor.getItem(0))).isNotEqualTo(priceColour(rich.getItem(0)));
    }

    @Test
    @DisplayName("die Reparatur nennt ZUSTAND und PREIS")
    void therepairNamesConditionAndPrice() {
        Inventory inventory =
                menu.build(
                        stock(),
                        10_000L,
                        new VendorMenu.Offers(
                                Map.of(LadderSlot.ARMOR, new VendorMenu.RepairOffer(68.0, 380L)),
                                Map.of()));

        assertThat(lore(inventory.getItem(VendorMenu.repairSlot(LadderSlot.ARMOR))))
                .as("ohne den Zustand waere der Preis unerklaerlich und saehe nach Willkuer aus")
                .contains("68")
                .contains("380");
    }

    @Test
    @DisplayName("und bei Preis 0 sagt sie: da ist NICHTS zu reparieren")
    void andatpriceZeroItSaysThereIsNothingToRepair() {
        // Null heisst "nichts abgenutzt" und nicht "umsonst" - fuer den Spieler ein Unterschied,
        // und FR-054 macht ihn auch im Ergebnis: die Reparatur wird abgelehnt.
        Inventory inventory =
                menu.build(
                        stock(),
                        10_000L,
                        new VendorMenu.Offers(
                                Map.of(LadderSlot.WEAPON, new VendorMenu.RepairOffer(100.0, 0L)),
                                Map.of()));

        assertThat(lore(inventory.getItem(VendorMenu.repairSlot(LadderSlot.WEAPON))))
                .contains("nothing to repair");
    }

    @Test
    @DisplayName("der Aufstieg nennt PREIS, STUFE und MINDESTLEVEL")
    void theupgradeNamesPriceTierAndLevel() {
        Inventory inventory =
                menu.build(
                        stock(),
                        10_000L,
                        new VendorMenu.Offers(
                                Map.of(),
                                Map.of(
                                        LadderSlot.ARMOR,
                                        new VendorMenu.UpgradeOffer(
                                                OptionalLong.of(2400L),
                                                OptionalInt.of(29),
                                                OptionalInt.of(3)))));

        String line = lore(inventory.getItem(VendorMenu.upgradeSlot(LadderSlot.ARMOR)));
        assertThat(line).contains("2400").contains("29").contains("3");
    }

    @Test
    @DisplayName("auf der Hoechststufe sagt er, dass es nichts mehr zu holen gibt")
    void atthetopItSaysThereIsNothingLeft() {
        Inventory inventory =
                menu.build(
                        stock(),
                        10_000L,
                        new VendorMenu.Offers(
                                Map.of(), Map.of(LadderSlot.WEAPON, VendorMenu.UpgradeOffer.atTop())));

        assertThat(lore(inventory.getItem(VendorMenu.upgradeSlot(LadderSlot.WEAPON))))
                .as("ein Klick, der es herausfindet, waere die schlechtere Auskunft")
                .contains("Top tier reached");
    }

    @Test
    @DisplayName("ohne Angaben stehen die Knoepfe da und sagen nur, was sie tun")
    void withoutOffersTheButtonsOnlySayWhatTheyDo() {
        Inventory inventory = menu.build(stock(), 0L, VendorMenu.Offers.unknown());

        assertThat(inventory.getItem(VendorMenu.repairSlot(LadderSlot.ARMOR))).isNotNull();
        assertThat(name(inventory.getItem(VendorMenu.repairSlot(LadderSlot.ARMOR))))
                .isEqualTo("Repair armour");
    }

    @Test
    @DisplayName("jede Leiter hat ihren EIGENEN Platz - kein Knopf muss raten")
    void eachladderHasItsOwnSlot() {
        assertThat(VendorMenu.repairSlot(LadderSlot.ARMOR))
                .isNotEqualTo(VendorMenu.repairSlot(LadderSlot.WEAPON));
        assertThat(VendorMenu.repairAt(VendorMenu.repairSlot(LadderSlot.WEAPON)))
                .contains(LadderSlot.WEAPON);
        assertThat(VendorMenu.upgradeAt(VendorMenu.upgradeSlot(LadderSlot.ARMOR)))
                .contains(LadderSlot.ARMOR);
    }

    @Test
    @DisplayName("und ein Klick auf einen Warenplatz meint eine VORLAGE, kein Dienst")
    void andaclickOnAStockSlotMeansATemplate() {
        assertThat(menu.templateAt(stock(), 0)).contains("potion.minor-healing");
        assertThat(menu.templateAt(stock(), VendorMenu.SLOT_SELL))
                .as("die Dienste liegen ausserhalb des Bestands")
                .isEmpty();
    }

    @Test
    @DisplayName("die Knoepfe sind NICHT kursiv")
    void thebuttonsAreNotItalic() {
        Inventory inventory = menu.build(stock(), 0L, VendorMenu.Offers.unknown());
        ItemStack sell = inventory.getItem(VendorMenu.SLOT_SELL);

        assertThat(sell.getItemMeta().displayName().decoration(
                        net.kyori.adventure.text.format.TextDecoration.ITALIC))
                .isEqualTo(net.kyori.adventure.text.format.TextDecoration.State.FALSE);
    }

    // --- Hilfsmittel ---------------------------------------------------------------

    /** Die Farbe der letzten Lore-Zeile — das ist die Preiszeile. */
    private static net.kyori.adventure.text.format.TextColor priceColour(ItemStack stack) {
        List<Component> lore = stack.getItemMeta().lore();
        return lore.getLast().color();
    }

    private static String name(ItemStack stack) {
        Component name = stack == null ? null : stack.getItemMeta().displayName();
        return name == null ? null : PLAIN.serialize(name);
    }

    private static String lore(ItemStack stack) {
        List<Component> lore = stack == null ? null : stack.getItemMeta().lore();
        return lore == null ? "" : lore.stream().map(PLAIN::serialize).reduce("", (a, b) -> a + " | " + b);
    }

    private static VendorStock stock() {
        Map<String, Long> prices = new LinkedHashMap<>();
        prices.put("potion.minor-healing", 12L);
        prices.put("potion.mana", 60L);
        return new VendorStock(prices);
    }

    private static Messages messages() {
        Map<String, String> texts = new LinkedHashMap<>();
        texts.put("item.vendor.title", "Merchant");
        texts.put("item.vendor.sell", "&eSell");
        texts.put("item.vendor.sell.lore", "&7Click an item in your own inventory to sell it.");
        texts.put("item.vendor.repair.armor-ladder", "&eRepair armour");
        texts.put("item.vendor.repair.weapon-ladder", "&eRepair weapon");
        texts.put("item.vendor.repair.armor-ladder.lore", "&7Condition {condition}%. Restores it fully for {price} coins.");
        texts.put("item.vendor.repair.weapon-ladder.lore", "&7Condition {condition}%. Restores it fully for {price} coins.");
        texts.put("item.vendor.repair.armor-ladder.lore-intact", "&7Condition {condition}% - nothing to repair.");
        texts.put("item.vendor.repair.weapon-ladder.lore-intact", "&7Condition {condition}% - nothing to repair.");
        texts.put("item.vendor.upgrade.armor-ladder", "&eUpgrade armour");
        texts.put("item.vendor.upgrade.weapon-ladder", "&eUpgrade weapon");
        texts.put("item.vendor.upgrade.armor-ladder.lore", "&7Tier {tier} for {price} coins, from level {level}.");
        texts.put("item.vendor.upgrade.weapon-ladder.lore", "&7Tier {tier} for {price} coins, from level {level}.");
        texts.put("item.vendor.upgrade.armor-ladder.lore-top", "&7Top tier reached. Nothing left to buy here.");
        texts.put("item.vendor.upgrade.weapon-ladder.lore-top", "&7Top tier reached. Nothing left to buy here.");
        texts.put("item.vendor.price", "&6{price} coins");
        texts.put("item.vendor.price-short", "&c{price} coins");
        texts.put("item.potion.minor-healing.name", "Minor Healing Potion");
        texts.put("item.potion.mana.name", "Mana Potion");
        texts.put("item.rarity.common.name", "Common");
        texts.put("item.rarity.uncommon.name", "Uncommon");
        texts.put("item.effect.heal", "&cHeals &f{amount}&c health");
        texts.put("item.effect.mana", "&9Restores &f{amount}&9 mana");
        texts.put("item.effect.cooldown", "&8Cooldown {seconds}s");
        texts.put("item.effect.splash", "&dThrown - affects everyone it hits");
        return new MapMessages(texts);
    }

    private static ItemConfig config() {
        Map<String, ItemTemplate> templates = new LinkedHashMap<>();
        templates.put(
                "potion.minor-healing",
                template("potion.minor-healing", "SPLASH_POTION", Rarity.COMMON, ConsumableEffect.healing(40.0, Duration.ofSeconds(8))));
        templates.put(
                "potion.mana",
                template("potion.mana", "POTION", Rarity.UNCOMMON, ConsumableEffect.mana(110.0, Duration.ofSeconds(8))));
        return new ItemConfig(
                templates,
                WearCurve.defaults(),
                new RepairPricing(List.of(0L, 40L)),
                LootTables.empty(),
                Map.of());
    }

    private static ItemTemplate template(
            String key, String material, Rarity rarity, ConsumableEffect effect) {
        return new ItemTemplate(
                key, ItemCategory.CONSUMABLE, material, rarity, null, null, 3L, null, effect, null);
    }

    private static final class ConfiguredItems implements Items {

        @Override
        public Optional<ItemTemplate> template(String templateKey) {
            return Optional.ofNullable(config().templates().get(templateKey));
        }

        @Override
        public java.util.Collection<String> templateKeys() {
            return config().templates().keySet();
        }

        @Override
        public OptionalLong sellPriceOf(String templateKey) {
            return template(templateKey).map(ItemTemplate::sellPriceOrNone).orElse(OptionalLong.empty());
        }

        @Override
        public WearCurve wear() {
            return config().wear();
        }
    }
}
