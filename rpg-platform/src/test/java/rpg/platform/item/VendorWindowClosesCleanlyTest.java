package rpg.platform.item;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import rpg.core.currency.BookingReason;
import rpg.core.currency.BookingResult;
import rpg.core.currency.Currency;
import rpg.core.currency.EquipmentPurchase;
import rpg.core.item.ConsumableEffect;
import rpg.core.item.ItemCategory;
import rpg.core.item.ItemConfig;
import rpg.core.item.ItemTemplate;
import rpg.core.item.LootTables;
import rpg.core.item.Rarity;
import rpg.core.item.RepairPricing;
import rpg.core.item.VendorStock;
import rpg.core.item.VendorTransaction;
import rpg.core.item.WearCurve;
import rpg.core.message.MapMessages;

/**
 * FR-065 — <b>ein Abbruch mitten im Vorgang hinterlässt nichts halb Gebuchtes.</b>
 *
 * <p><b>Die Zusage wird durch Bauart eingelöst, nicht durch Aufräumen.</b> Das Fenster hält keinen
 * Vorgang: kein Warenkorb, keine Reservierung, kein „ausstehender Kauf". Eine Buchung ist
 * abgeschlossen, bevor der nächste Klick möglich ist. Was Logout, Fensterschluss und Serverstopp
 * also vergessen müssen, ist genau eine Sache — welches Fenster offen war.
 *
 * <p>Das ist die schwer zu prüfende Art von Anforderung, weil sie eine <em>Abwesenheit</em>
 * behauptet. Geprüft wird sie deshalb von zwei Seiten: das Vergessen selbst, und dass es nichts
 * gibt, das dabei liegenbleiben könnte.
 */
class VendorWindowClosesCleanlyTest {

    private static final Logger QUIET = Logger.getLogger(VendorWindowClosesCleanlyTest.class.getName());
    private static final String ZONE = "greenfields";

    private ServerMock server;
    private WorldMock world;
    private PlayerMock player;
    private VendorListener listener;

    private final UUID character = UUID.randomUUID();
    private final RecordingCurrency currency = new RecordingCurrency();

    /** Ein Zustandsspeicher ohne geladenen Charakter: jede Reparatur wird abgewiesen, ohne zu buchen. */
    private final rpg.core.item.DefaultGearConditions conditions =
            new rpg.core.item.DefaultGearConditions(
                    rpg.core.item.WearCurve::defaults,
                    new rpg.core.item.GearConditionRepository() {
                        @Override
                        public java.util.concurrent.CompletableFuture<Optional<rpg.core.item.GearCondition>> find(
                                UUID characterId) {
                            return java.util.concurrent.CompletableFuture.completedFuture(
                                    Optional.empty());
                        }

                        @Override
                        public void markDirty(UUID characterId) {}
                    },
                    new rpg.core.event.DefaultEventBus(QUIET),
                    java.time.Clock.systemUTC());

    @BeforeEach
    void setUp() {
        QUIET.setLevel(Level.OFF);
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        player = server.addPlayer();

        listener =
                new VendorListener(
                        VendorWindowClosesCleanlyTest::config,
                        new VendorMenu(new ItemStackFactory(new ConfiguredItems(), messages()), messages()),
                        new VendorTransaction(
                                VendorWindowClosesCleanlyTest::config, currency, (c, t, a) -> true),
                        (characterId, slot) ->
                                new EquipmentPurchase.Result(
                                        EquipmentPurchase.Outcome.REFUSED,
                                        Optional.empty(),
                                        Optional.empty()),
                        new rpg.core.item.GearRepair(
                                VendorWindowClosesCleanlyTest::config,
                                conditions,
                                (characterId, slot) -> 1,
                                currency),
                        cosmetics(),
                        tag -> false,
                        currency,
                        new ItemStackFactory(new ConfiguredItems(), messages()),
                        playerId -> Optional.of(character),
                        messages(),
                        QUIET);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("ein Rechtsklick auf den Haendler oeffnet sein Fenster")
    void arightClickOpensTheWindow() {
        listener.openFor(player, ZONE);

        assertThat(listener.hasOpenWindow(player.getUniqueId())).isTrue();
    }

    @Test
    @DisplayName("Ausloggen vergisst das Fenster - und es war nichts gebucht")
    void aLogoutForgetsTheWindow() {
        listener.openFor(player, ZONE);

        // Nicht ueber einen eigenen Quit-Zuhoerer: den verbietet B01s NoCompetingSessionListenersTest,
        // und zu Recht. Das Sitzungsende ruft forget auf, so wie es B09s Fenster auch bekommt.
        listener.forget(player.getUniqueId());

        assertThat(listener.hasOpenWindow(player.getUniqueId())).isFalse();
        assertThat(currency.bookings)
                .as("es gab nichts Halbes zu buchen - das Fenster haelt keinen Vorgang (FR-065)")
                .isEmpty();
    }

    @Test
    @DisplayName("Fenster schliessen ebenso")
    void closingTheWindowForgetsIt() {
        listener.openFor(player, ZONE);

        listener.onClose(new org.bukkit.event.inventory.InventoryCloseEvent(player.getOpenInventory()));

        assertThat(listener.hasOpenWindow(player.getUniqueId())).isFalse();
        assertThat(currency.bookings).isEmpty();
    }

    @Test
    @DisplayName("und der Serverstopp raeumt alle Fenster auf einmal")
    void shutdownClearsEveryWindow() {
        listener.openFor(player, ZONE);

        listener.clear();

        assertThat(listener.hasOpenWindow(player.getUniqueId())).isFalse();
        assertThat(currency.bookings).isEmpty();
    }

    @Test
    @DisplayName("ein abgelehnter Aufstieg bucht nichts - die Route entscheidet, nicht das Fenster")
    void arefusedAdvanceBooksNothing() {
        listener.openFor(player, ZONE);

        // Der Aufstiegsplatz der Ruestungsleiter. Die Route sagt REFUSED, also darf nichts passieren.
        clickTopSlot(VendorMenu.upgradeSlot(rpg.core.classes.LadderSlot.ARMOR));

        assertThat(currency.bookings).isEmpty();
    }

    @Test
    @DisplayName("und ein Klick auf einen leeren Platz ebenfalls nicht")
    void aclickOnAnEmptySlotBooksNothing() {
        listener.openFor(player, ZONE);

        clickTopSlot(17);

        assertThat(currency.bookings).isEmpty();
    }

    @Test
    @DisplayName("der Haendler selbst bleibt stehen - er wird von nichts hier entfernt")
    void thevendorItselfIsUntouched() {
        Entity npc =
                new VendorNpc(QUIET)
                        .place(new Location(world, 0, 64, 0), ZONE)
                        .orElseThrow(() -> new AssertionError("nicht gesetzt"));

        listener.openFor(player, ZONE);
        listener.clear();

        assertThat(npc.isValid())
                .as("ein Fenster, das seinen NPC mitnimmt, waere ein Haendler weniger je Sitzung")
                .isTrue();
    }

    // --- Hilfsmittel ---------------------------------------------------------------

    private void clickTopSlot(int slot) {
        listener.onClick(
                new org.bukkit.event.inventory.InventoryClickEvent(
                        player.getOpenInventory(),
                        org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER,
                        slot,
                        org.bukkit.event.inventory.ClickType.LEFT,
                        org.bukkit.event.inventory.InventoryAction.PICKUP_ALL));
    }

    /** Ein Kosmetikspeicher ohne Besitz: jeder Klick faellt auf den Kauf zurueck. */
    private rpg.core.item.CosmeticApplication cosmetics() {
        rpg.core.item.CosmeticApplication application =
                new rpg.core.item.CosmeticApplication(
                        VendorWindowClosesCleanlyTest::config,
                        new rpg.core.item.CosmeticRepository() {
                            @Override
                            public java.util.concurrent.CompletableFuture<List<rpg.core.item.CosmeticUnlock>> find(
                                    UUID characterId) {
                                return java.util.concurrent.CompletableFuture.completedFuture(List.of());
                            }
                            @Override
                            public void markDirty(UUID characterId) {}
                        },
                        (characterId, slot) -> false,
                        new rpg.core.event.DefaultEventBus(QUIET),
                        java.time.Clock.systemUTC());
        application.put(character, List.of());
        return application;
    }

    private static MapMessages messages() {
        Map<String, String> texts = new LinkedHashMap<>();
        texts.put("item.vendor.title", "Merchant");
        return new MapMessages(texts);
    }

    private static ItemConfig config() {
        Map<String, ItemTemplate> templates = new LinkedHashMap<>();
        templates.put(
                "potion.test",
                new ItemTemplate(
                        "potion.test",
                        ItemCategory.CONSUMABLE,
                        "POTION",
                        Rarity.COMMON,
                        null,
                        null,
                        3L,
                        null,
                        ConsumableEffect.healing(40.0, Duration.ofSeconds(8)),
                        null));
        return new ItemConfig(
                templates,
                WearCurve.defaults(),
                new RepairPricing(List.of(0L, 40L)),
                LootTables.empty(),
                Map.of(ZONE, new VendorStock(Map.of("potion.test", 12L))));
    }

    /** Die Vorlagen der Testkonfiguration, als {@code Items}-Fassade für die Fabrik. */
    private static final class ConfiguredItems implements rpg.core.item.Items {

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
            return template(templateKey)
                    .map(ItemTemplate::sellPriceOrNone)
                    .orElse(OptionalLong.empty());
        }

        @Override
        public WearCurve wear() {
            return config().wear();
        }
    }

    private static final class RecordingCurrency implements Currency {

        private final List<BookingReason> bookings = new ArrayList<>();

        @Override
        public OptionalLong balanceOf(UUID characterId) {
            return OptionalLong.of(1000L);
        }

        @Override
        public long balanceOrZero(UUID characterId) {
            return 1000L;
        }

        @Override
        public boolean canAfford(UUID characterId, long amount) {
            return true;
        }

        @Override
        public BookingResult credit(UUID characterId, long amount, BookingReason reason) {
            bookings.add(reason);
            return BookingResult.OK;
        }

        @Override
        public BookingResult debit(UUID characterId, long amount, BookingReason reason) {
            bookings.add(reason);
            return BookingResult.OK;
        }
    }
}
