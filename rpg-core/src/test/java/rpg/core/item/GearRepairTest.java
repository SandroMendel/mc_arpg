package rpg.core.item;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.classes.LadderSlot;
import rpg.core.combat.DamageOrigin;
import rpg.core.currency.BookingReason;
import rpg.core.currency.BookingResult;
import rpg.core.currency.Currency;
import rpg.core.event.DefaultEventBus;

/**
 * FR-052 bis FR-054 — <b>die bezahlte Instandsetzung, und sie ist die einzige.</b>
 *
 * <p>Geprüft wird dasselbe wie bei {@link VendorTransaction} und {@code EquipmentPurchase}: die
 * <em>Reihenfolge</em>. Jede Ablehnung lässt den Kontostand unberührt, und jeder Abzug hat einen
 * Gegenwert. Wer bezahlt hat und nichts bekam, kann sich nicht selbst helfen — das ist der
 * schlechteste Ausgang, den dieser Block erzeugen kann.
 */
class GearRepairTest {

    private static final UUID CHARACTER = UUID.randomUUID();

    private final RecordingCurrency currency = new RecordingCurrency(10_000L);

    private DefaultGearConditions conditions;
    private GearRepair repair;

    @BeforeEach
    void setUp() {
        conditions =
                new DefaultGearConditions(
                        WearCurve::defaults,
                        new NoRepository(),
                        new DefaultEventBus(java.util.logging.Logger.getLogger("quiet")),
                        Clock.systemUTC());
        conditions.put(GearCondition.full(CHARACTER));
        repair = new GearRepair(GearRepairTest::config, conditions, (id, slot) -> 3, currency);
    }

    @Test
    @DisplayName("nur der bezahlte Slot wird wiederhergestellt (SC-012)")
    void onlyThePaidSlotIsRestored() {
        wearBoth();

        GearRepair.Result result = repair.repair(CHARACTER, LadderSlot.ARMOR);

        assertThat(result.isSuccess()).isTrue();
        assertThat(conditions.conditionOf(CHARACTER, LadderSlot.ARMOR)).isEqualTo(WearCurve.FULL);
        assertThat(conditions.conditionOf(CHARACTER, LadderSlot.WEAPON))
                .as("wer nur die Waffe abgenutzt hat, zahlt nicht fuer die Ruestung mit")
                .isLessThan(WearCurve.FULL);
    }

    @Test
    @DisplayName("gebucht wird unter REPAIR - genau einmal")
    void thebookingReasonIsRepair() {
        wearBoth();

        repair.repair(CHARACTER, LadderSlot.WEAPON);

        assertThat(currency.bookings).containsExactly(BookingReason.REPAIR);
    }

    @Test
    @DisplayName("FR-053: der Preis steigt mit dem fehlenden Anteil")
    void thepriceGrowsWithWhatIsMissing() {
        conditions.put(GearCondition.full(CHARACTER).with(LadderSlot.ARMOR, 99.0));
        long nearlyFull = repair.priceOf(CHARACTER, LadderSlot.ARMOR);

        conditions.put(GearCondition.full(CHARACTER).with(LadderSlot.ARMOR, 5.0));
        long nearlyGone = repair.priceOf(CHARACTER, LadderSlot.ARMOR);

        assertThat(nearlyGone)
                .as("sonst waere es guenstiger, die Ausruestung erst herunterkommen zu lassen")
                .isGreaterThan(nearlyFull);
    }

    @Test
    @DisplayName("FR-054: eine Reparatur ohne Verschleiss wird abgewiesen - und NICHTS gebucht")
    void arepairWithoutWearIsRefusedAndBooksNothing() {
        GearRepair.Result result = repair.repair(CHARACTER, LadderSlot.ARMOR);

        assertThat(result.outcome()).isEqualTo(GearRepair.Outcome.NOT_WORN);
        assertThat(currency.bookings).isEmpty();
    }

    @Test
    @DisplayName("zu wenig Coins - abgewiesen, und der Zustand bleibt, wie er war")
    void notEnoughCoinsLeavesEverythingAsItWas() {
        wearBoth();
        double before = conditions.conditionOf(CHARACTER, LadderSlot.ARMOR);
        GearRepair poor =
                new GearRepair(GearRepairTest::config, conditions, (id, slot) -> 3, new RecordingCurrency(0L));

        GearRepair.Result result = poor.repair(CHARACTER, LadderSlot.ARMOR);

        assertThat(result.outcome()).isEqualTo(GearRepair.Outcome.NOT_ENOUGH_COINS);
        assertThat(conditions.conditionOf(CHARACTER, LadderSlot.ARMOR))
                .as("weder bezahlt noch repariert - genau so soll eine Ablehnung aussehen")
                .isEqualTo(before);
    }

    @Test
    @DisplayName("ein Charakter, dessen Zustand nicht geladen ist, wird abgewiesen")
    void anunloadedCharacterIsRefused() {
        GearRepair.Result result = repair.repair(UUID.randomUUID(), LadderSlot.ARMOR);

        assertThat(result.outcome()).isEqualTo(GearRepair.Outcome.UNKNOWN_CHARACTER);
        assertThat(currency.bookings).isEmpty();
    }

    @Test
    @DisplayName("und der Preis steigt mit der Stufe - die Senke bleibt bis Level 60 spuerbar")
    void thepriceGrowsWithTheTier() {
        conditions.put(GearCondition.full(CHARACTER).with(LadderSlot.ARMOR, 0.0));

        GearRepair lowTier =
                new GearRepair(GearRepairTest::config, conditions, (id, slot) -> 1, currency);
        GearRepair highTier =
                new GearRepair(GearRepairTest::config, conditions, (id, slot) -> 3, currency);

        assertThat(highTier.priceOf(CHARACTER, LadderSlot.ARMOR))
                .isGreaterThan(lowTier.priceOf(CHARACTER, LadderSlot.ARMOR));
    }

    // --- Hilfsmittel ---------------------------------------------------------------

    private void wearBoth() {
        conditions.onDamageTaken(CHARACTER, DamageOrigin.MELEE, false, 2_000.0);
        conditions.onDamageDealt(CHARACTER, DamageOrigin.MELEE, false, 2_000.0);
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
                new RepairPricing(List.of(100L, 400L, 900L)),
                LootTables.empty(),
                Map.of());
    }

    private static final class NoRepository implements GearConditionRepository {

        @Override
        public CompletableFuture<Optional<GearCondition>> find(UUID characterId) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        @Override
        public void markDirty(UUID characterId) {}
    }

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
