package rpg.core.statistics;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Eine Zeile des eingefrorenen Endstands ([data-model.md] §1.3).
 *
 * @param seasonKey welche Saison
 * @param playerId welches Konto
 * @param rank der Platz; Gleichstände tragen denselben
 * @param score die erreichte Punktzahl
 * @param frozenAt wann der Abschluss lief — <b>nicht</b>, wann die Saison endete
 */
public record SeasonStanding(
        String seasonKey, UUID playerId, int rank, long score, Instant frozenAt) {

    public SeasonStanding {
        Objects.requireNonNull(seasonKey, "seasonKey");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(frozenAt, "frozenAt");
        if (rank < 1) {
            throw new IllegalArgumentException("ein Platz beginnt bei 1, war aber " + rank);
        }
        if (score < 0) {
            throw new IllegalArgumentException("eine Punktzahl ist nie negativ, war aber " + score);
        }
    }

    /**
     * Baut den Endstand einer Saison aus Punktzahlen.
     *
     * <p>Dieselbe Gleichstandsregel wie in einer Rangliste (FR-034): gleiche Punktzahl, gleicher
     * Platz, und der nächste überspringt. Der Entscheid innerhalb eines Gleichstands ist die
     * Kontokennung — hier ist er sichtbarer als sonst, weil an ihm eine Belohnung hängt. Er ist
     * trotzdem der richtige: jede andere Wahl (wer zuerst da war, wer zuletzt spielte) wäre eine
     * <em>zusätzliche</em> Regel, die niemand angekündigt hat.
     */
    public static List<SeasonStanding> rank(
            String seasonKey, java.util.Map<UUID, Long> scores, Instant frozenAt) {

        List<java.util.Map.Entry<UUID, Long>> ordered = new java.util.ArrayList<>(scores.entrySet());
        ordered.sort(
                java.util.Map.Entry.<UUID, Long>comparingByValue()
                        .reversed()
                        .thenComparing(entry -> entry.getKey().toString()));

        List<SeasonStanding> standings = new java.util.ArrayList<>();
        int rank = 0;
        long previous = Long.MIN_VALUE;
        for (int i = 0; i < ordered.size(); i++) {
            java.util.Map.Entry<UUID, Long> entry = ordered.get(i);
            if (entry.getValue() != previous) {
                rank = i + 1;
                previous = entry.getValue();
            }
            standings.add(new SeasonStanding(seasonKey, entry.getKey(), rank, entry.getValue(), frozenAt));
        }
        return List.copyOf(standings);
    }
}
