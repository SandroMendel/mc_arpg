package rpg.persistence.statistics;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.persistence.FlushReason;
import rpg.core.persistence.PlayerState;
import rpg.persistence.support.PersistenceHarness;
import rpg.persistence.support.PostgresContainer;

/**
 * SC-004 — <b>die Werte sind nach einem Neustart dieselben.</b>
 *
 * <p>Der Neustart wird nachgestellt, indem der ganze Bestand geschlossen und ein neuer gegen
 * dieselbe Datenbank aufgebaut wird — genau das, was ein Serverneustart tut. Ein Test, der nur
 * denselben Bestand zweimal fragt, prüfte den Speicher-Cache und nicht die Datenbank.
 *
 * <p><b>Was dabei mitgeprüft wird, ohne dass es im Namen steht:</b> dass die Werte den Weg durch
 * den Fluss überhaupt gegangen sind. Ein Zähler, der nur im Speicher steht, sieht bis zum Neustart
 * völlig richtig aus.
 */
class StatisticsSurviveRestartTest {

    private static final String SUM_METRIC = "mob_kills";
    private static final String MAX_METRIC = "damage_max";

    private PersistenceHarness harness;
    private UUID playerId;

    @BeforeEach
    void setUp() {
        PostgresContainer.resetSchema();
        harness = new PersistenceHarness();
        playerId = UUID.randomUUID();
        harness.playerStates.put(PlayerState.initial(playerId, Instant.now()));
    }

    @AfterEach
    void tearDown() {
        if (harness != null) {
            harness.close();
        }
    }

    @Test
    @DisplayName("SC-004 - Summen und Maxima stehen nach dem Neustart unveraendert da")
    void valuesAreIdenticalBeforeAndAfterARestart() throws Exception {
        harness.statistics.increment(playerId, SUM_METRIC, 7);
        harness.statistics.reportMax(playerId, MAX_METRIC, 1249);
        harness.flushCycle.flushNow(FlushReason.INTERVAL).get();

        long killsBefore = harness.statistics.total(playerId, SUM_METRIC).get();
        long damageBefore = harness.statistics.total(playerId, MAX_METRIC).get();

        restart();

        assertThat(harness.statistics.total(playerId, SUM_METRIC).get()).isEqualTo(killsBefore).isEqualTo(7L);
        assertThat(harness.statistics.total(playerId, MAX_METRIC).get())
                .isEqualTo(damageBefore)
                .isEqualTo(1249L);
    }

    @Test
    @DisplayName("nach dem Neustart wird weitergezaehlt, nicht neu angefangen")
    void countingContinuesAfterTheRestart() throws Exception {
        harness.statistics.increment(playerId, SUM_METRIC, 7);
        harness.flushCycle.flushNow(FlushReason.INTERVAL).get();

        restart();

        harness.statistics.increment(playerId, SUM_METRIC, 5);
        harness.flushCycle.flushNow(FlushReason.INTERVAL).get();

        assertThat(harness.statistics.total(playerId, SUM_METRIC).get()).isEqualTo(12L);
    }

    @Test
    @DisplayName("ein niedrigeres Maximum nach dem Neustart drueckt den gespeicherten nicht")
    void alowerMaximumAfterTheRestartDoesNotPushTheStoredOneDown() throws Exception {
        harness.statistics.reportMax(playerId, MAX_METRIC, 1249);
        harness.flushCycle.flushNow(FlushReason.INTERVAL).get();

        restart();

        // Der frische Bestand weiss NICHTS von dem gespeicherten Wert - und darf ihn trotzdem
        // nicht ueberschreiben. Genau das leistet GREATEST in der Datenbank statt eines
        // Vergleichs im Speicher.
        harness.statistics.reportMax(playerId, MAX_METRIC, 400);
        harness.flushCycle.flushNow(FlushReason.INTERVAL).get();

        assertThat(harness.statistics.total(playerId, MAX_METRIC).get()).isEqualTo(1249L);
    }

    /** Schließt den ganzen Bestand und baut einen neuen gegen dieselbe Datenbank auf. */
    private void restart() {
        harness.close();
        harness = new PersistenceHarness();
    }
}
