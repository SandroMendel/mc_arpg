package rpg.persistence.statistics;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.WeekFields;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import rpg.core.statistics.Aggregation;
import rpg.core.statistics.Leaderboard;
import rpg.core.statistics.LeaderboardCache;
import rpg.core.statistics.Period;
import rpg.core.statistics.ScoreWeights;
import rpg.core.statistics.SeasonCalendar;
import rpg.core.statistics.SeasonScore;
import rpg.core.statistics.SeasonScoreBoard;
import rpg.core.statistics.StatisticsConfig;

/**
 * Eine Auffrischung: Sichten erneuern, beide Quellen lesen, den Speicherstand <b>im Ganzen</b>
 * austauschen.
 *
 * <h2>Ein Takt für alles</h2>
 *
 * <p>Zähler- und Zustandsranglisten werden im selben Durchlauf gefüllt und mit demselben
 * Zeitstempel abgelegt (T075e). Zwei Takte hätten zwei Alter ergeben — und das Fenster müsste
 * erklären, welches der beiden es gerade nennt.
 *
 * <h2>Was ein Fehler hier kostet</h2>
 *
 * <p>Höchstens die Aktualität. Schlägt eine Abfrage fehl, bleibt der <b>vorige</b> Stand stehen,
 * und die Altersangabe im Fenster wächst sichtbar weiter (FR-032). Das ist die richtige Antwort:
 * veraltete Zahlen sind brauchbar, verschwundene nicht — und ein leerer Stand mitten im Betrieb
 * sähe für jeden Spieler aus wie „meine Statistik ist weg".
 */
public final class LeaderboardFill {

    private final LeaderboardCache cache;
    private final LeaderboardRefresh refresh;
    private final JdbcLeaderboardSource counters;
    private final JdbcStateLeaderboardSource states;
    private final Supplier<StatisticsConfig> config;
    private final Function<UUID, String> nameOf;
    private final Logger logger;
    private final Clock clock;

    public LeaderboardFill(
            LeaderboardCache cache,
            LeaderboardRefresh refresh,
            JdbcLeaderboardSource counters,
            JdbcStateLeaderboardSource states,
            Supplier<StatisticsConfig> config,
            Function<UUID, String> nameOf,
            Logger logger,
            Clock clock) {
        this.cache = Objects.requireNonNull(cache, "cache");
        this.refresh = Objects.requireNonNull(refresh, "refresh");
        this.counters = Objects.requireNonNull(counters, "counters");
        this.states = Objects.requireNonNull(states, "states");
        this.config = Objects.requireNonNull(config, "config");
        this.nameOf = Objects.requireNonNull(nameOf, "nameOf");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Ein vollständiger Durchlauf.
     *
     * <p><b>Aus einem Hintergrundfaden zu rufen.</b> Hier wird gerechnet und abgefragt; der Tick
     * hat damit nichts zu tun (FR-031).
     */
    public void refreshNow() {
        try {
            refresh.refreshAll();

            StatisticsConfig settings = config.get();
            int places = settings.leaderboards().places();
            LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
            Instant at = clock.instant();

            Map<LeaderboardCache.Key, Leaderboard> next = new LinkedHashMap<>();

            put(next, counters.allTime(), Period.ALL_TIME, "", places, at);
            put(
                    next,
                    counters.week(
                            today.get(WeekFields.ISO.weekBasedYear()),
                            today.get(WeekFields.ISO.weekOfWeekBasedYear())),
                    Period.WEEK,
                    isoWeekKey(today),
                    places,
                    at);
            put(next, counters.day(today), Period.DAY, today.toString(), places, at);

            Optional<SeasonCalendar.Season> season = settings.seasons().seasonOf(today);
            if (season.isPresent()) {
                Map<Aggregation, Map<UUID, Long>> ofSeason =
                        counters.season(season.get().from(), season.get().to());
                put(next, ofSeason, Period.SEASON, season.get().key(), places, at);
                cache.replaceSeasonScore(
                        scoreBoard(season.get(), ofSeason, settings, places, at));
            } else {
                // Zwischen zwei Saisons gibt es nichts zu gewinnen - und das ist etwas anderes
                // als "noch niemand hat Punkte" (FR-050e).
                cache.replaceSeasonScore(null);
            }

            putStateBoards(next, places, at);

            cache.replace(next, at);
            logger.fine(() -> "[statistics] leaderboards refreshed - " + next.size() + " boards");
        } catch (RuntimeException failure) {
            // Der vorige Stand bleibt stehen. Die Altersangabe im Fenster waechst sichtbar
            // weiter, und das ist die ehrlichere Auskunft als eine leere Liste.
            logger.log(Level.WARNING, "[statistics] leaderboard refresh failed", failure);
        }
    }

    /**
     * Die Saison-Gesamtwertung aus den Saisonwerten (ADR-046, FR-050e).
     *
     * <p>Gerechnet, nicht abgelegt: der <em>Zwischenstand</em> einer laufenden Saison gehört in
     * keine Tabelle ([data-model.md] §3). Erst der Endstand wird eingefroren, und erst dann trägt
     * er seine Gewichtung bei sich.
     */
    private SeasonScoreBoard scoreBoard(
            SeasonCalendar.Season season,
            Map<Aggregation, Map<UUID, Long>> ofSeason,
            StatisticsConfig settings,
            int places,
            Instant at) {

        // Umgedreht: von "je Rangliste alle Konten" auf "je Konto alle Ranglisten".
        Map<UUID, Map<Aggregation, Long>> byAccount = new LinkedHashMap<>();
        ofSeason.forEach(
                (board, values) ->
                        values.forEach(
                                (account, value) ->
                                        byAccount
                                                .computeIfAbsent(
                                                        account, ignored -> new LinkedHashMap<>())
                                                .put(board, value)));

        // Die Aufschlüsselung wird HIER gerechnet und mitgelegt, nicht erst im Fenster: dort
        // laege sie ausserhalb des Speicherstands, und dann brauchte das Oeffnen die Rohwerte -
        // also eine Abfrage, also genau das, was FR-030 ausschliesst (ADR-046).
        ScoreWeights weights = ScoreWeights.from(settings.score());
        Map<UUID, SeasonScore> scores = new LinkedHashMap<>();
        byAccount.forEach((account, values) -> scores.put(account, SeasonScore.of(values, weights)));

        return SeasonScoreBoard.of(season.key(), scores, places, nameOf, at);
    }

    private void put(
            Map<LeaderboardCache.Key, Leaderboard> target,
            Map<Aggregation, Map<UUID, Long>> byBoard,
            Period period,
            String periodKey,
            int places,
            Instant at) {
        byBoard.forEach(
                (board, values) ->
                        target.put(
                                // Der Schluessel im Stand traegt den Zeitraum, NICHT dessen
                                // Auspraegung: der Cache haelt immer die LAUFENDE Woche, den
                                // heutigen Tag, die aktuelle Saison. Legte man unter "2026-W35"
                                // ab, suchte das Fenster unter "" und faende nichts - und weil
                                // ein leerer Stand als "noch nicht aufgefrischt" gilt, saehe der
                                // Fehler wie ein normaler Zustand aus.
                                LeaderboardCache.Key.of(board, period),
                                Leaderboard.of(
                                        board,
                                        period,
                                        // Im Eintrag steht die Auspraegung sehr wohl - das Fenster
                                        // soll sagen koennen, WELCHE Woche gemeint ist.
                                        periodKey,
                                        values,
                                        Map.of(),
                                        places,
                                        nameOf,
                                        at)));
    }

    /**
     * Level und Coins — aus den Beständen, in denen sie ohnehin stehen (ADR-041).
     *
     * <p>Nur {@link Period#ALL_TIME}: ein Zustand hat keine Tages-, Wochen- oder Saisonform
     * (FR-023). Die Fassade weist die anderen drei ohnehin ab; sie hier gar nicht erst anzulegen
     * ist die zweite Hälfte derselben Aussage.
     */
    private void putStateBoards(
            Map<LeaderboardCache.Key, Leaderboard> target, int places, Instant at) {
        Map<UUID, JdbcStateLeaderboardSource.LevelStanding> levels = states.levels();

        Map<UUID, Long> levelValues = new LinkedHashMap<>();
        Map<UUID, Long> xpInLevel = new LinkedHashMap<>();
        levels.forEach(
                (account, standing) -> {
                    levelValues.put(account, (long) standing.level());
                    xpInLevel.put(account, standing.xpInLevel());
                });

        target.put(
                LeaderboardCache.Key.of(Aggregation.LEVEL, Period.ALL_TIME),
                Leaderboard.of(
                        Aggregation.LEVEL,
                        Period.ALL_TIME,
                        "",
                        levelValues,
                        // Der Gleichstandsentscheid ist die XP INNERHALB des Levels (FR-020).
                        xpInLevel,
                        places,
                        account -> nameWithClass(account, levels.get(account)),
                        at));

        target.put(
                LeaderboardCache.Key.of(Aggregation.COINS, Period.ALL_TIME),
                Leaderboard.of(
                        Aggregation.COINS,
                        Period.ALL_TIME,
                        "",
                        states.coins(),
                        Map.of(),
                        places,
                        nameOf,
                        at));
    }

    /** Der Name samt der Klasse <b>genau des</b> Charakters, der den Level hält (FR-021). */
    private String nameWithClass(UUID account, JdbcStateLeaderboardSource.LevelStanding standing) {
        String name = nameOf.apply(account);
        if (name == null) {
            return null;
        }
        return standing == null ? name : name + " (" + standing.characterClass() + ")";
    }

    /** {@code 2026-W35} — der Schlüssel, unter dem eine Woche im Speicherstand liegt. */
    static String isoWeekKey(LocalDate day) {
        return day.get(WeekFields.ISO.weekBasedYear())
                + "-W"
                + String.format("%02d", day.get(WeekFields.ISO.weekOfWeekBasedYear()));
    }
}
