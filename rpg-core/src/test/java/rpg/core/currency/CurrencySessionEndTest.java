package rpg.core.currency;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T037 - beiseitelegen, markieren, freigeben (FR-016, ADR-015 Punkt 7).
 *
 * <p>Der Fehler, den diese Reihenfolge verhindert, kostet den letzten Fortschritt <b>jeder</b>
 * Sitzung und ist von keinem Test der Regelschicht zu sehen: er entsteht erst an der Naht zwischen
 * Freigabe und Flush. Der Flush laeuft asynchron und damit normalerweise <em>nach</em> der Freigabe -
 * findet er dort nichts Lebendiges mehr, verwirft er die Markierung, und die letzten Buchungen sind
 * weg. B06 hatte die Ablage zunaechst nicht; das war einer der beiden Fehler, wegen derer ADR-015
 * geschrieben wurde.
 */
class CurrencySessionEndTest {

    private final UUID character = UUID.randomUUID();

    @Test
    @DisplayName("waehrend der Sitzung liest der Flush den lebenden Wert")
    void liveWhileTheSessionIsOpen() {
        CurrencyFixture.Harness harness = CurrencyFixture.loadedWith(character, 100L);

        harness.currency.credit(character, 50L, BookingReason.PILE_PICKED_UP);

        assertThat(harness.currency.liveOrLastKnown(character)).hasValue(150L);
    }

    @Test
    @DisplayName("eine Buchung unmittelbar vor der Freigabe wird noch geschrieben")
    void theLastBookingOfASessionSurvives() {
        CurrencyFixture.Harness harness = CurrencyFixture.loadedWith(character, 100L);

        harness.currency.credit(character, 900L, BookingReason.PILE_PICKED_UP);
        harness.currency.onSessionClosing(character);

        assertThat(harness.currency.balanceOf(character))
                .as("freigegeben - nichts Lebendiges mehr")
                .isEmpty();
        assertThat(harness.currency.liveOrLastKnown(character))
                .as("aber die Ablage antwortet dem Flush, der gleich kommt")
                .hasValue(1000L);
    }

    @Test
    @DisplayName("die Freigabe markiert erneut, damit der Verlust auf ein Autosave begrenzt bleibt")
    void closingMarksAgain() {
        CurrencyFixture.Harness harness = CurrencyFixture.loadedWith(character, 100L);
        harness.repository.dirtied.clear();

        harness.currency.onSessionClosing(character);

        assertThat(harness.repository.marked(character)).isTrue();
    }

    @Test
    @DisplayName("die Ablage wird beim Lesen geleert - sie gilt fuer genau einen letzten Schreibvorgang")
    void theStashIsConsumedOnRead() {
        CurrencyFixture.Harness harness = CurrencyFixture.loadedWith(character, 400L);
        harness.currency.onSessionClosing(character);

        assertThat(harness.currency.liveOrLastKnown(character)).hasValue(400L);
        assertThat(harness.currency.liveOrLastKnown(character))
                .as("sonst bliebe je Spieler, der je verbunden war, ein Eintrag liegen")
                .isEmpty();
    }

    @Test
    @DisplayName("ein Wiedereinstieg liest nicht die alte Ablage")
    void reloggingDoesNotSeeAStaleValue() {
        CurrencyFixture.Harness harness = CurrencyFixture.loadedWith(character, 400L);
        harness.currency.onSessionClosing(character);
        harness.currency.liveOrLastKnown(character); // der Flush war da

        harness.currency.onCharacterLoaded(
                character, java.util.Optional.of(new CharacterBalance(character, 400L, 1, 2L)));

        assertThat(harness.currency.balanceOf(character)).hasValue(400L);
    }

    @Test
    @DisplayName("die Freigabe eines nie geladenen Charakters tut nichts")
    void closingSomethingNeverLoadedIsHarmless() {
        CurrencyFixture.Harness harness = CurrencyFixture.empty();

        harness.currency.onSessionClosing(character);

        assertThat(harness.currency.liveOrLastKnown(character)).isEmpty();
        assertThat(harness.repository.dirtied).isEmpty();
    }

    @Test
    @DisplayName("nach der Freigabe ist der Charakter nicht mehr geladen - kein Leck")
    void nothingLeaks() {
        CurrencyFixture.Harness harness = CurrencyFixture.loadedWith(character, 100L);
        assertThat(harness.currency.loadedCount()).isEqualTo(1);

        harness.currency.onSessionClosing(character);

        assertThat(harness.currency.loadedCount()).isZero();
    }
}
