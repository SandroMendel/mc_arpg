package rpg.core.currency;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T021a - eine spaetere Aenderung des Startguthabens wirkt nicht rueckwirkend (FR-011b, Szenario 7b).
 *
 * <p>Der Fehler, den dieser Test ausschliesst, ist unsichtbar: waere das Startguthaben ein Wert, der
 * beim <em>Lesen</em> gilt, haette eine Erhoehung von 0 auf 500 jeden noch unbebuchten Charakter ueber
 * Nacht reicher gemacht - ohne Buchung, ohne Verlaufseintrag, ohne dass es jemandem auffaellt. Genau
 * die Fehlbuchung, die der Verlauf auffindbar machen soll.
 */
class NoRetroactiveStartingBalanceTest {

    private final UUID character = UUID.randomUUID();

    @Test
    @DisplayName("ein Charakter ohne gespeicherte Zeile meldet null, nicht den konfigurierten Wert")
    void noStoredRowMeansZeroNotTheConfiguredValue() {
        CurrencyFixture.Harness harness = CurrencyFixture.startingWith(500L);

        harness.currency.onCharacterLoaded(character, Optional.empty());

        assertThat(harness.currency.balanceOf(character))
                .as("nicht 500 - das Startguthaben wird gebucht, nicht gelesen")
                .hasValue(0L);
    }

    @Test
    @DisplayName("ein bestehender Stand bleibt unveraendert, wenn das Startguthaben spaeter steigt")
    void raisingTheConfiguredValueLeavesExistingBalancesAlone() {
        UUID veteran = UUID.randomUUID();

        // Gestern: Startguthaben null, der Charakter entsteht und spielt, ohne je eine Coin
        // anzufassen. Es gibt keine Kontozeile.
        CurrencyFixture.Harness yesterday = CurrencyFixture.startingWith(0L);
        yesterday.currency.onCharacterCreated(veteran);
        assertThat(yesterday.currency.balanceOf(veteran)).hasValue(0L);
        assertThat(yesterday.ledger.entries).isEmpty();

        // Heute: der Betreiber setzt das Startguthaben auf 500 und startet neu. Derselbe Charakter
        // meldet sich an - mit derselben leeren Kontozeile.
        CurrencyFixture.Harness today = CurrencyFixture.startingWith(500L);
        today.currency.onCharacterLoaded(veteran, Optional.empty());

        assertThat(today.currency.balanceOf(veteran))
                .as("nicht ueber Nacht um 500 reicher geworden")
                .hasValue(0L);
        assertThat(today.ledger.entries)
                .as("und erst recht nicht, ohne dass es irgendwo steht")
                .isEmpty();
    }

    @Test
    @DisplayName("der neue Wert gilt nur fuer neu erstellte Charaktere")
    void theNewValueAppliesToNewCharactersOnly() {
        UUID newcomer = UUID.randomUUID();
        CurrencyFixture.Harness harness = CurrencyFixture.startingWith(500L);

        harness.currency.onCharacterLoaded(character, Optional.empty());
        harness.currency.onCharacterCreated(newcomer);

        assertThat(harness.currency.balanceOf(character)).as("bestehend").hasValue(0L);
        assertThat(harness.currency.balanceOf(newcomer)).as("neu erstellt").hasValue(500L);
    }

    @Test
    @DisplayName("CharacterBalance.empty ist null - die Stelle, an der die Versuchung sitzt")
    void theEmptyAggregateIsZero() {
        assertThat(CharacterBalance.empty(character).balance())
                .as("hier den konfigurierten Wert einzusetzen waere der ganze Fehler")
                .isZero();
    }
}
