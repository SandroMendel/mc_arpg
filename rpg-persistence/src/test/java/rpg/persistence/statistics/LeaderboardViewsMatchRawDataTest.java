package rpg.persistence.statistics;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.WeekFields;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.persistence.FlushReason;
import rpg.core.persistence.PlayerState;
import rpg.core.statistics.Aggregation;
import rpg.persistence.support.PersistenceHarness;
import rpg.persistence.support.PostgresContainer;

/**
 * <b>Der eigentliche Beweis, dass die Sichtdefinition stimmt.</b>
 *
 * <p>Eine Materialized View ist Code, den kein Compiler prüft und kein Unit-Test erreicht. Sie
 * kann falsch gruppieren, den falschen Operator anwenden oder Zeilen verlieren — und das Ergebnis
 * sieht in jedem Fall aus wie eine Rangliste. Der einzige belastbare Test ist deshalb der
 * Vergleich mit einer <em>unabhängig</em> gerechneten Aggregation über dieselben Rohdaten.
 *
 * <p>Läuft gegen eine echte PostgreSQL. Gegen eine Attrappe wäre er sinnlos: die Sicht ist genau
 * das, was die Attrappe nicht hat.
 */
class LeaderboardViewsMatchRawDataTest {

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
    @DisplayName("die Allzeitsicht liefert denselben Stand wie eine direkte Aggregation")
    void thealltimeViewMatchesADirectAggregation() throws Exception {
        harness.statistics.increment(first, "mob_kills.rotling", 3);
        harness.statistics.increment(first, "mob_kills.rotling", 4);
        harness.statistics.increment(second, "mob_kills.rotling", 2);
        harness.flushCycle.flushNow(FlushReason.INTERVAL).get();
        refresh();

        // Die Sicht traegt so viele Zeilen wie die Rohtabelle Schluessel-Konto-Paare hat. Diese
        // Zeile hat den Fehler gefunden, den kein Vergleich von Zahlen gezeigt haette: die
        // Auffrischung lief ohne commit und wurde beim Schliessen der Verbindung
        // zurueckgerollt - lautlos, mit Erfolgsmeldung.
        assertThat(count("SELECT COUNT(*) FROM rpg.mv_stat_alltime")).as("Sichtzeilen").isEqualTo(2);

        Map<Aggregation, Map<UUID, Long>> board = source().allTime();

        assertThat(board.get(Aggregation.MOB_KILLS))
                .containsEntry(first, rawSum(first, "mob_kills.rotling"))
                .containsEntry(second, rawSum(second, "mob_kills.rotling"))
                .containsEntry(first, 7L)
                .containsEntry(second, 2L);
    }

    @Test
    @DisplayName("die Sicht maximiert, wo maximiert werden muss - und summiert nicht")
    void theviewMaximisesWhereItMust() throws Exception {
        // Zwei TAGE mit je einem Hoechstwert. Summierte die Sicht, staende hier 1900.
        insertRawDaily(first, "damage_max", LocalDate.now(ZoneOffset.UTC).minusDays(1), 700);
        insertRawDaily(first, "damage_max", LocalDate.now(ZoneOffset.UTC), 1200);
        refresh();

        assertThat(source().allTime().get(Aggregation.DAMAGE_MAX))
                .containsEntry(first, 1200L);
    }

    @Test
    @DisplayName("die Wochensicht enthaelt genau die Tage dieser ISO-Woche")
    void theweekViewHoldsExactlyThisIsoWeek() throws Exception {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate lastWeek = today.minusWeeks(1);

        insertRawDaily(first, "mob_kills.rotling", today, 5);
        insertRawDaily(first, "mob_kills.rotling", lastWeek, 99);
        refresh();

        int isoYear = today.get(WeekFields.ISO.weekBasedYear());
        int isoWeek = today.get(WeekFields.ISO.weekOfWeekBasedYear());

        assertThat(source().week(isoYear, isoWeek).get(Aggregation.MOB_KILLS))
                .as("die 99 der Vorwoche gehoeren nicht hierher")
                .containsEntry(first, 5L);
    }

    @Test
    @DisplayName("die Tagessicht enthaelt genau diesen Tag")
    void thedayViewHoldsExactlyThatDay() throws Exception {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        insertRawDaily(first, "mob_kills.rotling", today, 5);
        insertRawDaily(first, "mob_kills.rotling", today.minusDays(1), 99);
        refresh();

        assertThat(source().day(today).get(Aggregation.MOB_KILLS)).containsEntry(first, 5L);
        assertThat(source().day(today.minusDays(1)).get(Aggregation.MOB_KILLS))
                .containsEntry(first, 99L);
    }

    @Test
    @DisplayName("der Saisonstand deckt genau seinen Datumsbereich ab")
    void theseasonStandingCoversExactlyItsRange() throws Exception {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        insertRawDaily(first, "mob_kills.rotling", today, 5);
        insertRawDaily(first, "mob_kills.rotling", today.minusDays(10), 7);
        insertRawDaily(first, "mob_kills.rotling", today.minusDays(40), 99);

        // Der Saisonstand kommt NICHT aus einer Sicht (ADR-049) - er braucht deshalb auch kein
        // Refresh, und genau das prueft dieser Test mit: hier steht kein refresh().
        assertThat(source().season(today.minusDays(30), today).get(Aggregation.MOB_KILLS))
                .containsEntry(first, 12L);
    }

    private JdbcLeaderboardSource source() {
        return new JdbcLeaderboardSource(harness.pools.loginPool(), kind -> false);
    }

    private void refresh() {
        assertThat(new LeaderboardRefresh(harness.pools.loginPool(), java.util.logging.Logger.getLogger("test"))
                        .refreshAll())
                .as("alle drei Sichten frisch")
                .isEqualTo(3);
    }

    private long count(String sql) throws Exception {
        try (Connection connection = harness.pools.loginPool().getConnection();
                PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet rows = statement.executeQuery()) {
            return rows.next() ? rows.getLong(1) : -1L;
        }
    }

    private long rawSum(UUID playerId, String metric) throws Exception {
        try (Connection connection = harness.pools.loginPool().getConnection();
                PreparedStatement statement =
                        connection.prepareStatement(
                                "SELECT COALESCE(SUM(value), 0) FROM rpg.player_statistic_daily"
                                        + " WHERE player_id = ? AND metric = ?")) {
            statement.setObject(1, playerId);
            statement.setString(2, metric);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getLong(1) : 0L;
            }
        }
    }

    private void insertRawDaily(UUID playerId, String metric, LocalDate day, long value)
            throws Exception {
        try (Connection connection = harness.pools.loginPool().getConnection();
                PreparedStatement statement =
                        connection.prepareStatement(
                                "INSERT INTO rpg.player_statistic_daily"
                                        + " (player_id, metric, day, value) VALUES (?, ?, ?, ?)"
                                        + " ON CONFLICT (player_id, metric, day)"
                                        + " DO UPDATE SET value = excluded.value")) {
            statement.setObject(1, playerId);
            statement.setString(2, metric);
            statement.setDate(3, java.sql.Date.valueOf(day));
            statement.setLong(4, value);
            statement.executeUpdate();
            // Der Pool gibt autoCommit=false heraus; ohne das commit waere diese Zeile beim
            // Schliessen wieder weg - derselbe Fallstrick, der oben LeaderboardRefresh erwischt
            // hat.
            if (!connection.getAutoCommit()) {
                connection.commit();
            }
        }
    }
}
