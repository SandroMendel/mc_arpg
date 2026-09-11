package rpg.core.statistics;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Die Gewichtung der Saison-Gesamtwertung — <b>und sie wird eingefroren</b> (FR-050, ADR-046).
 *
 * <h2>Warum sie mit dem Endstand gespeichert wird</h2>
 *
 * <p>Eine Platzierung ist vergeben, sobald die Saison zu Ende ist. Läge die Gewichtung nur in
 * {@code statistics.yml}, sortierte die nächste Balancing-Änderung eine abgeschlossene Saison
 * rückwirkend um — und zwar unbemerkt, weil niemand eine Rangliste erneut aufruft, die er schon
 * gesehen hat. Wer den ersten Platz hatte, hätte ihn dann irgendwann nicht mehr, ohne dass etwas
 * passiert wäre.
 *
 * <p>Deshalb geht die Gewichtung als {@code JSONB} in {@code season_result}. Der Endstand trägt
 * damit seine eigene Rechenvorschrift bei sich: er ist nachrechenbar, auch Jahre später und auch
 * dann, wenn die Konfiguration inzwischen etwas ganz anderes sagt (SC-021).
 */
public record ScoreWeights(Map<Aggregation, Double> byBoard) {

    public ScoreWeights {
        byBoard =
                Map.copyOf(
                        new LinkedHashMap<>(Objects.requireNonNull(byBoard, "byBoard")));
        for (Map.Entry<Aggregation, Double> entry : byBoard.entrySet()) {
            if (!entry.getKey().scoreable()) {
                // Doppelt gemoppelt zum Schema - und mit Absicht: diese Klasse wird auch aus der
                // Datenbank gebaut, wo kein Schema mehr dazwischensteht.
                throw new IllegalArgumentException(
                        "'" + entry.getKey().key() + "' darf keine Gewichtung tragen (ADR-046)");
            }
            if (entry.getValue() < 0) {
                throw new IllegalArgumentException(
                        "negatives Gewicht fuer '" + entry.getKey().key() + "'");
            }
        }
    }

    /** Das Gewicht einer Rangliste; nicht genannt heißt null. */
    public double weightOf(Aggregation board) {
        return byBoard.getOrDefault(board, 0.0);
    }

    /** Ob überhaupt eine Rangliste beiträgt. */
    public boolean isEmpty() {
        return byBoard.isEmpty();
    }

    /** Die Gewichtung aus der laufenden Konfiguration. */
    public static ScoreWeights from(StatisticsConfig.Score score) {
        return new ScoreWeights(score.weights());
    }
}
