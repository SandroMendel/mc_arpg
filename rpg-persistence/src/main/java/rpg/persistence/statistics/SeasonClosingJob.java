package rpg.persistence.statistics;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import rpg.core.statistics.Aggregation;
import rpg.core.statistics.ScoreWeights;
import rpg.core.statistics.SeasonCalendar;
import rpg.core.statistics.SeasonClosing;
import rpg.core.statistics.SeasonScore;
import rpg.core.statistics.StatisticsConfig;

/**
 * Holt fällige Saisonabschlüsse nach — <b>beim Start und in jedem Auffrischungstakt</b> (FR-058).
 *
 * <h2>Warum beides und nicht eines</h2>
 *
 * <p>Ein Quartalsende fällt selten auf einen Moment, in dem der Server gerade läuft: die
 * Nachholung <b>beim Start</b> ist deshalb der Normalfall und nicht die Ausnahme. Aber ein Server,
 * der über den Jahreswechsel durchläuft, hätte damit nie abgeschlossen — dafür der Takt. Zusammen
 * decken sie beide Fälle, und keiner von beiden braucht einen Zeitplan, der sich merkt, wann er
 * das letzte Mal lief.
 *
 * <h2>Zweimal laufen kostet nichts</h2>
 *
 * <p>Der Beleg dafür, dass eine Saison abgeschlossen ist, sind ihre Zeilen in
 * {@code season_result} — kein Zustandsfeld (siehe {@link SeasonClosing}). Ein zweiter Durchlauf
 * findet sie und tut nichts. Das ist der Grund, aus dem dieser Job im Takt mitlaufen darf, ohne
 * dass jemand mitzählt: er ist im Regelfall eine Abfrage, die „schon erledigt" antwortet.
 *
 * <h2>Er rechnet aus denselben Rohdaten wie die Rangliste</h2>
 *
 * <p>Nicht aus dem Speicherstand: der hält die <em>laufende</em> Saison, und abzuschließen ist
 * die <em>vorige</em>. Und nicht aus einer zweiten Ablage — FR-057 und ADR-044 halten fest, dass
 * ein Saisonwechsel keine Rohdaten löscht, und genau deshalb ist der Endstand jederzeit wieder
 * herleitbar.
 */
public final class SeasonClosingJob {

    private final JdbcLeaderboardSource counters;
    private final JdbcSeasonResultRepository results;
    private final JdbcRewardClaimRepository claims;
    private final Supplier<StatisticsConfig> config;
    private final Logger logger;
    private final Clock clock;

    public SeasonClosingJob(
            JdbcLeaderboardSource counters,
            JdbcSeasonResultRepository results,
            JdbcRewardClaimRepository claims,
            Supplier<StatisticsConfig> config,
            Logger logger,
            Clock clock) {
        this.counters = Objects.requireNonNull(counters, "counters");
        this.results = Objects.requireNonNull(results, "results");
        this.claims = Objects.requireNonNull(claims, "claims");
        this.config = Objects.requireNonNull(config, "config");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Schließt jede beendete, noch offene Saison ab.
     *
     * <p><b>Aus einem Hintergrundfaden zu rufen.</b> Hier wird abgefragt und geschrieben.
     *
     * <p>Ein Fehler bei einer Saison hält die anderen nicht auf: der Abschluss ist idempotent, der
     * nächste Takt versucht es erneut, und bis dahin fehlt ein Endstand — was ein Betreiber im
     * Protokoll findet und niemand still verliert.
     *
     * @return wie viele Saisons in diesem Durchlauf abgeschlossen wurden
     */
    public int closeDueSeasons() {
        StatisticsConfig settings = config.get();
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        ScoreWeights weights = ScoreWeights.from(settings.score());

        int closed = 0;

        // WELCHE faellig sind, entscheidet rpg-core - dieselbe Antwort, die ohne Datenbank
        // geprueft wird (ClosingIsIdempotentTest). Hier steht nur, WORAUS die Antwort kommt: das
        // Enddatum aus dem Kalender und die vorhandenen Zeilen als Beleg.
        for (SeasonCalendar.Season season :
                SeasonClosing.due(settings.seasons(), today, results::isClosed)) {
            try {
                if (close(season, settings, weights)) {
                    closed++;
                }
            } catch (RuntimeException failure) {
                // Eine gescheiterte Saison haelt die anderen nicht auf, und der naechste Takt
                // versucht es erneut. Bis dahin fehlt ein Endstand - was ein Betreiber im
                // Protokoll findet und niemand still verliert.
                logger.log(
                        Level.WARNING,
                        "[statistics] season " + season.key() + " could not be closed",
                        failure);
            }
        }
        return closed;
    }

    /** @return ob in diesem Durchlauf tatsächlich abgeschlossen wurde */
    private boolean close(
            SeasonCalendar.Season season, StatisticsConfig settings, ScoreWeights weights) {

        SeasonClosing.Result result =
                SeasonClosing.close(
                        season,
                        totalsOf(season, weights),
                        settings.rewards(),
                        weights,
                        clock.instant());

        if (result.isEmpty()) {
            // Eine Saison ohne Teilnehmer. Sie erzeugt keinen Anspruch und keine Zeile - und der
            // naechste Durchlauf sieht sie wieder als offen. Das ist billiger als eine
            // Platzhalterzeile, die nur sagt "hier war niemand".
            logger.info("[statistics] season " + season.key() + " ended with nobody scored");
            return false;
        }

        // ERST die Ansprueche, DANN der Endstand - und das ist keine Geschmacksfrage. Der Beleg
        // fuer "abgeschlossen" sind die Zeilen im Endstand; waeren sie zuerst da und braeche es
        // dazwischen ab, saehe der naechste Durchlauf die Saison als erledigt und die Ansprueche
        // waeren fuer immer weg. Andersherum ist ein Abbruch folgenlos: die Saison gilt weiter
        // als offen, der naechste Durchlauf schreibt beides, und der Anspruch faellt auf ON
        // CONFLICT DO NOTHING.
        claims.create(result.claims());
        results.freeze(result.standings(), result.weights());

        logger.info(
                "[statistics] season "
                        + season.key()
                        + " closed - "
                        + result.standings().size()
                        + " standings, "
                        + result.claims().size()
                        + " claims");
        return true;
    }

    /**
     * Die Punktzahl je Konto über den Saisonzeitraum.
     *
     * <p>Dieselbe Rechnung wie beim Zwischenstand — {@link SeasonScore} — und ausdrücklich nicht
     * eine zweite: liefe der Endstand nach anderen Regeln als der Stand, den die Spieler die ganze
     * Saison über gesehen haben, wäre die Belohnung eine Überraschung.
     */
    private Map<UUID, Long> totalsOf(SeasonCalendar.Season season, ScoreWeights weights) {
        Map<Aggregation, Map<UUID, Long>> byBoard = counters.season(season.from(), season.to());

        Map<UUID, Map<Aggregation, Long>> byAccount = new LinkedHashMap<>();
        byBoard.forEach(
                (board, values) ->
                        values.forEach(
                                (account, value) ->
                                        byAccount
                                                .computeIfAbsent(
                                                        account, ignored -> new LinkedHashMap<>())
                                                .put(board, value)));

        Map<UUID, Long> totals = new LinkedHashMap<>();
        byAccount.forEach(
                (account, values) -> totals.put(account, SeasonScore.of(values, weights).total()));
        return totals;
    }
}
