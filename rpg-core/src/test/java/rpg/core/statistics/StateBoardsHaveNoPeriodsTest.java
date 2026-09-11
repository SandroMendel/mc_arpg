package rpg.core.statistics;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * FR-023, contracts/stats-api.md §2 — <b>ein Zustandswert kennt keinen Zeitraum außer Allzeit.</b>
 *
 * <p>Und er wird <b>abgewiesen</b>, nicht auf den aktuellen Stand umgedeutet. Der Unterschied
 * entscheidet, ob ein Fehler sichtbar ist: deutete man „Level diese Woche" freundlich als „Level
 * jetzt" um, zeigte das Fenster vier Zeiträume mit identischem Inhalt. Niemand hielte das für
 * einen Fehler — alle hielten es für eine kaputte Wochenauswertung, und der wahre Grund (es gibt
 * keine) käme nie zur Sprache.
 *
 * <p><b>Die Ansicht bietet für sie deshalb erst gar keinen Zeitraum an.</b> Diese Prüfung hier ist
 * das Netz darunter, für den Fall, dass eine spätere Ansicht es doch tut.
 */
class StateBoardsHaveNoPeriodsTest {

    @Test
    @DisplayName("FR-023 - eine Zustandsrangliste antwortet nur auf ALL_TIME")
    void astateBoardAnswersOnlyForAllTime() {
        LeaderboardCache cache = LeaderboardCache.empty();
        UUID player = UUID.randomUUID();

        cache.replace(
                Map.of(
                        LeaderboardCache.Key.of(Aggregation.LEVEL, Period.ALL_TIME),
                        Leaderboard.of(
                                Aggregation.LEVEL,
                                Period.ALL_TIME,
                                "",
                                Map.of(player, 42L),
                                10,
                                Map.of(),
                                Instant.now())),
                Instant.now());

        Leaderboards boards = Leaderboards.backedBy(cache);

        assertThat(boards.board(Aggregation.LEVEL, Period.ALL_TIME)).isPresent();
        assertThat(boards.board(Aggregation.LEVEL, Period.DAY)).isEmpty();
        assertThat(boards.board(Aggregation.LEVEL, Period.WEEK)).isEmpty();
        assertThat(boards.board(Aggregation.LEVEL, Period.SEASON)).isEmpty();
    }

    @Test
    @DisplayName("FR-023 - dasselbe gilt fuer die Coins")
    void thesameHoldsForCoins() {
        Leaderboards boards = Leaderboards.backedBy(LeaderboardCache.empty());

        assertThat(boards.board(Aggregation.COINS, Period.SEASON)).isEmpty();
    }

    @Test
    @DisplayName("eine Zaehlerrangliste kennt dagegen alle vier Zeitraeume")
    void acounterBoardKnowsAllFourPeriods() {
        for (Period period : Period.values()) {
            assertThat(period.fits(Aggregation.MOB_KILLS.source().kind()))
                    .as("%s passt zu Kills", period)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("ADR-046 - eine Zustandsrangliste traegt niemals eine Gewichtung")
    void astateBoardNeverCarriesAWeight() {
        // Der Riegel sass im ersten Entwurf in der ABWESENHEIT dieser Eintraege. Das ging nicht
        // auf, weil FR-030 auch fuer diese beiden Listen gilt und sie deshalb im selben
        // Speicherstand liegen muessen. Jetzt steht die Regel ausdruecklich da - und dieser Test
        // haelt sie fest, denn eine ausdrueckliche Regel kann man aendern, eine Abwesenheit nicht
        // versehentlich.
        assertThat(Aggregation.LEVEL.scoreable()).isFalse();
        assertThat(Aggregation.COINS.scoreable()).isFalse();
        assertThat(Aggregation.LEVEL.isState()).isTrue();

        assertThat(Aggregation.MOB_KILLS.scoreable()).isTrue();
        assertThat(Aggregation.BOSS_KILLS.scoreable()).isTrue();
    }

    @Test
    @DisplayName("die private Onlinezeit traegt ebenfalls keine Gewichtung, aus anderem Grund")
    void theprivateOnlineTimeCarriesNoWeightEither() {
        assertThat(Aggregation.PLAYTIME_ONLINE.scoreable()).isFalse();
        assertThat(Aggregation.PLAYTIME_ONLINE.isState())
                .as("nicht weil sie ein Zustand waere, sondern weil sie privat ist")
                .isFalse();
    }
}
