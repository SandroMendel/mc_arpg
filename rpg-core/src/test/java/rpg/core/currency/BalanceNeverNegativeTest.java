package rpg.core.currency;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T018 - die Zusage, an der der ganze Block haengt (US1 Szenario 2, FR-003, FR-004).
 *
 * <p>Abgelehnt, <b>nicht gekappt</b>. Eine stille Kappung waere ein Geschenk, das niemand bemerkt:
 * der Spieler glaubte, bezahlt zu haben, und haette das Geld noch.
 */
class BalanceNeverNegativeTest {

    private final UUID character = UUID.randomUUID();

    @Test
    @DisplayName("zu wenig Guthaben wird abgelehnt, der Stand bleibt unveraendert")
    void tooLittleIsRefused() {
        CurrencyFixture.Harness harness = CurrencyFixture.loadedWith(character, 100L);

        assertThat(harness.currency.debit(character, 500L, BookingReason.EQUIPMENT_TIER))
                .isEqualTo(BookingResult.NOT_ENOUGH);

        assertThat(harness.currency.balanceOf(character))
                .as("unveraendert - und ausdruecklich NICHT auf null gekappt")
                .hasValue(100L);
    }

    @Test
    @DisplayName("genau der Stand ist zahlbar, einer mehr nicht")
    void exactBalanceIsPayable() {
        CurrencyFixture.Harness harness = CurrencyFixture.loadedWith(character, 100L);

        assertThat(harness.currency.debit(character, 101L, BookingReason.ABILITY_RANK))
                .isEqualTo(BookingResult.NOT_ENOUGH);
        assertThat(harness.currency.debit(character, 100L, BookingReason.ABILITY_RANK))
                .isEqualTo(BookingResult.OK);
        assertThat(harness.currency.balanceOf(character)).hasValue(0L);
    }

    @Test
    @DisplayName("aus null laesst sich nichts abbuchen")
    void nothingComesOutOfZero() {
        CurrencyFixture.Harness harness = CurrencyFixture.loadedWith(character, 0L);

        assertThat(harness.currency.debit(character, 1L, BookingReason.REPAIR))
                .isEqualTo(BookingResult.NOT_ENOUGH);
        assertThat(harness.currency.balanceOf(character)).hasValue(0L);
    }

    @Test
    @DisplayName("die Ablehnung nennt ihren Grund, damit der Spieler ihn erfaehrt")
    void refusalCarriesItsMessage() {
        CurrencyFixture.Harness harness = CurrencyFixture.loadedWith(character, 10L);

        BookingResult result = harness.currency.debit(character, 50L, BookingReason.EQUIPMENT_TIER);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.messageKey()).isEqualTo(CurrencyMessageKeys.NOT_ENOUGH);
    }

    @Test
    @DisplayName("der Aggregattyp selbst laesst keinen negativen Stand zu")
    void theRecordRefusesItToo() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> new CharacterBalance(character, -1L, 1, 0L))
                .as("die Zusage steht im Speicher, im Record und als CHECK in der Tabelle")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negative");
    }
}
