package rpg.core.statistics;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * Eine fertige Rangliste — die ersten N Einträge <b>plus die vollständige Rangzuordnung</b>
 * ([data-model.md] §2.2).
 *
 * <h2>Warum beides und nicht nur die ersten N</h2>
 *
 * <p>FR-033 verlangt, dass ein Spieler seine eigene Platzierung sieht, auch wenn er nicht unter
 * den ersten zehn steht. Ohne die vollständige Zuordnung wäre das eine Nachfrage an die Datenbank
 * — beim Öffnen des Fensters, also genau dort, wo FR-030 keine Abfrage erlaubt. Die Zuordnung ist
 * eine Map von Konto auf Platz und kostet je Konto ein paar Bytes; die Alternative kostet eine
 * Abfrage je Öffnung.
 *
 * <h2>Gleichstand</h2>
 *
 * <p>Gleiche Werte tragen <b>denselben</b> Platz (FR-034). Der nächste Platz überspringt
 * entsprechend: 1, 2, 2, 4. Und die Reihenfolge innerhalb eines Gleichstands ist <b>stabil</b> —
 * nach Kontokennung, nicht nach Zufall. Sonst tauschten zwei Spieler bei jeder Auffrischung die
 * Plätze, ohne dass sich etwas geändert hätte, und beide hielten es für einen Fehler.
 */
public record Leaderboard(
        Aggregation board,
        Period period,
        String periodKey,
        List<LeaderboardEntry> top,
        Map<UUID, Integer> rankOf,
        Map<UUID, Long> valueOf,
        Instant refreshedAt) {

    public Leaderboard {
        Objects.requireNonNull(board, "board");
        Objects.requireNonNull(period, "period");
        Objects.requireNonNull(refreshedAt, "refreshedAt");
        top = List.copyOf(Objects.requireNonNull(top, "top"));
        rankOf = Map.copyOf(Objects.requireNonNull(rankOf, "rankOf"));
        valueOf = Map.copyOf(Objects.requireNonNull(valueOf, "valueOf"));
    }

    /**
     * Der Wert eines Kontos — auch weit außerhalb der ersten N.
     *
     * <p><b>Dazugekommen für das fremde Profil</b> (FR-044): dessen Kill-Zahlen brauchen die
     * Trennung in Mob- und Bosskills, und die entsteht aus der Aufschlüsselung — die ein fremdes
     * Konto nach FR-037 nicht herausgibt. Aus dem Speicherstand kommt sie fertig, weil die
     * Auffrischung sie ohnehin gebildet hat.
     *
     * <p>Der Preis ist ein {@code long} je Konto und Rangliste. Die Alternative wäre gewesen, dem
     * fremden Profil die Aufteilung zu verweigern — oder ihm doch eine Aufschlüsselung zu geben,
     * und damit die Regel, um die es hier geht.
     */
    public long valueFor(UUID playerId) {
        return valueOf.getOrDefault(playerId, 0L);
    }

    /** Der Platz eines Kontos, sofern es überhaupt einen Wert hat. */
    public OptionalInt rankFor(UUID playerId) {
        Integer rank = rankOf.get(playerId);
        return rank == null ? OptionalInt.empty() : OptionalInt.of(rank);
    }

    /** Der Eintrag eines Kontos unter den ersten N, sofern es dort steht. */
    public Optional<LeaderboardEntry> entryFor(UUID playerId) {
        return top.stream().filter(entry -> entry.playerId().equals(playerId)).findFirst();
    }

    /** Ob überhaupt jemand auf dieser Liste steht. */
    public boolean isEmpty() {
        return top.isEmpty();
    }

    /**
     * Baut eine Rangliste aus rohen Werten.
     *
     * @param values Konto → Wert, ungeordnet
     * @param places wie viele Einträge {@link #top()} tragen soll
     * @param names Anzeigename je Konto; fehlt einer, steht die Kennung da
     */
    public static Leaderboard of(
            Aggregation board,
            Period period,
            String periodKey,
            Map<UUID, Long> values,
            int places,
            Map<UUID, String> names,
            Instant refreshedAt) {
        return of(board, period, periodKey, values, Map.of(), places, names, refreshedAt);
    }

    /**
     * Dasselbe mit einem <b>fachlichen</b> Gleichstandsentscheid.
     *
     * <p>Die Level-Rangliste braucht ihn: bei gleichem Level entscheidet die XP <em>innerhalb</em>
     * dieses Levels (FR-020), nicht die Kontokennung. Angezeigt wird trotzdem der Level — der
     * zweite Wert ordnet nur.
     *
     * @param tieBreak zweiter Wert je Konto, absteigend; fehlt einer, zählt er als null
     */
    public static Leaderboard of(
            Aggregation board,
            Period period,
            String periodKey,
            Map<UUID, Long> values,
            Map<UUID, Long> tieBreak,
            int places,
            Map<UUID, String> names,
            Instant refreshedAt) {
        return of(
                board,
                period,
                periodKey,
                values,
                tieBreak,
                places,
                playerId -> names.get(playerId),
                refreshedAt);
    }

    /**
     * Dasselbe mit einer <b>Namensauflösung auf Abruf</b> (FR-040).
     *
     * <p>Aufgelöst wird nur für die Einträge, die tatsächlich angezeigt werden — bei zehn Plätzen
     * also zehnmal, nicht einmal je Konto auf dem Server. Und es passiert hier, beim Füllen,
     * außerhalb des Ticks: ein Namensnachschlag beim Öffnen wäre je Zeile eine Frage an den
     * Server, während der Spieler wartet.
     *
     * @param names Konto → Anzeigename; {@code null} heißt „unbekannt", dann steht die Kennung da
     */
    public static Leaderboard of(
            Aggregation board,
            Period period,
            String periodKey,
            Map<UUID, Long> values,
            Map<UUID, Long> tieBreak,
            int places,
            java.util.function.Function<UUID, String> names,
            Instant refreshedAt) {

        List<Map.Entry<UUID, Long>> ordered = new ArrayList<>(values.entrySet());
        ordered.sort(
                Map.Entry.<UUID, Long>comparingByValue()
                        .reversed()
                        .thenComparing(
                                entry -> tieBreak.getOrDefault(entry.getKey(), 0L),
                                Comparator.reverseOrder())
                        // Der letzte Entscheid ist die Kennung: stabil und nachvollziehbar. Ohne
                        // ihn waere die Reihenfolge die einer HashMap, und die aendert sich mit
                        // jedem Neustart - zwei Spieler taeuschten Bewegung vor, wo keine war.
                        .thenComparing(entry -> entry.getKey().toString()));

        List<LeaderboardEntry> top = new ArrayList<>();
        Map<UUID, Integer> ranks = new LinkedHashMap<>();

        int rank = 0;
        long previousValue = Long.MIN_VALUE;
        for (int i = 0; i < ordered.size(); i++) {
            Map.Entry<UUID, Long> entry = ordered.get(i);
            if (entry.getValue() != previousValue) {
                // 1, 2, 2, 4 - der naechste Platz ueberspringt die geteilten.
                rank = i + 1;
                previousValue = entry.getValue();
            }
            ranks.put(entry.getKey(), rank);
            if (top.size() < places) {
                String name = names.apply(entry.getKey());
                top.add(
                        new LeaderboardEntry(
                                rank,
                                entry.getKey(),
                                // Ohne bekannten Namen die Kennung: eine leere Zeile saehe aus
                                // wie ein Fehler, und ein Platzhalter waere eine Behauptung.
                                name == null ? entry.getKey().toString() : name,
                                entry.getValue()));
            }
        }
        return new Leaderboard(board, period, periodKey, top, ranks, values, refreshedAt);
    }

    /** Eine Liste, die es noch nicht gibt — vor der ersten Auffrischung (FR-035). */
    public static Leaderboard empty(
            Aggregation board, Period period, String periodKey, Instant refreshedAt) {
        return new Leaderboard(
                board, period, periodKey, List.of(), Map.of(), Map.of(), refreshedAt);
    }

    /** Nur damit der Vergleich in Tests lesbar bleibt. */
    static Comparator<LeaderboardEntry> byRank() {
        return Comparator.comparingInt(LeaderboardEntry::rank);
    }
}
