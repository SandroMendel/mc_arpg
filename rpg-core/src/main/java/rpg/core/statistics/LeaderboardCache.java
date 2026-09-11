package rpg.core.statistics;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Der Speicherstand aller Ranglisten — <b>das Öffnen einer Ansicht kostet keine Abfrage</b>
 * (FR-030, SC-003).
 *
 * <h2>Warum überhaupt ein Cache</h2>
 *
 * <p>Eine Rangliste ist eine Aggregation über potenziell alle Zeilen der Tagestabelle. Sie beim
 * Öffnen zu berechnen wäre bei einem Spieler unauffällig und bei fünfzig gleichzeitigen ein
 * stehender Server — und zwar genau dann, wenn viel los ist, weil dann auch viele nachsehen. Das
 * Blockdokument nennt deshalb als Akzeptanzkriterium: fünfzig gleichzeitige Aufrufe, <b>höchstens
 * eine</b> Datenbankabfrage.
 *
 * <p>Hier sind es null. Die Abfragen laufen bei der Auffrischung, nicht beim Lesen.
 *
 * <h2>Ausgetauscht, nicht verändert</h2>
 *
 * <p>Eine Auffrischung ersetzt den ganzen Stand über eine einzige Zuweisung. Wer währenddessen
 * liest, sieht entweder den alten oder den neuen Stand — nie einen halb aufgebauten. Das ist der
 * Grund für das {@link AtomicReference} auf eine unveränderliche Map statt einer Map, in der die
 * Auffrischung Eintrag für Eintrag ersetzt.
 *
 * <h2>Zwei Quellen, ein Stand</h2>
 *
 * <p>Die Zählerranglisten kommen aus den Materialized Views, die beiden Zustandsranglisten (Level,
 * Coins) aus dem Fortschritts- und dem Kontobestand (ADR-041). <b>Der Preis dieser Entscheidung
 * ist benannt:</b> dieser Cache hat zwei Quellen und damit zwei Wege, unvollständig zu sein. Der
 * Gewinn ist, dass die Zusage „das Öffnen kostet nichts" für <em>alle</em> Ranglisten gilt und
 * nicht nur für die Mehrheit — eine Ausnahme davon hätte niemand im Fenster erkannt.
 */
public final class LeaderboardCache {

    /** Der Schlüssel einer Rangliste: was gerankt wird, über welchen Zeitraum, und welchen. */
    public record Key(Aggregation board, Period period, String periodKey) {

        public Key {
            Objects.requireNonNull(board, "board");
            Objects.requireNonNull(period, "period");
        }

        /** Für Zeiträume ohne Untergliederung — Allzeit. */
        public static Key of(Aggregation board, Period period) {
            return new Key(board, period, "");
        }
    }

    private final AtomicReference<Map<Key, Leaderboard>> boards =
            new AtomicReference<>(Map.of());

    private final AtomicReference<Instant> refreshedAt = new AtomicReference<>();

    /**
     * Die Saison-Gesamtwertung — <b>ein eigener Platz, kein Eintrag in der Karte oben.</b>
     *
     * <p>Sie ist keine {@link Aggregation}: sie entsteht aus mehreren Metriken, gewichtet, und
     * ihre Einheit ist „Punkte". Siehe {@link SeasonScoreBoard} für die Begründung. Sie liegt
     * trotzdem <em>hier</em> und nicht anderswo, weil FR-030 auch für sie gilt — der Zwischenstand
     * ist sichtbar (FR-050e), und sichtbar heißt: ohne Abfrage beim Öffnen.
     */
    private final AtomicReference<SeasonScoreBoard> seasonScore = new AtomicReference<>();

    /**
     * Eine Rangliste, oder leer, wenn es sie noch nicht gibt.
     *
     * <p><b>Synchron und tickfrei</b>: ein Zugriff auf eine Map. Genau deshalb darf diese Methode
     * aus einem Menü heraus gerufen werden.
     */
    public Optional<Leaderboard> board(Key key) {
        return Optional.ofNullable(boards.get().get(key));
    }

    /** Wann zuletzt aufgefrischt wurde — für die Altersangabe in jeder Ansicht (FR-032). */
    public Optional<Instant> refreshedAt() {
        return Optional.ofNullable(refreshedAt.get());
    }

    /** Ob überhaupt schon einmal aufgefrischt wurde (FR-035). */
    public boolean isReady() {
        return refreshedAt.get() != null;
    }

    /**
     * Ersetzt den ganzen Stand.
     *
     * <p>Eine Zuweisung, kein schrittweises Befüllen: wer gleichzeitig liest, bekommt einen
     * vollständigen Stand, nie einen halben.
     */
    public void replace(Map<Key, Leaderboard> next, Instant at) {
        Objects.requireNonNull(at, "at");
        boards.set(Map.copyOf(Objects.requireNonNull(next, "next")));
        refreshedAt.set(at);
    }

    /** Der Zwischenstand der laufenden Saison, sofern eine läuft und gerechnet wurde (FR-050e). */
    public Optional<SeasonScoreBoard> seasonScore() {
        return Optional.ofNullable(seasonScore.get());
    }

    /**
     * Ersetzt die Saison-Gesamtwertung.
     *
     * <p>{@code null} heißt „keine laufende Saison" — und das ist etwas anderes als eine leere
     * Wertung: die eine sagt „es gibt gerade nichts zu gewinnen", die andere „noch niemand hat
     * Punkte". Beides trifft zwischen zwei Saisons zu, aber nur das Erste erklärt es.
     */
    public void replaceSeasonScore(SeasonScoreBoard board) {
        seasonScore.set(board);
    }

    /** Wie viele Ranglisten gerade im Stand liegen — für Diagnose und Tests. */
    public int size() {
        return boards.get().size();
    }

    /** Ein leerer Stand, den ein Test oder ein Start frisch anlegt. */
    public static LeaderboardCache empty() {
        return new LeaderboardCache();
    }

    /** Nur für Tests: ein veränderlicher Zwischenspeicher beim Aufbau eines Standes. */
    public static Map<Key, Leaderboard> newBuilder() {
        return new ConcurrentHashMap<>();
    }
}
