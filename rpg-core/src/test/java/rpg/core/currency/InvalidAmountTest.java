package rpg.core.currency;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T020 - ungueltige Betraege und der Rand des Zahlenbereichs (FR-009, FR-010).
 */
class InvalidAmountTest {

    private final UUID character = UUID.randomUUID();

    @Test
    @DisplayName("ein Betrag von null ist ein Aufruffehler, keine Buchung")
    void zeroIsACallerError() {
        CurrencyFixture.Harness harness = CurrencyFixture.loadedWith(character, 100L);

        assertThat(harness.currency.credit(character, 0L, BookingReason.PILE_PICKED_UP))
                .isEqualTo(BookingResult.INVALID_AMOUNT);
        assertThat(harness.currency.debit(character, 0L, BookingReason.REPAIR))
                .isEqualTo(BookingResult.INVALID_AMOUNT);
        assertThat(harness.currency.balanceOf(character)).hasValue(100L);
        assertThat(harness.ledger.entries).isEmpty();
    }

    @Test
    @DisplayName("ein negativer Betrag wird zurueckgewiesen - die Richtung steht in der Methode")
    void negativeIsRejected() {
        CurrencyFixture.Harness harness = CurrencyFixture.loadedWith(character, 100L);

        assertThat(harness.currency.credit(character, -50L, BookingReason.PILE_PICKED_UP))
                .as("eine negative Gutschrift waere eine zweite Schreibweise fuer eine Abbuchung")
                .isEqualTo(BookingResult.INVALID_AMOUNT);
        assertThat(harness.currency.debit(character, -50L, BookingReason.REPAIR))
                .isEqualTo(BookingResult.INVALID_AMOUNT);
        assertThat(harness.currency.balanceOf(character)).hasValue(100L);
    }

    @Test
    @DisplayName("eine ueberlaufende Gutschrift wird abgelehnt statt umzulaufen")
    void overflowIsRefusedNotWrapped() {
        CurrencyFixture.Harness harness = CurrencyFixture.loadedWith(character, Long.MAX_VALUE - 5L);

        assertThat(harness.currency.credit(character, 10L, BookingReason.VENDOR_SALE))
                .as("umgelaufen waere der Stand negativ - genau die Zusage, die nie brechen darf")
                .isEqualTo(BookingResult.WOULD_OVERFLOW);
        assertThat(harness.currency.balanceOf(character)).hasValue(Long.MAX_VALUE - 5L);
        assertThat(harness.ledger.entries).isEmpty();
    }

    @Test
    @DisplayName("genau bis an den Rand geht noch")
    void exactlyToTheEdgeIsFine() {
        CurrencyFixture.Harness harness = CurrencyFixture.loadedWith(character, Long.MAX_VALUE - 5L);

        assertThat(harness.currency.credit(character, 5L, BookingReason.VENDOR_SALE))
                .isEqualTo(BookingResult.OK);
        assertThat(harness.currency.balanceOf(character)).hasValue(Long.MAX_VALUE);
    }

    @Test
    @DisplayName("ein ungueltiger Betrag laesst den Aufrufer erkennen, was falsch war")
    void invalidAmountCarriesItsMessage() {
        CurrencyFixture.Harness harness = CurrencyFixture.loadedWith(character, 100L);

        assertThat(harness.currency.credit(character, 0L, BookingReason.PILE_PICKED_UP).messageKey())
                .isEqualTo(CurrencyMessageKeys.INVALID_AMOUNT);
    }

    @Test
    @DisplayName("ein Grund, der nur gutschreiben darf, kann nicht abbuchen")
    void reasonDirectionIsEnforced() {
        CurrencyFixture.Harness harness = CurrencyFixture.loadedWith(character, 100L);

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () ->
                                harness.currency.debit(
                                        character, 10L, BookingReason.PILE_PICKED_UP))
                .as("ein Aufheben, das abbucht, waere ein Grund, der nicht zur Buchung passt")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot debit");
    }
}
