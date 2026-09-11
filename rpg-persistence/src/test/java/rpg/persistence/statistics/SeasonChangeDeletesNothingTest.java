package rpg.persistence.statistics;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.persistence.FlushReason;
import rpg.core.persistence.PlayerState;
import rpg.core.statistics.Aggregation;
import rpg.core.statistics.ScoreWeights;
import rpg.core.statistics.SeasonStanding;
import rpg.persistence.support.PersistenceHarness;
import rpg.persistence.support.PostgresContainer;

/**
 * FR-057, ADR-044 — <b>nach einem Saisonwechsel ist kein Rohdatensatz verschwunden.</b>
 *
 * <p>Der Reflex am Saisonende wäre, „aufzuräumen": die Saison ist vorbei, ihr Endstand ist
 * eingefroren, wozu noch die Tageszeilen? Weil sie <em>nicht nur</em> zur Saison gehören. Dieselben
 * Zeilen tragen die Allzeitrangliste, das eigene Profil und jede spätere Auswertung, die noch
 * niemand vorhergesehen hat. Sie zu löschen wäre eine Entscheidung über Fragen, die noch keiner
 * gestellt hat — und sie ließe sich nicht zurücknehmen.
 *
 * <p><b>Deshalb löscht dieser Block nichts.</b> Nicht am Saisonende, nicht bei der Auffrischung,
 * nirgends: {@code NoDirectDatabaseAccessTest} verbietet jedes {@code DELETE} gegen die
 * Tagestabelle, und dieser Test prüft die andere Richtung — dass ein vollständiger Saisonwechsel
 * tatsächlich nichts anfasst.
 */
class SeasonChangeDeletesNothingTest {

    private static final LocalDate TODAY = LocalDate.now(ZoneOffset.UTC);

    private PersistenceHarness harness;
    private UUID account;

    @BeforeEach
    void setUp() throws Exception {
        PostgresContainer.resetSchema();
        harness = new PersistenceHarness();
        account = UUID.randomUUID();
        harness.playerStates.put(PlayerState.initial(account, Instant.now()));
        harness.flushCycle.flushNow(FlushReason.INTERVAL).get();
    }

    @AfterEach
    void tearDown() {
        if (harness != null) {
            harness.close();
        }
    }

    @Test
    @DisplayName("FR-057 - nach dem Abschluss stehen alle Rohzeilen unveraendert da")
    void afterClosingEveryRawRowIsStillThere() throws Exception {
        // Zeilen aus der abzuschliessenden Saison und aus der Zeit davor.
        insert("mob_kills.rotling", TODAY.minusDays(200), 40);
        insert("mob_kills.rotling", TODAY.minusDays(40), 12);
        insert("deaths.void", TODAY.minusDays(40), 3);

        long before = rawRows();
        long killsBefore = sumOf("mob_kills.rotling");

        // Ein vollstaendiger Saisonwechsel: Endstand einfrieren, Anspruch anlegen, auffrischen.
        new JdbcSeasonResultRepository(harness.pools.loginPool())
                .freeze(
                        SeasonStanding.rank("2026-q2", Map.of(account, 52L), Instant.now()),
                        new ScoreWeights(Map.of(Aggregation.MOB_KILLS, 1.0)));
        new LeaderboardRefresh(harness.pools.loginPool(), java.util.logging.Logger.getLogger("test"))
                .refreshAll();

        assertThat(rawRows()).as("keine Zeile weniger").isEqualTo(before);
        assertThat(sumOf("mob_kills.rotling")).as("und kein Wert veraendert").isEqualTo(killsBefore);
    }

    @Test
    @DisplayName("FR-057 - auch die Zeilen aus VOR der Saison bleiben")
    void evenTheRowsFromBeforeTheSeasonRemain() throws Exception {
        insert("mob_kills.rotling", TODAY.minusDays(400), 999);

        new JdbcSeasonResultRepository(harness.pools.loginPool())
                .freeze(
                        SeasonStanding.rank("2026-q2", Map.of(account, 1L), Instant.now()),
                        new ScoreWeights(Map.of(Aggregation.MOB_KILLS, 1.0)));

        // Sie gehoeren zur Allzeitrangliste, und die endet nicht mit einer Saison.
        assertThat(sumOf("mob_kills.rotling")).isEqualTo(999L);
    }

    @Test
    @DisplayName("ADR-044 - der Endstand steht NEBEN den Rohdaten, nicht an ihrer Stelle")
    void thestandingStandsBesideTheRawDataNotInsteadOfIt() throws Exception {
        insert("mob_kills.rotling", TODAY.minusDays(40), 12);

        JdbcSeasonResultRepository results = new JdbcSeasonResultRepository(harness.pools.loginPool());
        results.freeze(
                SeasonStanding.rank("2026-q2", Map.of(account, 12L), Instant.now()),
                new ScoreWeights(Map.of(Aggregation.MOB_KILLS, 1.0)));

        // Beides ist da: die verdichtete Aussage UND das, woraus sie entstand. Der Endstand ist
        // eine Zusammenfassung, kein Ersatz - sonst waere jede spaetere Frage an die Rohdaten
        // unbeantwortbar.
        assertThat(results.standingsOf("2026-q2")).hasSize(1);
        assertThat(rawRows()).isEqualTo(1L);
    }

    private void insert(String metric, LocalDate day, long value) throws Exception {
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
            if (!connection.getAutoCommit()) {
                connection.commit();
            }
        }
    }

    private long rawRows() throws Exception {
        return scalar("SELECT COUNT(*) FROM rpg.player_statistic_daily WHERE player_id = ?", null);
    }

    private long sumOf(String metric) throws Exception {
        return scalar(
                "SELECT COALESCE(SUM(value), 0) FROM rpg.player_statistic_daily"
                        + " WHERE player_id = ? AND metric = ?",
                metric);
    }

    private long scalar(String sql, String metric) throws Exception {
        try (Connection connection = harness.pools.loginPool().getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, account);
            if (metric != null) {
                statement.setString(2, metric);
            }
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getLong(1) : -1L;
            }
        }
    }
}
