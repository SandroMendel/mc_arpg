package rpg.core.currency;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T038 - zwei Charaktere eines Spielers halten getrennte Staende (US1 Szenario 5, SC-003, ADR-011).
 *
 * <p>Der Kontostand haengt am <b>Charakter</b>, nicht am Konto - wie Level und Faehigkeitsrang auch.
 * Ein gemeinsamer Geldbeutel machte aus „wieviel habe ich" eine Frage mit drei Antworten.
 */
class SeparateBalancesTest {

    private final UUID warrior = UUID.randomUUID();
    private final UUID rogue = UUID.randomUUID();
    private final UUID mage = UUID.randomUUID();

    @Test
    @DisplayName("eine Buchung auf den ersten laesst den zweiten unveraendert")
    void bookingOneLeavesTheOtherAlone() {
        CurrencyFixture.Harness harness = CurrencyFixture.empty();
        harness.currency.onCharacterLoaded(warrior, Optional.empty());
        harness.currency.onCharacterLoaded(rogue, Optional.empty());

        harness.currency.credit(warrior, 500L, BookingReason.PILE_PICKED_UP);

        assertThat(harness.currency.balanceOf(warrior)).hasValue(500L);
        assertThat(harness.currency.balanceOf(rogue)).as("unberuehrt").hasValue(0L);
    }

    @Test
    @DisplayName("ein Charakter kann nicht aus dem Geldbeutel eines anderen zahlen")
    void oneCannotSpendAnothersCoins() {
        CurrencyFixture.Harness harness = CurrencyFixture.empty();
        harness.currency.onCharacterLoaded(
                warrior, Optional.of(new CharacterBalance(warrior, 1000L, 1, 1L)));
        harness.currency.onCharacterLoaded(rogue, Optional.empty());

        assertThat(harness.currency.debit(rogue, 100L, BookingReason.EQUIPMENT_TIER))
                .isEqualTo(BookingResult.NOT_ENOUGH);
        assertThat(harness.currency.canAfford(rogue, 100L)).isFalse();
        assertThat(harness.currency.balanceOf(warrior))
                .as("und beim Reichen ist nichts abgegangen")
                .hasValue(1000L);
    }

    @Test
    @DisplayName("drei Charaktere, drei Staende - es wird nie zusammengezaehlt")
    void threeCharactersThreeBalances() {
        CurrencyFixture.Harness harness = CurrencyFixture.empty();
        harness.currency.onCharacterLoaded(
                warrior, Optional.of(new CharacterBalance(warrior, 100L, 1, 1L)));
        harness.currency.onCharacterLoaded(
                rogue, Optional.of(new CharacterBalance(rogue, 200L, 1, 1L)));
        harness.currency.onCharacterLoaded(
                mage, Optional.of(new CharacterBalance(mage, 300L, 1, 1L)));

        assertThat(harness.currency.balanceOf(warrior)).hasValue(100L);
        assertThat(harness.currency.balanceOf(rogue)).hasValue(200L);
        assertThat(harness.currency.balanceOf(mage)).hasValue(300L);
        assertThat(harness.currency.snapshot())
                .as("eine Summe ueber drei Staende waere eine Zahl, die es im Spiel nicht gibt")
                .containsOnlyKeys(warrior, rogue, mage);
    }

    @Test
    @DisplayName("das Ende einer Sitzung gibt nur den gewechselten Charakter frei")
    void closingReleasesOnlyOne() {
        CurrencyFixture.Harness harness = CurrencyFixture.empty();
        harness.currency.onCharacterLoaded(
                warrior, Optional.of(new CharacterBalance(warrior, 100L, 1, 1L)));
        harness.currency.onCharacterLoaded(
                rogue, Optional.of(new CharacterBalance(rogue, 200L, 1, 1L)));

        harness.currency.onSessionClosing(warrior);

        assertThat(harness.currency.balanceOf(warrior)).isEmpty();
        assertThat(harness.currency.balanceOf(rogue)).hasValue(200L);
    }

    @Test
    @DisplayName("jeder Verlaufseintrag nennt seinen Charakter")
    void everyEntryNamesItsCharacter() {
        CurrencyFixture.Harness harness = CurrencyFixture.empty();
        harness.currency.onCharacterLoaded(warrior, Optional.empty());
        harness.currency.onCharacterLoaded(rogue, Optional.empty());

        harness.currency.credit(warrior, 10L, BookingReason.PILE_PICKED_UP);
        harness.currency.credit(rogue, 20L, BookingReason.PILE_PICKED_UP);

        assertThat(harness.ledger.forCharacter(warrior)).hasSize(1);
        assertThat(harness.ledger.forCharacter(rogue)).hasSize(1);
        assertThat(harness.ledger.forCharacter(warrior).get(0).amount()).isEqualTo(10L);
    }
}
