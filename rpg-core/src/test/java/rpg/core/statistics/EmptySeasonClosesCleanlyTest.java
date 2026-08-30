package rpg.core.statistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Edge Case — <b>eine Saison ohne Teilnehmer erzeugt keinen Anspruch, und die nächste startet
 * trotzdem.</b>
 *
 * <p>Der Fall ist kein Kuriosum: er trifft jeden frisch aufgesetzten Server, dessen erste Saison
 * schon läuft, bevor der erste Spieler da ist. Wenn der Abschluss daran scheitert — mit einer
 * Ausnahme, einem leeren Endstand, der als „noch nicht abgeschlossen" gilt, oder einem Anspruch
 * für niemanden —, hängt der Server an der ersten Quartalsgrenze seines Lebens.
 *
 * <p>Und das Scheitern sähe nicht nach Saisonabschluss aus, sondern nach einem Startproblem: der
 * eigentliche Grund läge Wochen zurück.
 */
class EmptySeasonClosesCleanlyTest {

    private static final Instant NOW = Instant.parse("2026-10-05T12:00:00Z");

    @Test
    @DisplayName("eine Saison ohne Teilnehmer schliesst ohne Ausnahme ab")
    void aseasonWithoutParticipantsClosesWithoutAnException() {
        assertThatCode(
                        () ->
                                SeasonClosing.close(
                                        season(),
                                        Map.of(),
                                        Map.of(1, reward()),
                                        weights(),
                                        NOW))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("sie erzeugt keinen Endstand und keinen Anspruch")
    void itproducesNoStandingAndNoClaim() {
        SeasonClosing.Result result =
                SeasonClosing.close(season(), Map.of(), Map.of(1, reward()), weights(), NOW);

        assertThat(result.standings()).isEmpty();
        assertThat(result.claims())
                .as("ein Anspruch fuer niemanden waere eine Zeile ohne Empfaenger")
                .isEmpty();
        assertThat(result.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("eine Saison, in der alle null Punkte haben, verhaelt sich genauso")
    void aseasonWhereEveryoneScoredZeroBehavesTheSame() {
        SeasonClosing.Result result =
                SeasonClosing.close(
                        season(),
                        Map.of(
                                java.util.UUID.randomUUID(), 0L,
                                java.util.UUID.randomUUID(), 0L),
                        Map.of(1, reward()),
                        weights(),
                        NOW);

        // Anwesenheit allein kuert keinen Sieger. Waere es anders, gewaenne bei einer leeren
        // Saison, wessen Kontokennung zufaellig vorne steht - der Gleichstandsentscheid.
        assertThat(result.standings()).isEmpty();
        assertThat(result.claims()).isEmpty();
    }

    @Test
    @DisplayName("die naechste Saison ist davon voellig unberuehrt")
    void thenextSeasonIsEntirelyUnaffected() {
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

        assertThat(calendar.seasonOf(LocalDate.of(2026, 10, 5)))
                .get()
                .extracting(SeasonCalendar.Season::key)
                .isEqualTo("2026-q4");
    }

    private static SeasonCalendar.Season season() {
        return new SeasonCalendar.Season(
                "2026-q3", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 9, 30));
    }

    private static StatisticsConfig.Reward reward() {
        return new StatisticsConfig.Reward(5_000L, List.of());
    }

    private static ScoreWeights weights() {
        return new ScoreWeights(Map.of(Aggregation.MOB_KILLS, 1.0));
    }
}
