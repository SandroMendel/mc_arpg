package rpg.core.statistics;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * SC-012, SC-023, FR-032a — <b>eine neu erfasste Zählermetrik wird von allein zur Rangliste.</b>
 *
 * <p>Ohne eine Zeile Code und <b>ohne einen Eintrag in der Konfiguration</b>. Das ist die Zusage,
 * die diesen Block billig macht: wer in einem späteren Block etwas Neues zählt, trägt es in
 * {@link MetricRegistry} ein und ist fertig — er schreibt kein SQL, legt keine Sicht an, ergänzt
 * keine YAML-Liste und pflegt keinen Schalter.
 *
 * <p><b>Die Zusage ist leicht zu verlieren, und zwar unbemerkt.</b> Sie bricht nicht mit einem
 * Fehler, sondern mit einer Rangliste, die es einfach nicht gibt — und das fällt erst jemandem
 * auf, der sie sucht. Deshalb prüft dieser Test die vier Stellen, an denen sie hängt, einzeln:
 *
 * <ol>
 *   <li>jede öffentliche Zählermetrik hat eine Aggregation,
 *   <li>jede Aggregation hat alle vier Zeiträume (soweit ihre Art sie zulässt),
 *   <li>die Konfiguration listet keine Ranglisten — sie kann also keine vergessen,
 *   <li>und die Textprüfung beim Start wächst mit dem Verzeichnis mit.
 * </ol>
 */
class NewMetricAppearsWithoutConfigTest {

    private static final Instant AT = Instant.parse("2026-08-30T12:00:00Z");

    @Test
    @DisplayName("SC-023 - jede oeffentliche Zaehlermetrik hat eine Rangliste")
    void everyPublicCounterMetricHasABoard() {
        List<String> withoutBoard = new ArrayList<>();

        for (Metric metric : MetricRegistry.all().values()) {
            if (metric.visibility() != MetricVisibility.PUBLIC || metric.kind() == MetricKind.STATE) {
                continue;
            }
            boolean aggregated =
                    java.util.Arrays.stream(Aggregation.values())
                            .anyMatch(board -> board.source() == metric);
            if (!aggregated) {
                withoutBoard.add(metric.key());
            }
        }

        // Wer eine Metrik eintraegt und die Aggregation vergisst, erfaehrt es HIER - und nicht von
        // einem Spieler, der eine Rangliste sucht, die es nie gab.
        assertThat(withoutBoard)
                .as("eine erfasste, oeffentliche Zaehlermetrik ohne Rangliste (FR-032a)")
                .isEmpty();
    }

    @Test
    @DisplayName("FR-032a - und sie hat sie in ALLEN VIER Zeitraeumen")
    void anditHasItInAllFourPeriods() {
        LeaderboardCache cache = LeaderboardCache.empty();
        cache.replace(everyBoardInEveryFittingPeriod(), AT);
        Leaderboards leaderboards = Leaderboards.backedBy(cache);

        for (Aggregation board : Aggregation.values()) {
            if (board.visibility() != MetricVisibility.PUBLIC || board.isState()) {
                continue;
            }
            for (Period period : Period.values()) {
                assertThat(leaderboards.board(board, period))
                        .as(board.key() + " / " + period)
                        .isPresent();
            }
        }
    }

    @Test
    @DisplayName("SC-012 - die Konfiguration listet keine Ranglisten, kann also keine vergessen")
    void theconfigListsNoBoardsSoItCannotForgetOne() throws IOException {
        String leaderboardSection = sectionOf(Files.readString(shippedConfig()), "leaderboards");

        // Der entscheidende Punkt ist eine Abwesenheit: der Ranglisten-Abschnitt sagt, WIE OFT
        // aufgefrischt wird und WIE VIELE Plaetze zu sehen sind - aber nirgends, WELCHE
        // Ranglisten es gibt. Gaebe es diese Aufzaehlung, waere sie die eine Stelle, an der eine
        // neue Metrik haengen bliebe, und sie bliebe still haengen.
        for (Aggregation board : Aggregation.values()) {
            assertThat(leaderboardSection)
                    .as("leaderboards: zaehlt " + board.key() + " auf")
                    .doesNotContain(board.key());
        }

        // Die Gewichtung nennt zwar Ranglisten - aber nur die, die PUNKTE geben. Eine Rangliste
        // ohne Gewicht existiert trotzdem; sie traegt nur nichts zur Saisonwertung bei.
        StatisticsConfig withoutWeights =
                new StatisticsConfig(
                        new StatisticsConfig.Capture(0.1, java.time.Duration.ofMinutes(5)),
                        new StatisticsConfig.Leaderboards(java.time.Duration.ofMinutes(5), 10),
                        SeasonCalendar.of(
                                List.of(
                                        new SeasonCalendar.Season(
                                                "2026-q3",
                                                java.time.LocalDate.of(2026, 7, 1),
                                                java.time.LocalDate.of(2026, 9, 30)))),
                        new StatisticsConfig.Score(Map.of(Aggregation.MOB_KILLS, 1.0)),
                        Map.of(),
                        java.util.Optional.empty());

        assertThat(ScoreWeights.from(withoutWeights.score()).weightOf(Aggregation.DAMAGE_MAX))
                .as("kein Gewicht - aber das ist keine Aussage darueber, ob es die Liste gibt")
                .isZero();
    }

    @Test
    @DisplayName("die Textpruefung beim Start waechst mit dem Verzeichnis mit")
    void thestartupTextCheckGrowsWithTheRegistry() {
        List<rpg.core.message.MessageKey> keys = StatisticsMessageKeys.all();

        // Sonst waere der Preis einer neuen Metrik ein roher Schluesselname mitten in der
        // Oberflaeche - und zwar erst bei dem Spieler, der das Fenster oeffnet.
        for (Aggregation board : Aggregation.values()) {
            assertThat(keys).contains(StatisticsMessageKeys.boardName(board));
            assertThat(keys).contains(StatisticsMessageKeys.boardHint(board));
        }
        for (Period period : Period.values()) {
            assertThat(keys).contains(StatisticsMessageKeys.periodName(period));
        }
    }

    @Test
    @DisplayName("die Sichten nennen keine SUMMEN-Metrik beim Namen - die kommen von allein dazu")
    void theviewsNameNoSumMetric() throws IOException {
        String sql = Files.readString(viewMigration());

        for (Metric metric : MetricRegistry.all().values()) {
            if (metric.kind() != MetricKind.SUM) {
                continue;
            }
            // Summieren ist der Normalfall der Sicht. Stuende hier auch nur ein Schluessel,
            // waere die naechste Zaehlermetrik eine Migration - und die Zusage waere gebrochen,
            // ohne dass etwas rot wird.
            assertThat(sql)
                    .as("die Sicht nennt " + metric.key() + " beim Namen")
                    .doesNotContain("'" + metric.key() + "'");
        }
    }

    @Test
    @DisplayName("die GRENZE der Zusage: eine MAX-Metrik steht sehr wohl in der Sicht")
    void thelimitOfThePromiseAmaxMetricIsNamedInTheView() throws IOException {
        String sql = Files.readString(viewMigration());

        List<String> maxMetrics =
                MetricRegistry.all().values().stream()
                        .filter(metric -> metric.kind() == MetricKind.MAX)
                        .map(Metric::key)
                        .toList();

        // HIER endet FR-032a, und das ist keine Schlamperei, sondern eine Eigenschaft von SQL:
        // die Sicht muss wissen, welche Familie MAXIMIERT statt zu summieren, und das steht in
        // keiner Spalte. Eine zweite MAX-Metrik braucht deshalb eine Migration.
        //
        // Dieser Test haelt die Grenze fest, statt sie zu verschweigen: wer eine MAX-Metrik
        // eintraegt und die Sicht vergisst, bekommt hier einen Fehlschlag - und nicht eine
        // Rangliste, die still die falsche Rechnung macht. Eine summierte Hoechstschadenzahl
        // saehe naemlich wie eine plausible Zahl aus.
        for (String key : maxMetrics) {
            assertThat(sql)
                    .as(
                            key
                                    + " ist eine MAX-Metrik und muss in der Sicht stehen -"
                                    + " sonst wird sie summiert")
                    .contains("'" + key + "'");
        }

        // Und umgekehrt: was in der Liste der Sicht steht, ist auch wirklich eine MAX-Metrik.
        for (String named : maxListOf(sql)) {
            assertThat(maxMetrics)
                    .as("die Sicht maximiert " + named + ", das Verzeichnis kennt das nicht so")
                    .contains(named);
        }
    }

    // ------------------------------------------------------------------ Gerüst

    /** Was {@code LeaderboardFill} anlegt: jede Rangliste in jedem Zeitraum, der zu ihr passt. */
    private static Map<LeaderboardCache.Key, Leaderboard> everyBoardInEveryFittingPeriod() {
        Map<LeaderboardCache.Key, Leaderboard> boards = new LinkedHashMap<>();
        for (Aggregation board : Aggregation.values()) {
            for (Period period : Period.values()) {
                if (!period.fits(board.source().kind())) {
                    continue;
                }
                boards.put(
                        LeaderboardCache.Key.of(board, period),
                        Leaderboard.of(
                                board,
                                period,
                                "",
                                Map.of(UUID.randomUUID(), 1L),
                                Map.of(),
                                10,
                                account -> "Somebody",
                                AT));
            }
        }
        return boards;
    }

    /** Die Metrikschlüssel aus den {@code IN (...)}-Listen der Sicht. */
    private static List<String> maxListOf(String sql) {
        List<String> named = new ArrayList<>();
        java.util.regex.Matcher inList =
                java.util.regex.Pattern.compile("split_part\\(d\\.metric, '\\.', 1\\) IN \\(([^)]*)\\)")
                        .matcher(sql);
        while (inList.find()) {
            for (String raw : inList.group(1).split(",")) {
                named.add(raw.trim().replace("'", ""));
            }
        }
        return named;
    }

    /**
     * Ein YAML-Abschnitt der obersten Ebene, <b>ohne Kommentare</b>.
     *
     * <p>Die Kommentare müssen weg, bevor gesucht wird: {@code statistics.yml} erklärt in ihnen
     * ausführlich, worum es geht, und nennt dabei Ranglisten beim Namen. Ein Wächter, der eine
     * Erklärung für eine Konfiguration hält, meldet den Text und nicht die Sache.
     */
    private static String sectionOf(String yaml, String key) {
        StringBuilder body = new StringBuilder();
        boolean inside = false;
        for (String line : yaml.split("\r?\n")) {
            String code = line.replaceAll("#.*$", "");
            if (code.startsWith(key + ":")) {
                inside = true;
                continue;
            }
            if (inside && !code.isBlank() && !code.startsWith(" ")) {
                break;
            }
            if (inside) {
                body.append(code).append('\n');
            }
        }
        return body.toString();
    }

    private static Path viewMigration() {
        return repositoryRoot()
                .resolve("rpg-persistence/src/main/resources/db/migration")
                .resolve("V12_1__statistic_leaderboard_views.sql");
    }

    private static Path shippedConfig() {
        return repositoryRoot().resolve("rpg-plugin/src/main/resources/statistics.yml");
    }

    /** Von {@code rpg-core} aus eine Ebene hoch — dasselbe Muster wie in den anderen Wächtern. */
    private static Path repositoryRoot() {
        Path here = Path.of("").toAbsolutePath();
        return here.getFileName().toString().startsWith("rpg-") ? here.getParent() : here;
    }
}
