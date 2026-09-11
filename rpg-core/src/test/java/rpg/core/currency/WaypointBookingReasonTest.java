package rpg.core.currency;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T089 - was B09 in dieses Enum geschrieben hat, und warum es zwei Zeilen sein mussten (SC-022,
 * FR-050d, ADR-032).
 *
 * <p>Der Test liegt bei B08b, weil das Enum B08b gehoert. Wer die zwei Werte wieder entfernt, faellt
 * hier auf und nicht erst im Zonenblock.
 */
class WaypointBookingReasonTest {

    private static final UUID CHARACTER = UUID.randomUUID();

    @Test
    @DisplayName("die Fahrt nimmt, die Rueckbuchung gibt - und keine von beiden ist eine Amtshandlung")
    void theTwoReasonsPointInOppositeDirections() {
        assertThat(BookingReason.WAYPOINT_TRAVEL.direction())
                .isEqualTo(BookingReason.Direction.DEBIT);
        assertThat(BookingReason.WAYPOINT_REFUND.direction())
                .isEqualTo(BookingReason.Direction.CREDIT);

        // EITHER waere bequem gewesen und falsch: eine Fahrt, die gutschreibt, ist ein Fehler und
        // soll einer bleiben.
        assertThat(BookingReason.WAYPOINT_TRAVEL.allowsCredit()).isFalse();
        assertThat(BookingReason.WAYPOINT_REFUND.allowsDebit()).isFalse();

        // Kein Betreiber steht dahinter, also braucht der Eintrag auch keinen Namen - das ist der
        // Unterschied zu ADMIN_REMOVE, das ohne Verursacher gar nicht gebucht werden kann.
        assertThat(BookingReason.WAYPOINT_TRAVEL.isAdministrative()).isFalse();
        assertThat(BookingReason.WAYPOINT_REFUND.isAdministrative()).isFalse();
    }

    @Test
    @DisplayName("der Verlauf trennt eine Reise von einem Einkauf und von einer Reparatur (SC-022)")
    void theHistoryTellsTravelApartFromShopping() {
        CurrencyFixture.Harness harness = CurrencyFixture.loadedWith(CHARACTER, 500L);

        harness.currency.debit(CHARACTER, 30L, BookingReason.WAYPOINT_TRAVEL);
        harness.currency.debit(CHARACTER, 40L, BookingReason.VENDOR_PURCHASE);
        harness.currency.debit(CHARACTER, 50L, BookingReason.REPAIR);

        List<LedgerEntry> history = harness.ledger.forCharacter(CHARACTER);

        assertThat(history)
                .extracting(LedgerEntry::reason)
                .as("drei Abgaenge, drei verschiedene Gruende - genau das ist der Zweck des Enums")
                .containsExactly(
                        BookingReason.WAYPOINT_TRAVEL,
                        BookingReason.VENDOR_PURCHASE,
                        BookingReason.REPAIR);
    }

    @Test
    @DisplayName("eine Rueckbuchung ist im Verlauf keine Gutschrift (FR-050d)")
    void aRefundDoesNotLookLikeAGift() {
        CurrencyFixture.Harness harness = CurrencyFixture.loadedWith(CHARACTER, 500L);

        // Die Abfolge, die R1 erzwungen hat: erst nehmen, dann versetzen, und wenn das Versetzen
        // scheitert, in derselben Tickphase zurueckgeben.
        harness.currency.debit(CHARACTER, 30L, BookingReason.WAYPOINT_TRAVEL);
        harness.currency.credit(CHARACTER, 30L, BookingReason.WAYPOINT_REFUND);

        List<LedgerEntry> history = harness.ledger.forCharacter(CHARACTER);

        assertThat(history).hasSize(2);
        assertThat(history)
                .extracting(LedgerEntry::reason)
                .containsExactly(BookingReason.WAYPOINT_TRAVEL, BookingReason.WAYPOINT_REFUND);
        assertThat(history.get(1).reason())
                .as("waere das PILE_PICKED_UP, saehe die gescheiterte Reise aus wie Fundgeld")
                .isNotEqualTo(BookingReason.PILE_PICKED_UP);
        assertThat(harness.currency.balanceOf(CHARACTER))
                .as("das Paar hebt sich auf - das ist die ganze Zusage")
                .hasValue(500L);
    }
}
