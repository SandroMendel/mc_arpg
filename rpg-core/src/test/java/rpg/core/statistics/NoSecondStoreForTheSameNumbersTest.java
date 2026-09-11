package rpg.core.statistics;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * FR-002 — <b>kein zweiter Bestand für Zahlen, die schon irgendwo stehen.</b>
 *
 * <p>B11 hat für seine gleichlautende Zusage (FR-079 dort) denselben Wächter gebaut, und aus
 * demselben Grund: die eigene Ablage ist immer der schnellere Weg. Sie zu bauen dauert eine
 * Stunde, die fremde zu verstehen einen Nachmittag — und die zweite Ablage verhält sich beim
 * nächsten Balancing anders als die erste, ohne dass irgendwo etwas rot wird.
 *
 * <p>Für B12 ist die Versuchung besonders groß, weil der Block <em>aus</em> Zahlen besteht. Die
 * Tagestabelle {@code player_statistic_daily} gehört aber B02, ebenso ihr Repository und ihr Platz
 * in {@code FlushCycle.WRITE_ORDER}. B12 schließt sich an — es baut nicht nach.
 *
 * <p><b>Ohne diesen Test stünde die Regel nur in zwei {@code package-info}</b>, und die liest
 * niemand, während er eine Klasse hinzufügt.
 */
class NoSecondStoreForTheSameNumbersTest {

    /** Die beiden Tabellen, die dieser Block wirklich neu anlegt (data-model.md §1.3, §1.4). */
    private static final List<String> ALLOWED_NEW_TABLES =
            List.of("season_result", "season_reward_claim");

    private static final Pattern CREATE_TABLE =
            Pattern.compile("create\\s+table\\s+(?:if\\s+not\\s+exists\\s+)?(?:rpg\\.)?([a-z_]+)");

    private static final Pattern AGGREGATE_TYPE = Pattern.compile("AggregateType\\.([A-Z_]+)");

    @Test
    @DisplayName("FR-002 - die Wanderung dieses Blocks legt nur die beiden Saisontabellen an")
    void theBlocksMigrationsCreateOnlyTheTwoSeasonTables() throws IOException {
        List<String> created = new ArrayList<>();
        for (Path migration : blockMigrations()) {
            Matcher matcher =
                    CREATE_TABLE.matcher(Files.readString(migration).toLowerCase(Locale.ROOT));
            while (matcher.find()) {
                created.add(matcher.group(1));
            }
        }

        assertThat(created)
                .as(
                        "eine dritte Tabelle in V12_* waere eine zweite Ablage fuer Zahlen, die"
                                + " bereits in player_statistic_daily stehen (FR-002)")
                .allMatch(ALLOWED_NEW_TABLES::contains, "eine der beiden Saisontabellen");
    }

    /**
     * Was ein zweiter Schreibweg auf die Tagestabelle wäre — <b>Lesen ist keiner.</b>
     *
     * <p>Diese Unterscheidung ist beim Bau von Phase 4 entstanden, und zwar dadurch, dass die
     * erste Fassung dieses Tests <em>jede</em> Erwähnung der Tabelle verboten hat. Angeschlagen
     * hat sie beim Saisonstand, der die Rohtabelle liest, weil eine Materialized View keine
     * Parameter kennt (ADR-049).
     *
     * <p><b>Das war ein Fehlalarm, und die Schärfung ist keine Ausnahme.</b> FR-002 verbietet
     * einen zweiten <em>Bestand</em> — eine zweite Stelle, an der dieselben Zahlen leben und
     * auseinanderdriften können. Ein {@code SELECT} legt nichts an; ein {@code INSERT},
     * {@code UPDATE} oder {@code DELETE} außerhalb von B02s Repository legte einen zweiten
     * Schreibweg an, und der ist genau das Verbotene.
     */
    private static final List<String> WRITE_VERBS =
            List.of("insert into", "update", "delete from", "truncate");

    @Test
    @DisplayName("FR-002 - der Block SCHREIBT die Tagestabelle nicht selbst (lesen darf er)")
    void theBlockDoesNotWriteTheDailyTableItself() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path source : SourceGuard.statisticsSources()) {
            String code = SourceGuard.codeOnly(Files.readString(source)).toLowerCase(Locale.ROOT);
            for (String verb : WRITE_VERBS) {
                if (mentionsTableAfter(code, verb)) {
                    violations.add(source.getFileName() + ": " + verb + " ... player_statistic_daily");
                }
            }
        }

        assertThat(violations)
                .as(
                        "der Schreibweg auf die Tagestabelle gehoert B02 - B12 geht ueber"
                                + " StatisticsRepository. Lesen ist erlaubt und beim Saisonstand"
                                + " noetig (ADR-049)")
                .isEmpty();
    }

    /** Ob nach einem Schreibverb im selben Statement die Tagestabelle genannt wird. */
    private static boolean mentionsTableAfter(String code, String verb) {
        int at = code.indexOf(verb);
        while (at >= 0) {
            int end = Math.min(code.length(), at + verb.length() + 120);
            if (code.substring(at, end).contains("player_statistic_daily")) {
                return true;
            }
            at = code.indexOf(verb, at + 1);
        }
        return false;
    }

    @Test
    @DisplayName("R10 - es entsteht kein neuer AggregateType")
    void noNewAggregateTypeIsIntroduced() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path source : SourceGuard.statisticsSources()) {
            Matcher matcher =
                    AGGREGATE_TYPE.matcher(SourceGuard.codeOnly(Files.readString(source)));
            while (matcher.find()) {
                if (!matcher.group(1).equals("STATISTICS")) {
                    violations.add(source.getFileName() + ": AggregateType." + matcher.group(1));
                }
            }
        }

        assertThat(violations)
                .as(
                        "die dreifache Registrierung aus ADR-015 ist fuer die Statistik laengst"
                                + " erledigt - ein zweiter Typ waere ein zweiter Schreibweg (R10)")
                .isEmpty();
    }

    private static List<Path> blockMigrations() throws IOException {
        Path dir =
                SourceGuard.repositoryRoot()
                        .resolve("rpg-persistence/src/main/resources/db/migration");
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> walk = Files.list(dir)) {
            return walk.filter(path -> path.getFileName().toString().startsWith("V12_")).toList();
        }
    }
}
