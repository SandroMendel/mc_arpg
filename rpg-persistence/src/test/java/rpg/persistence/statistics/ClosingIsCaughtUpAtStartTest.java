package rpg.persistence.statistics;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.persistence.FlushReason;
import rpg.core.persistence.PlayerState;
import rpg.core.statistics.Aggregation;
import rpg.core.statistics.RewardClaim;
import rpg.core.statistics.SeasonCalendar;
import rpg.core.statistics.SeasonStanding;
import rpg.core.statistics.StatisticsConfig;
import rpg.persistence.support.PersistenceHarness;
import rpg.persistence.support.PostgresContainer;

/**
 * FR-058, SC-009 — <b>war der Server über das Saisonende hinweg aus, wird der Abschluss beim Start
 * nachgeholt: genau einmal.</b>
 *
 * <p>Ein Quartalsende fällt selten auf einen Moment, in dem der Server gerade läuft. Die
 * Nachholung ist deshalb der Normalfall und nicht die Ausnahme — und weil der Job auch im
 * Auffrischungstakt mitläuft, muss ein zweiter Durchlauf folgenlos bleiben. Sonst bekäme derselbe
 * Spieler alle fünf Minuten einen neuen Anspruch auf dieselbe Belohnung.
 *
 * <p><b>Der Beleg dafür, dass abgeschlossen wurde, sind die Zeilen im Endstand</b>, nicht ein
 * Zustandsfeld — deshalb prüft dieser Test nach dem zweiten Durchlauf die Zeilen und nicht einen
 * Rückgabewert.
 */
class ClosingIsCaughtUpAtStartTest {

    private static final LocalDate TODAY = LocalDate.now(ZoneOffset.UTC);
    private static final String ENDED = "2026-past";
    private static final String RUNNING = "2026-now";

    private PersistenceHarness harness;
    private UUID first;
    private UUID second;

    @BeforeEach
    void setUp() throws Exception {
        PostgresContainer.resetSchema();
        harness = new PersistenceHarness();
        first = UUID.randomUUID();
        second = UUID.randomUUID();
        harness.playerStates.put(PlayerState.initial(first, Instant.now()));
        harness.playerStates.put(PlayerState.initial(second, Instant.now()));
        harness.flushCycle.flushNow(FlushReason.INTERVAL).get();
    }

    @AfterEach
    void tearDown() {
        if (harness != null) {
            harness.close();
        }
    }

    @Test
    @DisplayName("FR-058 - eine beendete Saison wird nachgeholt, mit Endstand und Anspruch")
    void aendedSeasonIsCaughtUp() throws Exception {
        insert(first, "mob_kills.rotling", TODAY.minusDays(40), 30);
        insert(second, "mob_kills.rotling", TODAY.minusDays(40), 10);

        assertThat(job().closeDueSeasons()).isEqualTo(1);

        List<SeasonStanding> standings = results().standingsOf(ENDED);
        assertThat(standings).hasSize(2);
        assertThat(standings.get(0).playerId()).isEqualTo(first);
        assertThat(standings.get(0).rank()).isEqualTo(1);
        // 30 Kills x Gewicht 2 = 60 Punkte. Dieselbe Rechnung wie beim Zwischenstand - liefe der
        // Endstand nach anderen Regeln, waere die Belohnung eine Ueberraschung.
        assertThat(standings.get(0).score()).isEqualTo(60L);

        List<RewardClaim> open = claims().openFor(first);
        assertThat(open).hasSize(1);
        assertThat(open.get(0).seasonKey()).isEqualTo(ENDED);
        assertThat(open.get(0).rank()).isEqualTo(1);
    }

    @Test
    @DisplayName("SC-009 - der zweite Durchlauf tut nichts, und ein dritter auch nicht")
    void thesecondRunDoesNothingAndSoDoesAThird() throws Exception {
        insert(first, "mob_kills.rotling", TODAY.minusDays(40), 30);

        SeasonClosingJob job = job();
        assertThat(job.closeDueSeasons()).isEqualTo(1);

        // Der Job laeuft in JEDEM Auffrischungstakt mit. Waere er nicht folgenlos, bekaeme
        // derselbe Spieler alle fuenf Minuten einen neuen Anspruch auf dieselbe Belohnung.
        assertThat(job.closeDueSeasons()).isZero();
        assertThat(job.closeDueSeasons()).isZero();

        assertThat(results().standingsOf(ENDED)).hasSize(1);
        assertThat(claims().openFor(first)).hasSize(1);
    }

    @Test
    @DisplayName("die LAUFENDE Saison wird nicht abgeschlossen - auch nicht an ihrem letzten Tag")
    void therunningSeasonIsNotClosed() throws Exception {
        insert(first, "mob_kills.rotling", TODAY, 5);

        job().closeDueSeasons();

        // Eine Saison am letzten ihrer Tage abzuschliessen naehme jedem den letzten Tag - und
        // zwar genau den, an dem am meisten gespielt wird.
        assertThat(results().isClosed(RUNNING)).isFalse();
        assertThat(claims().openFor(first)).isEmpty();
    }

    @Test
    @DisplayName("eine beendete Saison ohne Teilnehmer erzeugt keinen Anspruch")
    void anemptyEndedSeasonCreatesNoClaim() {
        assertThat(job().closeDueSeasons()).isZero();

        assertThat(results().standingsOf(ENDED)).isEmpty();
        assertThat(claims().openFor(first)).isEmpty();
    }

    @Test
    @DisplayName("FR-039 - ein anonymisiertes Konto steht nicht im Endstand")
    void ananonymisedAccountIsNotInTheStanding() throws Exception {
        insert(first, "mob_kills.rotling", TODAY.minusDays(40), 30);
        insert(second, "mob_kills.rotling", TODAY.minusDays(40), 90);
        anonymise(second);

        job().closeDueSeasons();

        // Der Filter sitzt in der Abfrage, nicht im Abschluss. Faende er hier nicht statt,
        // erschiene ein anonymisiertes Konto ausgerechnet in der Rangliste, die belohnt wird.
        List<SeasonStanding> standings = results().standingsOf(ENDED);
        assertThat(standings).hasSize(1);
        assertThat(standings.get(0).playerId()).isEqualTo(first);
    }

    // ------------------------------------------------------------------ Gerüst

    private SeasonClosingJob job() {
        return new SeasonClosingJob(
                new JdbcLeaderboardSource(harness.pools.loginPool(), kind -> false),
                results(),
                claims(),
                ClosingIsCaughtUpAtStartTest::config,
                Logger.getLogger("test"),
                Clock.systemUTC());
    }

    private JdbcSeasonResultRepository results() {
        return new JdbcSeasonResultRepository(harness.pools.loginPool());
    }

    private JdbcRewardClaimRepository claims() {
        return new JdbcRewardClaimRepository(harness.pools.loginPool());
    }

    private static StatisticsConfig config() {
        return new StatisticsConfig(
                new StatisticsConfig.Capture(0.1, Duration.ofMinutes(5)),
                new StatisticsConfig.Leaderboards(Duration.ofMinutes(5), 10),
                SeasonCalendar.of(
                        List.of(
                                new SeasonCalendar.Season(
                                        ENDED, TODAY.minusDays(90), TODAY.minusDays(1)),
                                new SeasonCalendar.Season(RUNNING, TODAY, TODAY.plusDays(89)))),
                new StatisticsConfig.Score(Map.of(Aggregation.MOB_KILLS, 2.0)),
                Map.of(1, new StatisticsConfig.Reward(5_000L, List.of())),
                Optional.empty());
    }

    private void insert(UUID account, String metric, LocalDate day, long value) throws Exception {
        try (Connection connection = harness.pools.loginPool().getConnection();
                PreparedStatement statement =
                        connection.prepareStatement(
                                "INSERT INTO rpg.player_statistic_daily"
                                        + " (player_id, metric, day, value) VALUES (?, ?, ?, ?)")) {
            statement.setObject(1, account);
            statement.setString(2, metric);
            statement.setDate(3, java.sql.Date.valueOf(day));
            statement.setLong(4, value);
            statement.executeUpdate();
            commit(connection);
        }
    }

    private void anonymise(UUID account) throws Exception {
        try (Connection connection = harness.pools.loginPool().getConnection();
                PreparedStatement statement =
                        connection.prepareStatement(
                                "UPDATE rpg.player_state SET anonymized = TRUE WHERE player_id = ?")) {
            statement.setObject(1, account);
            statement.executeUpdate();
            commit(connection);
        }
    }

    /** Der Pool steht auf {@code autoCommit = false} — ohne das hier wäre nichts geschrieben. */
    private static void commit(Connection connection) throws Exception {
        if (!connection.getAutoCommit()) {
            connection.commit();
        }
    }
}
