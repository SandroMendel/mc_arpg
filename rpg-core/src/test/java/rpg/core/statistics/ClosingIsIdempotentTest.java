package rpg.core.statistics;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * FR-058, SC-009 — <b>war der Server über das Saisonende hinweg aus, wird der Abschluss beim Start
 * nachgeholt — genau einmal.</b>
 *
 * <p>Das ist der Normalfall, nicht die Ausnahme: ein Quartalsende fällt selten auf einen Moment,
 * in dem der Server gerade läuft. Ein Abschluss, der nur bei laufendem Server greift, hätte also
 * meistens gar nicht stattgefunden — und niemand hätte es gemerkt, weil eine Saison ohne Endstand
 * aussieht wie eine, in der eben niemand etwas erreicht hat.
 *
 * <p><b>Genau einmal</b> ist die zweite Hälfte und die schwierigere: der Start prüft bei jedem
 * Hochfahren, und ohne einen Beleg für „schon gelaufen" liefe der Abschluss bei jedem Neustart
 * erneut — mit neuen Punktzahlen, weil inzwischen weitergespielt wurde. Der Beleg sind die Zeilen
 * in {@code season_result}.
 */
class ClosingIsIdempotentTest {

    private static final Instant NOW = Instant.parse("2026-10-05T12:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 5);

    @Test
    @DisplayName("FR-058 - eine abgelaufene Saison ohne Endstand ist faellig")
    void anexpiredSeasonWithoutAResultIsDue() {
        List<SeasonCalendar.Season> due =
                SeasonClosing.due(calendar(), TODAY, seasonKey -> false);

        assertThat(due).extracting(SeasonCalendar.Season::key).containsExactly("2026-q3");
    }

    @Test
    @DisplayName("FR-058 - dieselbe Saison mit Endstand ist NICHT mehr faellig")
    void thesameSeasonWithAResultIsNoLongerDue() {
        Set<String> closed = new HashSet<>(Set.of("2026-q3"));

        assertThat(SeasonClosing.due(calendar(), TODAY, closed::contains)).isEmpty();
    }

    @Test
    @DisplayName("SC-009 - die laufende Saison wird NICHT abgeschlossen")
    void therunningSeasonIsNotClosed() {
        // Der 5. Oktober liegt in 2026-q4. Sie abzuschliessen hiesse, mitten in einer laufenden
        // Saison Belohnungen zu vergeben - und den Rest des Quartals umsonst zu spielen.
        assertThat(SeasonClosing.due(calendar(), TODAY, seasonKey -> false))
                .extracting(SeasonCalendar.Season::key)
                .doesNotContain("2026-q4");
    }

    @Test
    @DisplayName("FR-058 - war der Server ein halbes Jahr aus, werden BEIDE Saisons nachgeholt")
    void iftheServerWasOffForHalfAYearBothSeasonsAreCaughtUp() {
        // Ein halbes Jahr spaeter: q3 und q4 sind beide vorbei.
        List<SeasonCalendar.Season> due =
                SeasonClosing.due(calendar(), LocalDate.of(2027, 2, 1), seasonKey -> false);

        assertThat(due)
                .as("die aeltere zu ueberspringen hiesse, ihre Belohnungen verfallen zu lassen")
                .extracting(SeasonCalendar.Season::key)
                .containsExactly("2026-q3", "2026-q4");
    }

    @Test
    @DisplayName("SC-009 - ein zweiter Durchlauf findet nichts mehr zu tun")
    void asecondRunFindsNothingLeftToDo() {
        Set<String> closed = new HashSet<>();

        // Erster Start: schliesst ab und merkt sich das ueber die geschriebenen Zeilen.
        List<SeasonCalendar.Season> first = SeasonClosing.due(calendar(), TODAY, closed::contains);
        first.forEach(season -> closed.add(season.key()));

        // Zweiter Start, kurz darauf.
        assertThat(SeasonClosing.due(calendar(), TODAY, closed::contains)).isEmpty();
    }

    @Test
    @DisplayName("der Endstand kuert einen Ersten, und der bekommt den Anspruch")
    void thestandingCrownsAFirstAndHeGetsTheClaim() {
        UUID winner = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        SeasonClosing.Result result =
                SeasonClosing.close(
                        season("2026-q3", "2026-07-01", "2026-09-30"),
                        Map.of(winner, 900L, second, 400L),
                        Map.of(1, reward(5_000L)),
                        weights(),
                        NOW);

        assertThat(result.standings()).extracting(SeasonStanding::playerId)
                .containsExactly(winner, second);
        assertThat(result.claims()).hasSize(1);
        assertThat(result.claims().get(0).playerId()).isEqualTo(winner);
        assertThat(result.claims().get(0).isOpen()).isTrue();
    }

    @Test
    @DisplayName("ein Platz ohne konfigurierte Belohnung steht im Endstand, bekommt aber nichts")
    void aplaceWithoutAConfiguredRewardIsRankedButUnrewarded() {
        UUID winner = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        SeasonClosing.Result result =
                SeasonClosing.close(
                        season("2026-q3", "2026-07-01", "2026-09-30"),
                        Map.of(winner, 900L, second, 400L),
                        Map.of(1, reward(5_000L)),
                        weights(),
                        NOW);

        // Die Platzierung ist die Ehre, der Anspruch waere der Preis.
        assertThat(result.standings()).hasSize(2);
        assertThat(result.claims()).extracting(RewardClaim::playerId).doesNotContain(second);
    }

    @Test
    @DisplayName("wer null Punkte hat, steht gar nicht im Endstand")
    void someoneWithZeroPointsIsNotInTheStandingAtAll() {
        UUID active = UUID.randomUUID();
        UUID idle = UUID.randomUUID();

        SeasonClosing.Result result =
                SeasonClosing.close(
                        season("2026-q3", "2026-07-01", "2026-09-30"),
                        Map.of(active, 10L, idle, 0L),
                        Map.of(),
                        weights(),
                        NOW);

        // Eine Saison, in der jemand nichts getan hat, hat ihn nicht "auf dem letzten Platz" -
        // sie hat ihn gar nicht.
        assertThat(result.standings()).extracting(SeasonStanding::playerId).containsExactly(active);
    }

    private static SeasonCalendar calendar() {
        return SeasonCalendar.of(
                List.of(
                        season("2026-q3", "2026-07-01", "2026-09-30"),
                        season("2026-q4", "2026-10-01", "2026-12-31")));
    }

    private static SeasonCalendar.Season season(String key, String from, String to) {
        return new SeasonCalendar.Season(key, LocalDate.parse(from), LocalDate.parse(to));
    }

    private static StatisticsConfig.Reward reward(long coins) {
        return new StatisticsConfig.Reward(coins, List.of());
    }

    private static ScoreWeights weights() {
        return new ScoreWeights(Map.of(Aggregation.MOB_KILLS, 1.0));
    }
}
