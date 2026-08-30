package rpg.core.statistics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * SC-020 — <b>die Punktzahl lässt sich aus den gezeigten Werten und Gewichten nachrechnen.</b>
 *
 * <p>Das ist keine Bequemlichkeit für den Spieler, sondern die Bedingung dafür, dass die Belohnung
 * akzeptiert wird. ADR-046 sagt es deutlich: eine Wertung, deren Zustandekommen man nicht sieht,
 * wird als Willkür gelesen. Wer Zweiter wird und nicht nachrechnen kann, warum, hält das Ergebnis
 * für manipuliert — und hat, streng genommen, keinen Grund, es nicht zu tun.
 *
 * <p>Deshalb prüft dieser Test nicht nur, dass die Summe stimmt, sondern dass <b>die angezeigte
 * Rechnung dieselbe ist</b>: jede Zeile trägt Wert, Einheit, Gewicht und Punkte, und die Punkte
 * der Zeilen summieren sich exakt auf die ausgewiesene Gesamtpunktzahl.
 */
class SeasonScoreIsRecomputableTest {

    @Test
    @DisplayName("SC-020 - die Aufschluesselung summiert sich exakt auf die Punktzahl")
    void thebreakdownAddsUpExactly() {
        Map<Aggregation, Long> values = new LinkedHashMap<>();
        values.put(Aggregation.MOB_KILLS, 340L);
        values.put(Aggregation.BOSS_KILLS, 6L);
        values.put(Aggregation.PLAYTIME_ACTIVE, 12_600L); // 3,5 Stunden

        SeasonScore score = SeasonScore.of(values, weights());

        assertThat(score.addsUp()).isTrue();
        assertThat(score.parts()).extracting(SeasonScore.Part::points).containsExactly(340L, 300L, 8L);
        assertThat(score.total()).isEqualTo(648L);
    }

    @Test
    @DisplayName("SC-020 - jede Zeile traegt Wert, Einheit, Gewicht und Punkte")
    void everyLineCarriesValueUnitWeightAndPoints() {
        SeasonScore score =
                SeasonScore.of(Map.of(Aggregation.BOSS_KILLS, 6L), weights());

        SeasonScore.Part boss = score.parts().get(0);
        assertThat(boss.board()).isEqualTo(Aggregation.BOSS_KILLS);
        assertThat(boss.value()).isEqualTo(6L);
        assertThat(boss.units()).isEqualTo(6L);
        assertThat(boss.weight()).isEqualTo(50.0);
        assertThat(boss.points()).isEqualTo(300L);

        // Nachgerechnet, wie ein Spieler es taete.
        assertThat((long) (boss.units() * boss.weight())).isEqualTo(boss.points());
    }

    @Test
    @DisplayName("die Spielzeit zaehlt je ANGEFANGENER Stunde, nicht je Sekunde")
    void playtimeCountsPerStartedHour() {
        // Ohne die Umrechnung waeren 3,5 Stunden 25 200 Punkte statt 8 - und die Saisonwertung
        // waere eine reine Anwesenheitsliste.
        SeasonScore score =
                SeasonScore.of(Map.of(Aggregation.PLAYTIME_ACTIVE, 12_600L), weights());

        assertThat(score.parts().get(0).units()).as("vier angefangene Stunden").isEqualTo(4L);
        assertThat(score.total()).isEqualTo(8L);
    }

    @Test
    @DisplayName("eine angefangene Stunde zaehlt ganz - eine Minute genuegt")
    void astartedHourCountsInFull() {
        assertThat(Aggregation.PLAYTIME_ACTIVE.scoreUnits(60L)).isEqualTo(1L);
        assertThat(Aggregation.PLAYTIME_ACTIVE.scoreUnits(3_600L)).isEqualTo(1L);
        assertThat(Aggregation.PLAYTIME_ACTIVE.scoreUnits(3_601L)).isEqualTo(2L);
        assertThat(Aggregation.PLAYTIME_ACTIVE.scoreUnits(0L)).isZero();
    }

    @Test
    @DisplayName("was nicht beitraegt, steht auch nicht in der Aufschluesselung")
    void whatDoesNotContributeIsNotListed() {
        Map<Aggregation, Long> values = new LinkedHashMap<>();
        values.put(Aggregation.MOB_KILLS, 10L);
        values.put(Aggregation.DEATHS, 12L); // Gewicht 0

        SeasonScore score = SeasonScore.of(values, weights());

        // Eine Zeile "Tode: 12 x 0 = 0" erklaert nichts und lenkt von den Zeilen ab, die etwas
        // erklaeren.
        assertThat(score.parts()).extracting(SeasonScore.Part::board)
                .containsExactly(Aggregation.MOB_KILLS);
    }

    @Test
    @DisplayName("ein Konto ohne jede Leistung hat null Punkte und eine leere Rechnung")
    void anaccountWithoutAnyAchievementScoresZero() {
        SeasonScore score = SeasonScore.of(Map.of(), weights());

        assertThat(score.total()).isZero();
        assertThat(score.parts()).isEmpty();
        assertThat(score.addsUp()).isTrue();
    }

    @Test
    @DisplayName("Punkte sind ganzzahlig und werden je Zeile abgerundet")
    void pointsAreWholeNumbersRoundedPerLine() {
        // Ein Rang, der sich in der dritten Nachkommastelle entscheidet, ist von einem Zufall
        // nicht zu unterscheiden - und zwei Konten mit derselben angezeigten Punktzahl auf
        // verschiedenen Plaetzen saehen aus wie ein Fehler.
        ScoreWeights fractional =
                new ScoreWeights(Map.of(Aggregation.MOB_KILLS, 0.5));

        SeasonScore score = SeasonScore.of(Map.of(Aggregation.MOB_KILLS, 7L), fractional);

        assertThat(score.parts().get(0).points()).isEqualTo(3L);
        assertThat(score.total()).isEqualTo(3L);
        assertThat(score.addsUp()).isTrue();
    }

    private static ScoreWeights weights() {
        Map<Aggregation, Double> byBoard = new LinkedHashMap<>();
        byBoard.put(Aggregation.MOB_KILLS, 1.0);
        byBoard.put(Aggregation.BOSS_KILLS, 50.0);
        byBoard.put(Aggregation.PLAYTIME_ACTIVE, 2.0);
        byBoard.put(Aggregation.DAMAGE_MAX, 0.0);
        byBoard.put(Aggregation.DEATHS, 0.0);
        return new ScoreWeights(byBoard);
    }
}
