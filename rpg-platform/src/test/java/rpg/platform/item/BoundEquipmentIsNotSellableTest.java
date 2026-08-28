package rpg.platform.item;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import rpg.core.classes.BoundEquipment;
import rpg.core.session.CharacterClass;
import rpg.core.classes.LadderSlot;
import rpg.core.currency.BookingReason;
import rpg.core.currency.BookingResult;
import rpg.core.currency.Currency;
import rpg.core.item.ConsumableEffect;
import rpg.core.item.ItemCategory;
import rpg.core.item.ItemConfig;
import rpg.core.item.ItemTemplate;
import rpg.core.item.LootTables;
import rpg.core.item.Rarity;
import rpg.core.item.RepairPricing;
import rpg.core.item.VendorTransaction;
import rpg.core.item.WearCurve;
import rpg.platform.classes.BoundItemFactory;
import rpg.platform.classes.BoundItemTag;

/**
 * FR-063, SC-008 — <b>Klassenausrüstung ist unverkäuflich.</b>
 *
 * <p>ADR-018: die Ausrüstung <em>ist</em> der Fortschritt. Sie zu verkaufen hieße, eine Stufe gegen
 * Coins einzutauschen, die sie nicht zurückkaufen können — und der Händler ist nur einer von drei
 * Wegen, auf denen das versucht wird (Enderchest und Mülleimer sind die anderen).
 *
 * <p><b>Die Antwort kommt von B07</b> (FR-079). Dieser Block liest den Vermerk vom Gegenstand und
 * <em>fragt</em> {@link BoundEquipment}; er entscheidet nichts selbst. Eine zweite Prüfung wäre eine
 * zweite Wahrheit, und die drei Wege würden nach dem nächsten Umbau unterschiedlich antworten. Genau
 * das prüft der letzte Test hier nach.
 */
class BoundEquipmentIsNotSellableTest {

    private static final Path ITEM_PACKAGE =
            Path.of("src", "main", "java", "rpg", "platform", "item");

    private static final UUID CHARACTER = UUID.randomUUID();
    private static final String ZONE = "greenfields";

    private final RecordingCurrency currency = new RecordingCurrency();
    private final VendorTransaction vendor =
            new VendorTransaction(BoundEquipmentIsNotSellableTest::config, currency, (c, t, a) -> true);

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("der Vermerk von B07 ist vom Gegenstand lesbar - und nur von einem gebundenen")
    void theBindingTagIsReadableFromTheStack() {
        String tag = BoundEquipment.tagFor(CHARACTER, CharacterClass.WARRIOR, LadderSlot.ARMOR);

        assertThat(BoundItemTag.tagOf(boundStack(tag))).contains(tag);
        assertThat(BoundItemTag.tagOf(new ItemStack(Material.POTION)))
                .as("ein gewoehnlicher Gegenstand traegt keinen - sonst waere nichts verkaeuflich")
                .isEmpty();
    }

    @Test
    @DisplayName("Klassenruestung wird abgewiesen - und NICHTS gebucht")
    void boundArmorIsRefused() {
        String tag = BoundEquipment.tagFor(CHARACTER, CharacterClass.WARRIOR, LadderSlot.ARMOR);

        VendorTransaction.Result result = sellWhatIsOn(boundStack(tag));

        assertThat(result.outcome()).isEqualTo(VendorTransaction.Outcome.BOUND_EQUIPMENT);
        assertThat(currency.bookings).isEmpty();
    }

    @Test
    @DisplayName("und die Klassenwaffe ebenso")
    void theBoundWeaponIsRefusedToo() {
        String tag = BoundEquipment.tagFor(CHARACTER, CharacterClass.WARRIOR, LadderSlot.WEAPON);

        assertThat(sellWhatIsOn(boundStack(tag)).outcome())
                .isEqualTo(VendorTransaction.Outcome.BOUND_EQUIPMENT);
        assertThat(currency.bookings).isEmpty();
    }

    @Test
    @DisplayName("ein gewoehnlicher Trank dagegen bringt Coins - sonst pruefte der Test nichts")
    void anOrdinaryPotionStillSells() {
        ItemStack potion = new ItemStack(Material.POTION);

        VendorTransaction.Result result =
                vendor.sell(
                        CHARACTER,
                        "potion.test",
                        1,
                        BoundItemTag.tagOf(potion).map(BoundEquipmentIsNotSellableTest::isBound)
                                .orElse(false));

        assertThat(result.isSuccess()).isTrue();
        assertThat(currency.bookings).containsExactly(BookingReason.VENDOR_SALE);
    }

    @Test
    @DisplayName("KEINE Klasse dieses Blocks entscheidet die Bindung selbst")
    void nothingHereDecidesTheBindingItself() throws IOException {
        try (var sources = Files.walk(ITEM_PACKAGE)) {
            var offenders =
                    sources.filter(path -> path.toString().endsWith(".java"))
                            .filter(
                                    path -> {
                                        try {
                                            String code = codeOnly(Files.readString(path));
                                            // Der Vermerk selbst zu bauen oder seinen Schluessel zu
                                            // kennen waere die zweite Wahrheit.
                                            return code.contains("class_bound")
                                                    || code.contains("BoundEquipment.tagFor(");
                                        } catch (IOException failure) {
                                            throw new IllegalStateException(failure);
                                        }
                                    })
                            .map(path -> path.getFileName().toString())
                            .toList();

            assertThat(offenders)
                    .as("gefragt wird B07 - hier wird nur gelesen und weitergereicht (FR-079)")
                    .isEmpty();
        }
    }

    // --- Hilfsmittel ---------------------------------------------------------------

    /**
     * Verkauft, was auf dem Stapel steht — auf demselben Weg wie {@code VendorListener}: Vermerk
     * lesen, B07 fragen, Antwort weiterreichen.
     */
    private VendorTransaction.Result sellWhatIsOn(ItemStack stack) {
        boolean bound = BoundItemTag.tagOf(stack).map(BoundEquipmentIsNotSellableTest::isBound).orElse(false);
        return vendor.sell(CHARACTER, "potion.test", stack.getAmount(), bound);
    }

    /**
     * B07s Antwort, ohne B07s Konfiguration.
     *
     * <p>{@code BoundEquipment.isBound} braucht eine {@code ClassConfig}, und die liegt in einem
     * Prüfstand, den {@code rpg-platform} nicht sieht. Geprüft wird hier ohnehin etwas anderes: dass
     * die Antwort <em>von dort</em> kommt und ungeprüft weitergereicht wird. Das Verhalten von
     * {@code isBound} selbst prüft B07 in {@code BoundEquipmentTest}.
     */
    private static boolean isBound(String tag) {
        return tag != null && !tag.isBlank();
    }

    private static ItemStack boundStack(String tag) {
        ItemStack stack = new ItemStack(Material.IRON_CHESTPLATE);
        ItemMeta meta = stack.getItemMeta();
        BoundItemFactory.markBound(meta, tag);
        stack.setItemMeta(meta);
        return stack;
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
                Map.of(ZONE, new rpg.core.item.VendorStock(Map.of("potion.test", 12L))));
    }

    /** Kommentare weg — eine Erklärung ist kein Aufruf. */
    private static String codeOnly(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
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
