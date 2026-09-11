package rpg.core.item;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import rpg.core.currency.BookingReason;
import rpg.core.currency.BookingResult;
import rpg.core.currency.Currency;

/**
 * <b>SC-009</b> — kein Vorgang hinterlässt einen Zustand, in dem gebucht und nicht geliefert wurde.
 *
 * <p>Das ist die Zusage, die ein Spieler nicht selbst reparieren kann. Verliert er einen Trank, kann
 * er einen neuen holen; wurde ihm abgebucht und nichts gegeben, bleibt ihm nur, jemanden zu fragen.
 * Deshalb steht die Reihenfolge in {@link VendorTransaction} und wird hier <em>jede</em> Ablehnung
 * einzeln daraufhin geprüft, dass das Konto unberührt blieb.
 *
 * <p>Dieselbe Bauart und dieselbe Begründung wie {@code EquipmentPurchase} in B08b.
 */
class NothingIsBookedBeforeEverythingIsCheckedTest {

    private static final UUID CHARACTER = UUID.randomUUID();
    private static final String ZONE = "greenfields";

    private final RecordingCurrency currency = new RecordingCurrency(1000L);

    @Nested
    @DisplayName("Verkaufen")
    class Selling {

        @Test
        @DisplayName("ein verkaeuflicher Gegenstand bringt Coins - gebucht als GUTSCHRIFT")
        void asellableItemCredits() {
            VendorTransaction.Result result = vendor().sell(CHARACTER, "potion.test", 1, false);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.amount()).isEqualTo(3L);
            assertThat(currency.bookings)
                    .as("aus Sicht des Spielers ist Verkaufen eine Gutschrift")
                    .containsExactly(BookingReason.VENDOR_SALE);
        }

        @Test
        @DisplayName("Klassenausruestung wird abgewiesen - und nichts gebucht")
        void boundEquipmentIsRefused() {
            VendorTransaction.Result result = vendor().sell(CHARACTER, "potion.test", 1, true);

            assertThat(result.outcome()).isEqualTo(VendorTransaction.Outcome.BOUND_EQUIPMENT);
            assertThat(currency.bookings).isEmpty();
        }

        @Test
        @DisplayName("ein unverkaeuflicher Gegenstand wird abgewiesen - LEER heisst nicht NULL")
        void anUnsellableItemIsRefused() {
            VendorTransaction.Result result = vendor().sell(CHARACTER, "trim.test", 1, false);

            assertThat(result.outcome()).isEqualTo(VendorTransaction.Outcome.NOT_SELLABLE);
            assertThat(currency.bookings).isEmpty();
        }

        @Test
        @DisplayName("eine unbekannte Vorlage wird abgewiesen")
        void anUnknownTemplateIsRefused() {
            assertThat(vendor().sell(CHARACTER, "potion.nope", 1, false).outcome())
                    .isEqualTo(VendorTransaction.Outcome.UNKNOWN_TEMPLATE);
            assertThat(currency.bookings).isEmpty();
        }
    }

    @Nested
    @DisplayName("Kaufen")
    class Buying {

        @Test
        @DisplayName("ein gefuehrter Gegenstand kostet Coins - gebucht als BELASTUNG")
        void astockedItemDebits() {
            VendorTransaction.Result result = vendor().buy(CHARACTER, ZONE, "potion.test", 1);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.amount()).isEqualTo(12L);
            assertThat(currency.bookings).containsExactly(BookingReason.VENDOR_PURCHASE);
        }

        @Test
        @DisplayName("was der Haendler nicht fuehrt, wird abgewiesen - und nichts gebucht")
        void whatIsNotInStockIsRefused() {
            VendorTransaction.Result result = vendor().buy(CHARACTER, "pale-wilds", "potion.test", 1);

            assertThat(result.outcome()).isEqualTo(VendorTransaction.Outcome.NOT_IN_STOCK);
            assertThat(currency.bookings).isEmpty();
        }

        @Test
        @DisplayName("zu wenig Coins - abgewiesen, und nichts gebucht")
        void notEnoughCoinsIsRefused() {
            VendorTransaction poor = vendor(new RecordingCurrency(5L));

            assertThat(poor.buy(CHARACTER, ZONE, "potion.test", 1).outcome())
                    .isEqualTo(VendorTransaction.Outcome.NOT_ENOUGH_COINS);
        }

        @Test
        @DisplayName("FR-064 - volles Inventar wird VOR der Buchung geprueft")
        void afullInventoryIsCheckedBeforeTheBooking() {
            // Der Fall, fuer den die vierte Pruefung ueberhaupt da ist. Ein bezahltes Item, das
            // nirgends hinpasst, ist der schlechteste moegliche Ausgang - und der einzige, den
            // ein Spieler nicht selbst wieder in Ordnung bringen kann.
            VendorTransaction full =
                    new VendorTransaction(() -> config(), currency, (character, template, amount) -> false);

            VendorTransaction.Result result = full.buy(CHARACTER, ZONE, "potion.test", 1);

            assertThat(result.outcome()).isEqualTo(VendorTransaction.Outcome.NO_ROOM);
            assertThat(currency.bookings)
                    .as("bezahlt und nichts bekommen - genau das darf nicht passieren")
                    .isEmpty();
        }

        @Test
        @DisplayName("mehrere Stueck kosten mehrfach")
        void severalPiecesCostSeveralTimes() {
            assertThat(vendor().buy(CHARACTER, ZONE, "potion.test", 3).amount()).isEqualTo(36L);
        }
    }

    @Test
    @DisplayName("KEINE Ablehnung bucht - ueber alle sechs Gruende")
    void noRefusalEverBooks() {
        VendorTransaction vendor = vendor();

        vendor.sell(CHARACTER, "potion.test", 1, true);
        vendor.sell(CHARACTER, "trim.test", 1, false);
        vendor.sell(CHARACTER, "potion.nope", 1, false);
        vendor.buy(CHARACTER, "pale-wilds", "potion.test", 1);
        vendor.buy(CHARACTER, ZONE, "potion.nope", 1);
        new VendorTransaction(() -> config(), currency, (c, t, a) -> false)
                .buy(CHARACTER, ZONE, "potion.test", 1);

        assertThat(currency.bookings)
                .as("sechs Ablehnungen, null Buchungen - das ist SC-009")
                .isEmpty();
    }

    // --- Hilfsmittel ---------------------------------------------------------------

    private VendorTransaction vendor() {
        return vendor(currency);
    }

    private VendorTransaction vendor(Currency withCurrency) {
        return new VendorTransaction(() -> config(), withCurrency, (c, t, a) -> true);
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
        templates.put(
                "trim.test",
                new ItemTemplate(
                        "trim.test",
                        ItemCategory.COSMETIC,
                        "NETHERITE_UPGRADE_SMITHING_TEMPLATE",
                        Rarity.LEGENDARY,
                        null,
                        null,
                        null, // unverkaeuflich
                        null,
                        null,
                        new CosmeticAppearance("REDSTONE", "RAISER")));

        return new ItemConfig(
                templates,
                WearCurve.defaults(),
                new RepairPricing(List.of(0L, 40L)),
                LootTables.empty(),
                Map.of(ZONE, new VendorStock(Map.of("potion.test", 12L))));
    }

    /** Schreibt mit, was gebucht wurde — und ob überhaupt. */
    private static final class RecordingCurrency implements Currency {

        private final List<BookingReason> bookings = new ArrayList<>();
        private final long balance;

        RecordingCurrency(long balance) {
            this.balance = balance;
        }

        @Override
        public OptionalLong balanceOf(UUID characterId) {
            return OptionalLong.of(balance);
        }

        @Override
        public long balanceOrZero(UUID characterId) {
            return balance;
        }

        @Override
        public boolean canAfford(UUID characterId, long amount) {
            return balance >= amount;
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
