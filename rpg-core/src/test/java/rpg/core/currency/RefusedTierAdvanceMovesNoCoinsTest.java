package rpg.core.currency;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.classes.ClassConfigFixture;
import rpg.core.classes.ClassProgress;
import rpg.core.classes.ClassProgressRepository;
import rpg.core.classes.LadderSlot;
import rpg.core.classes.TierAdvance;
import rpg.core.event.DefaultEventBus;
import rpg.core.session.CharacterClass;

/**
 * FR-062 — <b>ein abgelehnter Aufstieg bewegt keine Coins.</b>
 *
 * <p>{@link EquipmentPurchase} war bis B11 gebaut, aber von niemandem aufgerufen: die Route stand
 * da und hatte keinen Test. B11s Händler ist ihr erster Nutzer (FR-061), und damit wird diese
 * Zusage zum ersten Mal wirklich gebraucht — also wird sie hier auch zum ersten Mal geprüft.
 *
 * <p><b>Warum sie hier steht und nicht bei B11.</b> Es ist die Zusage <em>dieser</em> Klasse, nicht
 * die des Fensters, das sie aufruft. Läge der Test bei B11, würde er stillschweigend mitwandern,
 * wenn ein späterer Block einen zweiten Aufrufer bekommt — und die Klasse stünde wieder ungeprüft
 * da.
 */
class RefusedTierAdvanceMovesNoCoinsTest {

    private static final Logger QUIET =
            Logger.getLogger(RefusedTierAdvanceMovesNoCoinsTest.class.getName());

    private final Map<UUID, CharacterClass> classes = new HashMap<>();
    private final Map<UUID, Integer> levels = new HashMap<>();
    private final Map<UUID, ClassProgress> progress = new HashMap<>();
    private static final CharacterClass PRICED_CLASS = CharacterClass.WARRIOR;
    private static final long PRICE = 500L;

    private final RecordingCurrency currency = new RecordingCurrency(1_000_000L);

    private EquipmentPurchase purchase;

    @BeforeEach
    void setUp() {
        QUIET.setLevel(Level.OFF);
        purchase = purchaseWith(currency);
    }

    private EquipmentPurchase purchaseWith(Currency withCurrency) {
        TierAdvance tiers =
                new TierAdvance(
                        pricedConfig(),
                        id -> Optional.ofNullable(classes.get(id)),
                        id -> levels.getOrDefault(id, 1),
                        id -> Optional.ofNullable(progress.get(id)),
                        updated -> progress.put(updated.characterId(), updated),
                        new NoRepository(),
                        new DefaultEventBus(QUIET));
        return new EquipmentPurchase(
                tiers,
                withCurrency,
                id -> Optional.ofNullable(classes.get(id)),
                id -> Optional.ofNullable(progress.get(id)),
                QUIET);
    }

    /**
     * B07s Prüfstand-Konfiguration, aber mit einem <b>Preis</b> an der zweiten Rüstungsstufe.
     *
     * <p>Dort steht ein leerer Kostenblock, also ist jeder Aufstieg dort umsonst — und ein
     * kostenloser Aufstieg kann nicht zeigen, ob eine Ablehnung Coins bewegt. Der Preis wird
     * deshalb hier gesetzt und nicht im gemeinsamen Prüfstand: B07s Tests rechnen mit dem, was dort
     * steht.
     */
    @SuppressWarnings("unchecked")
    private static rpg.core.classes.ClassConfig pricedConfig() {
        try {
            Map<String, Object> classes = ClassConfigFixture.valid();
            Map<String, Object> warrior = (Map<String, Object>) classes.get(PRICED_CLASS.name());
            List<Object> armor = (List<Object>) warrior.get("armor-ladder");
            Map<String, Object> secondTier = (Map<String, Object>) armor.get(1);
            secondTier.put("cost", Map.of("coins", PRICE));
            return ClassConfigFixture.bind(classes);
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    @Test
    @DisplayName("zu niedriges Level - abgelehnt, und der Kontostand bleibt unberuehrt")
    void aLevelTooLowMovesNoCoins() {
        UUID character = character(1);

        EquipmentPurchase.Result result = purchase.buyNext(character, LadderSlot.ARMOR);

        assertThat(result.isSuccess()).isFalse();
        assertThat(currency.bookings)
                .as("FR-062: der Aufstieg fand nicht statt, also wurde auch nichts belastet")
                .isEmpty();
    }

    @Test
    @DisplayName("unbekannter Charakter - abgelehnt, und der Kontostand bleibt unberuehrt")
    void anUnknownCharacterMovesNoCoins() {
        EquipmentPurchase.Result result = purchase.buyNext(UUID.randomUUID(), LadderSlot.WEAPON);

        assertThat(result.isSuccess()).isFalse();
        assertThat(currency.bookings).isEmpty();
    }

    @Test
    @DisplayName("zu wenig Coins - abgelehnt, und zwar BEVOR B07 die Stufe vergibt")
    void notEnoughCoinsRefusesBeforeTheAdvance() {
        UUID character = character(60);
        EquipmentPurchase poor = purchaseWith(new RecordingCurrency(0L));

        EquipmentPurchase.Result result = poor.buyNext(character, LadderSlot.ARMOR);

        assertThat(result.outcome()).isEqualTo(EquipmentPurchase.Outcome.NOT_ENOUGH_COINS);
        assertThat(progress.get(character).tierOf(LadderSlot.ARMOR))
                .as("die Stufe darf nicht vergeben worden sein - sonst waere sie geschenkt")
                .isEqualTo(ClassProgress.INITIAL_TIER);
    }

    @Test
    @DisplayName("und ein durchgefuehrter Aufstieg bucht genau einmal")
    void asuccessfulAdvanceBooksOnce() {
        UUID character = character(60);

        EquipmentPurchase.Result result = purchase.buyNext(character, LadderSlot.ARMOR);

        assertThat(result.isSuccess()).isTrue();
        assertThat(currency.bookings).hasSize(1);
    }

    // --- Hilfsmittel ---------------------------------------------------------------

    private UUID character(int level) {
        UUID characterId = UUID.randomUUID();
        CharacterClass id = PRICED_CLASS;
        classes.put(characterId, id);
        levels.put(characterId, level);
        progress.put(characterId, ClassProgress.initial(characterId));
        return characterId;
    }

    /** Der Aufstieg wird nicht geschrieben — hier zählt nur, was gebucht wurde. */
    private static final class NoRepository implements ClassProgressRepository {

        @Override
        public CompletableFuture<Optional<ClassProgress>> find(UUID characterId) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        @Override
        public void markDirty(UUID characterId) {}
    }

    /** Schreibt mit, ob überhaupt gebucht wurde. */
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
