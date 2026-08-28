package rpg.core.currency;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T017 - Buchen, und dass jede Buchung ihren Grund traegt (US1 Szenarien 1 und 6, FR-005).
 */
class DefaultCurrencyTest {

    private final UUID character = UUID.randomUUID();

    @Test
    @DisplayName("eine Gutschrift erhoeht den Stand und traegt ihren Grund")
    void creditRaisesTheBalance() {
        CurrencyFixture.Harness harness = CurrencyFixture.loadedWith(character, 0L);

        assertThat(harness.currency.credit(character, 500L, BookingReason.PILE_PICKED_UP))
                .isEqualTo(BookingResult.OK);

        assertThat(harness.currency.balanceOf(character)).hasValue(500L);
        assertThat(harness.ledger.forCharacter(character))
                .singleElement()
                .satisfies(
                        entry -> {
                            assertThat(entry.reason()).isEqualTo(BookingReason.PILE_PICKED_UP);
                            assertThat(entry.direction()).isEqualTo(LedgerEntry.Direction.CREDIT);
                            assertThat(entry.amount()).isEqualTo(500L);
                            assertThat(entry.balanceBefore()).isZero();
                            assertThat(entry.balanceAfter()).isEqualTo(500L);
                        });
    }

    @Test
    @DisplayName("eine Abbuchung senkt den Stand und traegt ihren Grund")
    void debitLowersTheBalance() {
        CurrencyFixture.Harness harness = CurrencyFixture.loadedWith(character, 500L);

        assertThat(harness.currency.debit(character, 200L, BookingReason.EQUIPMENT_TIER))
                .isEqualTo(BookingResult.OK);

        assertThat(harness.currency.balanceOf(character)).hasValue(300L);
        assertThat(harness.ledger.forCharacter(character))
                .singleElement()
                .satisfies(
                        entry -> {
                            assertThat(entry.reason()).isEqualTo(BookingReason.EQUIPMENT_TIER);
                            assertThat(entry.direction()).isEqualTo(LedgerEntry.Direction.DEBIT);
                            assertThat(entry.balanceBefore()).isEqualTo(500L);
                            assertThat(entry.balanceAfter()).isEqualTo(300L);
                        });
    }

    @Test
    @DisplayName("es gibt keinen Weg, ohne Grund zu buchen - der Compiler laesst ihn nicht zu")
    void everyBookingCarriesAReason() {
        CurrencyFixture.Harness harness = CurrencyFixture.loadedWith(character, 100L);

        harness.currency.credit(character, 10L, BookingReason.VENDOR_SALE);
        harness.currency.debit(character, 5L, BookingReason.REPAIR);

        assertThat(harness.ledger.forCharacter(character))
                .as("jeder Eintrag nennt seinen Grund (FR-005)")
                .allSatisfy(entry -> assertThat(entry.reason()).isNotNull());
    }

    @Test
    @DisplayName("eine gelungene Buchung merkt den Charakter zum Schreiben vor")
    void successfulBookingMarksTheCharacter() {
        CurrencyFixture.Harness harness = CurrencyFixture.loadedWith(character, 100L);

        harness.currency.credit(character, 10L, BookingReason.PILE_PICKED_UP);

        assertThat(harness.repository.marked(character)).isTrue();
    }

    @Test
    @DisplayName("eine abgelehnte Buchung merkt nichts vor - es hat sich nichts geaendert")
    void refusedBookingMarksNothing() {
        CurrencyFixture.Harness harness = CurrencyFixture.loadedWith(character, 10L);

        assertThat(harness.currency.debit(character, 500L, BookingReason.ABILITY_RANK))
                .isEqualTo(BookingResult.NOT_ENOUGH);

        assertThat(harness.repository.dirtied).isEmpty();
        assertThat(harness.ledger.entries).isEmpty();
    }

    @Test
    @DisplayName("ein nicht geladener Charakter ergibt NO_SUCH_CHARACTER, nicht null")
    void unloadedCharacterIsNotZero() {
        CurrencyFixture.Harness harness = CurrencyFixture.empty();

        assertThat(harness.currency.balanceOf(character))
                .as("leer heisst 'nicht geladen', nie 'null'")
                .isEmpty();
        assertThat(harness.currency.credit(character, 10L, BookingReason.PILE_PICKED_UP))
                .isEqualTo(BookingResult.NO_SUCH_CHARACTER);
        assertThat(harness.currency.canAfford(character, 1L))
                .as("nicht geladen ist nicht zahlungsfaehig")
                .isFalse();
    }

    @Test
    @DisplayName("ein geladener Charakter ohne gespeicherte Zeile haelt null, kein Fehler")
    void loadedWithoutStoredRowHoldsZero() {
        CurrencyFixture.Harness harness = CurrencyFixture.empty();

        harness.currency.onCharacterLoaded(character, Optional.empty());

        assertThat(harness.currency.balanceOf(character)).hasValue(0L);
        assertThat(harness.currency.balanceOrZero(character)).isZero();
    }

    @Test
    @DisplayName("canAfford ist eine Frage, keine Reservierung - zweimal fragen aendert nichts")
    void canAffordReservesNothing() {
        CurrencyFixture.Harness harness = CurrencyFixture.loadedWith(character, 500L);

        assertThat(harness.currency.canAfford(character, 500L)).isTrue();
        assertThat(harness.currency.canAfford(character, 500L)).isTrue();
        assertThat(harness.currency.balanceOf(character)).hasValue(500L);
        assertThat(harness.ledger.entries).isEmpty();
    }
}
