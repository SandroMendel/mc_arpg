package rpg.persistence.statistics;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.persistence.FlushReason;
import rpg.core.persistence.PlayerState;
import rpg.core.statistics.Aggregation;
import rpg.core.statistics.ScoreWeights;
import rpg.core.statistics.SeasonStanding;
import rpg.persistence.support.PersistenceHarness;
import rpg.persistence.support.PostgresContainer;

/**
 * SC-021 — <b>Gewichte ändern, Endstand bleibt.</b>
 *
 * <p>Das ist der ganze Grund, aus dem die Gewichtung mit in die Zeile geht. Läge sie nur in
 * {@code statistics.yml}, sortierte die nächste Balancing-Änderung eine abgeschlossene Saison
 * rückwirkend um — <b>und niemand bekäme es mit</b>: eine Rangliste, die man schon gesehen hat,
 * ruft man nicht erneut auf. Wer den ersten Platz hatte, hätte ihn irgendwann nicht mehr, ohne
 * dass etwas passiert wäre.
 *
 * <p>Der Test macht genau das: er friert einen Stand ein, ändert danach die Gewichtung so, dass
 * eine <em>Neuberechnung</em> die Reihenfolge umdrehen würde — und prüft, dass der gespeicherte
 * Stand unverändert dasteht.
 */
class FrozenStandingIgnoresLaterWeightsTest {

    private static final String SEASON = "2026-q2";
    private static final Instant FROZEN_AT = Instant.parse("2026-07-01T00:00:00Z");

    private PersistenceHarness harness;
    private UUID grinder;
    private UUID bossHunter;

    @BeforeEach
    void setUp() throws Exception {
        PostgresContainer.resetSchema();
        harness = new PersistenceHarness();
        grinder = UUID.randomUUID();
        bossHunter = UUID.randomUUID();
        harness.playerStates.put(PlayerState.initial(grinder, Instant.now()));
        harness.playerStates.put(PlayerState.initial(bossHunter, Instant.now()));
        harness.flushCycle.flushNow(FlushReason.INTERVAL).get();
    }

    @AfterEach
    void tearDown() {
        if (harness != null) {
            harness.close();
        }
    }

    @Test
    @DisplayName("SC-021 - der eingefrorene Stand bleibt, auch wenn die Gewichte sich aendern")
    void thefrozenStandingSurvivesAWeightChange() {
        JdbcSeasonResultRepository results = results();

        // Gerechnet mit "Bosskills sind 50 wert": der Bossjaeger gewinnt.
        Map<UUID, Long> scores = new LinkedHashMap<>();
        scores.put(bossHunter, 500L);
        scores.put(grinder, 340L);

        results.freeze(SeasonStanding.rank(SEASON, scores, FROZEN_AT), originalWeights());

        // Danach entscheidet der Betreiber, dass Bosskills nur noch 1 wert sind. Eine
        // NEUBERECHNUNG kehrte die Reihenfolge um - der Grinder haette dann mehr Punkte.
        // Der eingefrorene Stand darf davon nichts wissen.
        assertThat(results.standingsOf(SEASON))
                .extracting(SeasonStanding::playerId)
                .containsExactly(bossHunter, grinder);
        assertThat(results.standingsOf(SEASON).get(0).score()).isEqualTo(500L);
    }

    @Test
    @DisplayName("SC-021 - und die Gewichtung von damals steht bei der Zeile")
    void andTheWeightingOfBackThenIsStoredWithTheRow() {
        results().freeze(
                        SeasonStanding.rank(SEASON, Map.of(bossHunter, 500L), FROZEN_AT),
                        originalWeights());

        ScoreWeights stored = results().weightsOf(SEASON);

        // Nachrechenbar, auch Jahre spaeter und auch dann, wenn statistics.yml inzwischen etwas
        // ganz anderes sagt.
        assertThat(stored.weightOf(Aggregation.BOSS_KILLS)).isEqualTo(50.0);
        assertThat(stored.weightOf(Aggregation.MOB_KILLS)).isEqualTo(1.0);
    }

    @Test
    @DisplayName("FR-058 - ein zweiter Abschluss ueberschreibt nichts")
    void asecondClosingOverwritesNothing() {
        JdbcSeasonResultRepository results = results();
        results.freeze(SeasonStanding.rank(SEASON, Map.of(bossHunter, 500L), FROZEN_AT), originalWeights());

        // Der Abschluss wird versehentlich ein zweites Mal ausgeloest - mit anderen Zahlen.
        results.freeze(
                SeasonStanding.rank(SEASON, Map.of(bossHunter, 999L), Instant.now()),
                changedWeights());

        assertThat(results.standingsOf(SEASON).get(0).score())
                .as("was einmal eingefroren ist, bleibt es")
                .isEqualTo(500L);
        assertThat(results.weightsOf(SEASON).weightOf(Aggregation.BOSS_KILLS)).isEqualTo(50.0);
    }

    @Test
    @DisplayName("FR-058 - das Vorhandensein von Zeilen ist der Beleg fuer den Abschluss")
    void thepresenceOfRowsIsTheProofOfClosing() {
        JdbcSeasonResultRepository results = results();

        assertThat(results.isClosed(SEASON)).isFalse();

        results.freeze(SeasonStanding.rank(SEASON, Map.of(bossHunter, 500L), FROZEN_AT), originalWeights());

        // Eine eigene "abgeschlossen"-Spalte waere eine zweite Wahrheit ueber dieselbe Tatsache -
        // und die beiden liefen beim ersten Abbruch mitten im Abschluss auseinander.
        assertThat(results.isClosed(SEASON)).isTrue();
    }

    @Test
    @DisplayName("eine Saison ohne Teilnehmer schreibt nichts und gilt als nicht abgeschlossen")
    void aseasonWithoutParticipantsWritesNothing() {
        JdbcSeasonResultRepository results = results();

        results.freeze(List.of(), originalWeights());

        // Der Fall gehoert in Phase 7 noch einmal betrachtet: "nichts geschrieben" heisst hier
        // auch "nicht abgeschlossen", und der Abschluss wuerde beim naechsten Start erneut
        // versucht. Fuer eine leere Saison ist das harmlos - er schreibt wieder nichts.
        assertThat(results.isClosed(SEASON)).isFalse();
        assertThat(results.standingsOf(SEASON)).isEmpty();
    }

    private JdbcSeasonResultRepository results() {
        return new JdbcSeasonResultRepository(harness.pools.loginPool());
    }

    private static ScoreWeights originalWeights() {
        Map<Aggregation, Double> byBoard = new LinkedHashMap<>();
        byBoard.put(Aggregation.MOB_KILLS, 1.0);
        byBoard.put(Aggregation.BOSS_KILLS, 50.0);
        return new ScoreWeights(byBoard);
    }

    private static ScoreWeights changedWeights() {
        Map<Aggregation, Double> byBoard = new LinkedHashMap<>();
        byBoard.put(Aggregation.MOB_KILLS, 1.0);
        byBoard.put(Aggregation.BOSS_KILLS, 1.0);
        return new ScoreWeights(byBoard);
    }
}
