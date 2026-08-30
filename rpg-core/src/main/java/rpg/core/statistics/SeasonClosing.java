package rpg.core.statistics;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Der Abschluss einer Saison: <b>Endstand einfrieren, Ansprüche anlegen</b> (FR-050, FR-052).
 *
 * <h2>Er läuft genau einmal — auch wenn der Server aus war</h2>
 *
 * <p>FR-058: war der Server über das Enddatum hinweg abgeschaltet, wird der Abschluss beim Start
 * nachgeholt. Das ist der Normalfall und nicht die Ausnahme — ein Quartalsende fällt selten auf
 * einen Moment, in dem der Server gerade läuft.
 *
 * <p><b>Der Beleg dafür, dass er schon lief, sind die Zeilen in {@code season_result}</b>, nicht
 * ein Zustandsfeld. Ein Zustandsfeld wäre eine zweite Wahrheit über dieselbe Tatsache, und die
 * beiden liefen beim ersten Abbruch mitten im Abschluss auseinander: das Feld stünde auf
 * „abgeschlossen", die Zeilen fehlten, und niemand käme darauf nachzusehen.
 *
 * <h2>Was diese Klasse nicht tut</h2>
 *
 * <p>Sie fragt nichts ab und schreibt nichts. Sie bekommt die Punktzahlen gereicht und liefert,
 * was geschrieben werden soll — die Datenbank ist die Sache der Repositories. Damit ist der
 * Abschluss ohne Server und ohne Datenbank prüfbar, und genau das braucht ein Vorgang, der
 * viermal im Jahr läuft und beim ersten Mal richtig sein muss.
 */
public final class SeasonClosing {

    /** Was ein Abschluss hervorbringt. */
    public record Result(
            SeasonCalendar.Season season,
            List<SeasonStanding> standings,
            List<RewardClaim> claims,
            ScoreWeights weights) {

        public Result {
            standings = List.copyOf(standings);
            claims = List.copyOf(claims);
        }

        /** Ob überhaupt etwas zu schreiben ist. */
        public boolean isEmpty() {
            return standings.isEmpty();
        }
    }

    private SeasonClosing() {}

    /**
     * Bereitet den Abschluss einer Saison vor.
     *
     * @param season die abzuschließende Saison
     * @param scores Punktzahl je Konto über den Saisonzeitraum
     * @param rewards die konfigurierten Belohnungen je Platz
     * @param at wann der Abschluss läuft — <b>nicht</b>, wann die Saison endete
     */
    public static Result close(
            SeasonCalendar.Season season,
            Map<UUID, Long> scores,
            Map<Integer, StatisticsConfig.Reward> rewards,
            ScoreWeights weights,
            Instant at) {

        Objects.requireNonNull(season, "season");
        Objects.requireNonNull(at, "at");

        // Wer null Punkte hat, steht nicht im Endstand. Eine Saison, in der jemand nichts getan
        // hat, hat ihn nicht "auf dem letzten Platz" - sie hat ihn gar nicht.
        Map<UUID, Long> scored = new LinkedHashMap<>();
        scores.forEach(
                (account, score) -> {
                    if (score > 0) {
                        scored.put(account, score);
                    }
                });

        List<SeasonStanding> standings = SeasonStanding.rank(season.key(), scored, at);

        List<RewardClaim> claims = new ArrayList<>();
        for (SeasonStanding standing : standings) {
            StatisticsConfig.Reward reward = rewards.get(standing.rank());
            if (reward == null) {
                // Für diesen Platz ist nichts vorgesehen. Die Platzierung steht trotzdem im
                // Endstand - sie ist die Ehre, der Anspruch waere der Preis.
                continue;
            }
            claims.add(RewardClaim.open(season.key(), standing.playerId(), standing.rank(), reward));
        }

        return new Result(season, standings, claims, weights);
    }

    /**
     * Welche Saisons abgeschlossen werden müssen.
     *
     * <p>Alle, deren Enddatum vorbei ist und für die es noch keinen Endstand gibt — nicht nur die
     * letzte. War ein Server ein halbes Jahr aus, sind es zwei; sie einzeln nachzuholen ist
     * richtiger, als die ältere zu überspringen und ihre Belohnungen verfallen zu lassen.
     *
     * @param closedAlready sagt, ob eine Saison bereits einen Endstand hat
     */
    public static List<SeasonCalendar.Season> due(
            SeasonCalendar calendar,
            LocalDate today,
            java.util.function.Predicate<String> closedAlready) {

        List<SeasonCalendar.Season> due = new ArrayList<>();
        for (SeasonCalendar.Season season : calendar.all()) {
            if (season.to().isBefore(today) && !closedAlready.test(season.key())) {
                due.add(season);
            }
        }
        return due;
    }
}
