package rpg.core.statistics;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * FR-026, FR-027 — die Grenzen der Zeiträume.
 *
 * <p>Die Woche beginnt am <b>Montag</b> und folgt ISO-8601, der Tag um <b>00:00 UTC</b>, und ein
 * Tag gehört zu <b>genau einer</b> Saison. Alle drei Zusagen haben gemeinsam, dass ihr Bruch keine
 * Ausnahme wirft, sondern eine leicht andere Zahl liefert — die Sorte Fehler, die man erst
 * bemerkt, wenn eine Rangliste am Montagmorgen zurückspringt.
 */
class PeriodBoundariesTest {

    @Test
    @DisplayName("FR-026 - die Woche beginnt montags und endet sonntags")
    void theWeekStartsOnMondayAndEndsOnSunday() {
        // Mittwoch, 2026-08-26
        LocalDate wednesday = LocalDate.of(2026, 8, 26);

        assertThat(Period.startOfWeek(wednesday)).isEqualTo(LocalDate.of(2026, 8, 24));
        assertThat(Period.endOfWeek(wednesday)).isEqualTo(LocalDate.of(2026, 8, 30));
    }

    @Test
    @DisplayName("FR-026 - ein Montag ist bereits der Anfang seiner Woche, kein Ende der vorigen")
    void aMondayIsAlreadyTheStartOfItsOwnWeek() {
        LocalDate monday = LocalDate.of(2026, 8, 24);

        assertThat(Period.startOfWeek(monday)).isEqualTo(monday);
        assertThat(Period.endOfWeek(monday)).isEqualTo(LocalDate.of(2026, 8, 30));
    }

    @Test
    @DisplayName("FR-026 - ein Sonntag gehoert zur ablaufenden Woche, nicht zur naechsten")
    void aSundayBelongsToTheWeekThatIsEnding() {
        LocalDate sunday = LocalDate.of(2026, 8, 30);

        // Genau hier trennen sich ISO und die sonntagsbeginnende Zaehlweise: nach ISO ist dies der
        // siebte Tag derselben Woche, nach der anderen der erste einer neuen.
        assertThat(Period.startOfWeek(sunday)).isEqualTo(LocalDate.of(2026, 8, 24));
        assertThat(Period.endOfWeek(sunday)).isEqualTo(sunday);
    }

    @Test
    @DisplayName("FR-026 - eine Woche ueber den Jahreswechsel bleibt zusammen")
    void aWeekAcrossTheTurnOfTheYearStaysTogether() {
        // Donnerstag, 2026-12-31 - die ISO-Woche laeuft ins neue Jahr weiter.
        LocalDate lastDayOfYear = LocalDate.of(2026, 12, 31);

        assertThat(Period.startOfWeek(lastDayOfYear)).isEqualTo(LocalDate.of(2026, 12, 28));
        assertThat(Period.endOfWeek(lastDayOfYear)).isEqualTo(LocalDate.of(2027, 1, 3));
    }

    @Test
    @DisplayName("FR-027 - ein Tag gehoert zu genau einer Saison")
    void aDayBelongsToExactlyOneSeason() {
        SeasonCalendar calendar =
                SeasonCalendar.of(
                        List.of(
                                new SeasonCalendar.Season(
                                        "2026-q3",
                                        LocalDate.of(2026, 7, 1),
                                        LocalDate.of(2026, 9, 30)),
                                new SeasonCalendar.Season(
                                        "2026-q4",
                                        LocalDate.of(2026, 10, 1),
                                        LocalDate.of(2026, 12, 31))));

        // Der letzte Tag der einen und der erste der naechsten - die Stelle, an der ein
        // ausschliessendes Ende einen Tag verschlucken wuerde.
        assertThat(calendar.seasonOf(LocalDate.of(2026, 9, 30)))
                .get()
                .extracting(SeasonCalendar.Season::key)
                .isEqualTo("2026-q3");
        assertThat(calendar.seasonOf(LocalDate.of(2026, 10, 1)))
                .get()
                .extracting(SeasonCalendar.Season::key)
                .isEqualTo("2026-q4");
    }

    @Test
    @DisplayName("FR-027 - ausserhalb jeder Saison gehoert ein Tag zu keiner")
    void outsideEverySeasonADayBelongsToNone() {
        SeasonCalendar calendar =
                SeasonCalendar.of(
                        List.of(
                                new SeasonCalendar.Season(
                                        "2026-q3",
                                        LocalDate.of(2026, 7, 1),
                                        LocalDate.of(2026, 9, 30))));

        assertThat(calendar.seasonOf(LocalDate.of(2026, 6, 30))).isEmpty();
        assertThat(calendar.seasonOf(LocalDate.of(2026, 10, 1))).isEmpty();
    }

    @Test
    @DisplayName("FR-023 - ein Zustandswert kennt nur den Allzeit-Zeitraum")
    void aStateValueOnlyKnowsAllTime() {
        assertThat(Period.ALL_TIME.fits(MetricKind.STATE)).isTrue();
        assertThat(Period.DAY.fits(MetricKind.STATE)).isFalse();
        assertThat(Period.WEEK.fits(MetricKind.STATE)).isFalse();
        assertThat(Period.SEASON.fits(MetricKind.STATE)).isFalse();

        for (Period period : Period.values()) {
            assertThat(period.fits(MetricKind.SUM)).isTrue();
            assertThat(period.fits(MetricKind.MAX)).isTrue();
        }
    }
}
