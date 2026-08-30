package rpg.core.statistics;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * Die Rangliste der Saison-Gesamtwertung (FR-050e).
 *
 * <h2>Warum sie keine {@link Aggregation} ist</h2>
 *
 * <p>Jede Aggregation ist eine Sicht auf <em>eine</em> Metrikfamilie: Kills, Tode, Spielzeit. Die
 * Gesamtwertung ist keine — sie entsteht aus <b>mehreren</b>, gewichtet, und ihre Einheit ist
 * „Punkte" und nicht die Einheit irgendeiner Metrik.
 *
 * <p>Sie trotzdem als Aggregation zu führen hätte bedeutet, ihr eine Quellmetrik zu geben, die sie
 * nicht hat — und jede Stelle, die {@code source()} liest, hätte eine Lüge bekommen: die
 * Zeitraumprüfung, die Metrikart, der Literal-Wächter. Ein Feld, das nur für einen Eintrag nicht
 * stimmt, ist schlimmer als ein eigener Typ.
 *
 * <p><b>Der Zwischenstand ist sichtbar, nicht erst der Endstand.</b> Eine Wertung, deren Stand man
 * erst erfährt, wenn sie vorbei ist, ist kein Wettbewerb, sondern eine Bekanntgabe — und niemand
 * richtet sein Spielen danach aus, was er nicht sehen kann.
 *
 * @param seasonKey welche Saison
 * @param top die ersten N, nach Punkten
 * @param scoreOf Konto → Punktzahl, vollständig
 * @param rankOf Konto → Platz, vollständig — damit die eigene Platzierung nichts kostet (FR-033)
 * @param refreshedAt wann zuletzt gerechnet wurde
 */
public record SeasonScoreBoard(
        String seasonKey,
        List<LeaderboardEntry> top,
        Map<UUID, Long> scoreOf,
        Map<UUID, Integer> rankOf,
        Instant refreshedAt) {

    public SeasonScoreBoard {
        Objects.requireNonNull(seasonKey, "seasonKey");
        Objects.requireNonNull(refreshedAt, "refreshedAt");
        top = List.copyOf(Objects.requireNonNull(top, "top"));
        scoreOf = Map.copyOf(Objects.requireNonNull(scoreOf, "scoreOf"));
        rankOf = Map.copyOf(Objects.requireNonNull(rankOf, "rankOf"));
    }

    /** Die Punktzahl eines Kontos. */
    public long scoreFor(UUID playerId) {
        return scoreOf.getOrDefault(playerId, 0L);
    }

    /** Der Platz eines Kontos, sofern es Punkte hat. */
    public OptionalInt rankFor(UUID playerId) {
        Integer rank = rankOf.get(playerId);
        return rank == null ? OptionalInt.empty() : OptionalInt.of(rank);
    }

    /**
     * Baut die Rangliste aus Punktzahlen.
     *
     * <p>Dieselbe Gleichstandsregel wie überall: gleiche Punktzahl, gleicher Platz, der nächste
     * überspringt — und der Entscheid innerhalb eines Gleichstands ist die Kontokennung, damit die
     * Reihenfolge über Auffrischungen hinweg stabil bleibt.
     *
     * <p>Wer null Punkte hat, steht nicht darauf. Das ist dieselbe Regel wie beim Endstand: eine
     * Saison, in der jemand nichts getan hat, hat ihn nicht auf dem letzten Platz — sie hat ihn
     * nicht.
     */
    public static SeasonScoreBoard of(
            String seasonKey,
            Map<UUID, Long> scores,
            int places,
            java.util.function.Function<UUID, String> names,
            Instant refreshedAt) {

        List<Map.Entry<UUID, Long>> ordered = new ArrayList<>();
        scores.forEach(
                (account, score) -> {
                    if (score > 0) {
                        ordered.add(Map.entry(account, score));
                    }
                });
        ordered.sort(
                Map.Entry.<UUID, Long>comparingByValue()
                        .reversed()
                        .thenComparing(entry -> entry.getKey().toString()));

        List<LeaderboardEntry> top = new ArrayList<>();
        Map<UUID, Long> byAccount = new LinkedHashMap<>();
        Map<UUID, Integer> ranks = new LinkedHashMap<>();

        int rank = 0;
        long previous = Long.MIN_VALUE;
        for (int i = 0; i < ordered.size(); i++) {
            Map.Entry<UUID, Long> entry = ordered.get(i);
            if (entry.getValue() != previous) {
                rank = i + 1;
                previous = entry.getValue();
            }
            byAccount.put(entry.getKey(), entry.getValue());
            ranks.put(entry.getKey(), rank);
            if (top.size() < places) {
                String name = names.apply(entry.getKey());
                top.add(
                        new LeaderboardEntry(
                                rank,
                                entry.getKey(),
                                name == null ? entry.getKey().toString() : name,
                                entry.getValue()));
            }
        }
        return new SeasonScoreBoard(seasonKey, top, byAccount, ranks, refreshedAt);
    }

    /** Nur zur Lesbarkeit in Tests. */
    static Comparator<LeaderboardEntry> byRank() {
        return Comparator.comparingInt(LeaderboardEntry::rank);
    }
}
