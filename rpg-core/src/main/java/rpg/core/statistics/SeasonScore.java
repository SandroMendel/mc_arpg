package rpg.core.statistics;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Die Punktzahl eines Kontos in einer Saison — <b>und ihre Aufschlüsselung</b> (ADR-046, FR-050b).
 *
 * <h2>Warum die Aufschlüsselung nicht optional ist</h2>
 *
 * <p>ADR-046 verlangt sie ausdrücklich: <em>„eine Wertung, deren Zustandekommen man nicht sieht,
 * wird als Willkür gelesen — und bei einer Belohnung wird sie das lauter als anderswo."</em>
 *
 * <p>Deshalb ist sie kein Zusatz für ein Fenster, sondern Teil des Ergebnisses. Ein
 * {@link SeasonScore} ohne {@link #parts()} wäre eine Zahl, die man glauben muss; mit ihnen ist er
 * eine Rechnung, die man nachvollziehen kann (SC-020). Die Summe der Punkte aller Teile
 * <b>ist</b> die Gesamtpunktzahl — nicht ungefähr, sondern exakt.
 *
 * <h2>Ganzzahlig, und abgerundet</h2>
 *
 * <p>Gewichte dürfen Kommazahlen sein, Punkte nicht. Ein Rang, der sich in der dritten
 * Nachkommastelle entscheidet, ist für einen Spieler ununterscheidbar von einem Zufall — und zwei
 * Konten mit „derselben" Punktzahl, die trotzdem verschiedene Plätze bekommen, sehen aus wie ein
 * Fehler. Gerundet wird je Teil, nicht erst am Ende, damit die angezeigte Rechnung auch aufgeht.
 */
public record SeasonScore(long total, List<Part> parts) {

    /**
     * Ein Beitrag zur Punktzahl (FR-050f).
     *
     * @param board welche Rangliste
     * @param value der Rohwert, so wie er in der Statistik steht
     * @param units die daraus folgenden Punkteinheiten — bei der Spielzeit angefangene Stunden
     * @param weight das Gewicht je Einheit
     * @param points {@code units × weight}, abgerundet
     */
    public record Part(Aggregation board, long value, long units, double weight, long points) {}

    public SeasonScore {
        parts = List.copyOf(Objects.requireNonNull(parts, "parts"));
    }

    /**
     * Rechnet die Punktzahl aus.
     *
     * <p>Nur beitragende Ranglisten erscheinen in der Aufschlüsselung: eine Zeile „Tode: 12 × 0 =
     * 0" erklärt nichts und lenkt von den Zeilen ab, die etwas erklären. Ein Gewicht von null
     * heißt „trägt nicht bei", und genau so sieht die Aufschlüsselung dann aus.
     *
     * @param values Rohwerte je Rangliste über den Saisonzeitraum
     */
    public static SeasonScore of(Map<Aggregation, Long> values, ScoreWeights weights) {
        List<Part> parts = new ArrayList<>();
        long total = 0;

        for (Aggregation board : Aggregation.values()) {
            double weight = weights.weightOf(board);
            if (weight <= 0) {
                continue;
            }
            long value = values.getOrDefault(board, 0L);
            long units = board.scoreUnits(value);
            long points = (long) Math.floor(units * weight);
            if (points <= 0) {
                // Wer nichts erreicht hat, bekommt keine Zeile - aber wer etwas erreicht hat, das
                // nach Gewichtung unter einem Punkt bleibt, auch nicht. Beides waere eine Zeile
                // mit einer Null darin.
                continue;
            }
            parts.add(new Part(board, value, units, weight, points));
            total += points;
        }
        return new SeasonScore(total, parts);
    }

    /**
     * Ob die Aufschlüsselung sich auf die Gesamtpunktzahl summiert.
     *
     * <p>Die Zusicherung aus SC-020, als Frage formulierbar gemacht. Sie gilt hier durch Bauart —
     * dieser Prüfer existiert, damit ein Test sie festhalten kann, ohne die Rechnung
     * nachzubauen.
     */
    public boolean addsUp() {
        return parts.stream().mapToLong(Part::points).sum() == total;
    }
}
