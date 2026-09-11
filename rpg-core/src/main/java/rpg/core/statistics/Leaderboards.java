package rpg.core.statistics;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * Was B12 nach außen an <b>Ranglisten</b> anbietet (contracts/stats-api.md §3).
 *
 * <h2>Die eine Zusicherung, die alles trägt</h2>
 *
 * <p>{@link #board} ist <b>synchron und tickfrei</b> und löst <b>niemals</b> eine
 * Datenbankabfrage aus (FR-030). Der Wert steht im Speicher; gefüllt wird er im
 * Auffrischungstakt. Das ist der Grund, aus dem diese Methode aus einem Menü heraus gerufen werden
 * darf, und der Grund, aus dem fünfzig gleichzeitige Öffnungen null Abfragen kosten (SC-003).
 *
 * <p>Ein leeres {@code Optional} heißt <b>„noch nie aufgefrischt"</b> und ist von der Ansicht als
 * Meldung darzustellen — nicht als leere Liste (FR-035). Der Unterschied ist der zwischen „der
 * Stand kommt gleich" und „niemand hat je etwas getan", und der zweite Satz wäre eine Lüge.
 *
 * <h2>Warum hier {@link Aggregation} steht und nicht {@link Metric}</h2>
 *
 * <p>Der Vertrag hatte ursprünglich {@code Metric}. Das ging nicht auf: {@code boss_kills} ist
 * keine Metrik (FR-009 gibt ihm bewusst keinen Zähler), und {@code deaths} wie
 * {@code playtime_active} werden als <em>Familie</em> gerankt, nicht je Schlüssel. Eine Rangliste
 * ist eine Aggregation, und der Typ heißt jetzt so.
 */
public interface Leaderboards {

    /** Der aktuelle Stand aus dem Speicher. Löst niemals eine Datenbankabfrage aus (FR-030). */
    Optional<Leaderboard> board(Aggregation board, Period period, String periodKey);

    /** Der Stand eines Zeitraums ohne Untergliederung — Allzeit. */
    default Optional<Leaderboard> board(Aggregation board, Period period) {
        return board(board, period, "");
    }

    /** Der Rang eines Kontos, auch außerhalb der ersten N (FR-033). */
    default OptionalInt rankOf(Aggregation board, Period period, UUID playerId) {
        return board(board, period)
                .map(list -> list.rankFor(playerId))
                .orElse(OptionalInt.empty());
    }

    /**
     * Die Saison-Gesamtwertung der <b>laufenden</b> Saison (FR-050e).
     *
     * <p>Sie steht hier und nicht bei {@link Seasons}, weil sie dieselbe Zusicherung trägt wie jede
     * andere Rangliste: aus dem Speicher, tickfrei, ohne Abfrage. Ein Zwischenstand, dessen Abruf
     * etwas kostet, würde beim ersten Andrang abgeschaltet — und wäre damit wieder erst am
     * Saisonende sichtbar.
     *
     * <p>Leer heißt <b>„gerade läuft keine Saison"</b> oder „noch nie aufgefrischt". Beides ist
     * etwas anderes als eine leere Wertung: die eine sagt „es gibt nichts zu gewinnen", die andere
     * „noch niemand hat Punkte".
     */
    Optional<SeasonScoreBoard> seasonScore();

    /** Wann zuletzt aufgefrischt wurde — jede Ansicht muss das Alter nennen (FR-032). */
    Optional<Instant> refreshedAt();

    /** Die übliche Fassung: sie liest den Speicherstand und sonst nichts. */
    static Leaderboards backedBy(LeaderboardCache cache) {
        Objects.requireNonNull(cache, "cache");
        return new Leaderboards() {

            @Override
            public Optional<Leaderboard> board(Aggregation board, Period period, String periodKey) {
                if (board.visibility() != MetricVisibility.PUBLIC) {
                    // FR-036: es gibt keine private Rangliste. Nicht "eine, die man nicht sehen
                    // darf" - gar keine. Der Unterschied ist der zwischen einer Sperre, die
                    // jede Ansicht einzeln durchsetzen muss, und einer Antwort, die es nirgends
                    // zu sperren gibt.
                    return Optional.empty();
                }
                if (!period.fits(board.source().kind())) {
                    // FR-023: ein Zustandswert hat keine Tages-, Wochen- oder Saisonform.
                    // Abgewiesen, nicht auf den aktuellen Stand umgedeutet - sonst zeigte eine
                    // "Level heute"-Liste denselben Inhalt wie die Allzeitliste, und niemand
                    // wuesste, dass der Zeitraum nichts bedeutet.
                    return Optional.empty();
                }
                return cache.board(new LeaderboardCache.Key(board, period, periodKey));
            }

            @Override
            public Optional<SeasonScoreBoard> seasonScore() {
                return cache.seasonScore();
            }

            @Override
            public Optional<Instant> refreshedAt() {
                return cache.refreshedAt();
            }
        };
    }
}
