package rpg.persistence.statistics;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.persistence.FlushReason;
import rpg.core.persistence.PlayerState;
import rpg.persistence.support.PersistenceHarness;
import rpg.persistence.support.PostgresContainer;

/**
 * Die Zusage aus research.md R1 — <b>zehn Metriken und sechzig kosten dieselben vier Abfragen.</b>
 *
 * <p>Der naheliegende Entwurf wäre eine Sicht <em>je</em> Metrik gewesen: {@code mv_mob_kills},
 * {@code mv_deaths}, {@code mv_damage_max}. Er hätte am Anfang gut ausgesehen und wäre mit jedem
 * Block teurer geworden — bei sechzig Metriken sechzig Sichten, sechzig Auffrischungen, sechzig
 * Abfragen je Takt. Und niemand hätte den Moment benennen können, ab dem es zu viel wird.
 *
 * <p><b>Deshalb ist die Metrik eine Spalte und kein Sichtname.</b> Die Zahl der Abfragen hängt an
 * der Zahl der <em>Zeiträume</em> — vier —, nicht an der Zahl der Metriken. Dieser Test hält das
 * fest, indem er dasselbe zweimal misst: einmal mit zehn Metrikschlüsseln, einmal mit sechzig.
 *
 * <p><b>Gezählt werden Abfragen, nicht Zeilen.</b> Sechzig Schlüssel liefern natürlich mehr
 * Zeilen als zehn; das ist nicht die Zusage. Die Zusage ist, dass der Weg zur Datenbank derselbe
 * bleibt — und genau die Zahl der Wege ist es, die eine Auffrischung teuer macht.
 */
class RefreshCostIsIndependentOfMetricCountTest {

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
    @DisplayName("R1 - zehn Metrikschluessel und sechzig kosten dieselbe Zahl von Abfragen")
    void tenMetricKeysAndSixtyCostTheSameNumberOfQueries() throws Exception {
        insertKinds(10);
        refreshViews();
        int withTen = queriesForOneFullRead();

        insertKinds(60);
        refreshViews();
        int withSixty = queriesForOneFullRead();

        assertThat(withSixty)
                .as("die Zahl der Abfragen haengt an den Zeitraeumen, nicht an den Metriken")
                .isEqualTo(withTen);
    }

    @Test
    @DisplayName("R1 - und es sind genau vier: Allzeit, Woche, Tag, Saison")
    void anditIsExactlyFourAllTimeWeekDaySeason() throws Exception {
        insertKinds(30);
        refreshViews();

        // Vier Zeitraeume, vier Abfragen. Waeren es mehr, haette jemand eine Schleife ueber
        // Metriken gebaut, wo eine Gruppierung genuegt - und der Fehler waere erst auf einem
        // vollen Server als Ruckeln aufgefallen.
        assertThat(queriesForOneFullRead()).isEqualTo(4);
    }

    @Test
    @DisplayName("und die Zahl der ZEILEN waechst sehr wohl - das ist nicht dasselbe")
    void thenumberOfRowsDoesGrowAndThatIsSomethingElse() throws Exception {
        insertKinds(10);
        refreshViews();
        long withTen = killRowsInTheAllTimeView();

        insertKinds(60);
        refreshViews();

        // Der Gegenbeweis dazu, dass hier gar nichts passiert: die Daten wachsen wirklich, nur
        // der Weg zu ihnen nicht.
        assertThat(killRowsInTheAllTimeView()).isGreaterThan(withTen);
    }

    // ------------------------------------------------------------------ Gerüst

    /** Ein vollständiger Lesedurchlauf über alle vier Zeiträume, mit gezählten Abfragen. */
    private int queriesForOneFullRead() {
        AtomicInteger queries = new AtomicInteger();
        JdbcLeaderboardSource source =
                new JdbcLeaderboardSource(counting(harness.pools.loginPool(), queries), kind -> false);

        source.allTime();
        source.week(
                TODAY.get(java.time.temporal.WeekFields.ISO.weekBasedYear()),
                TODAY.get(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear()));
        source.day(TODAY);
        source.season(TODAY.minusDays(90), TODAY);

        return queries.get();
    }

    private void insertKinds(int count) throws Exception {
        try (Connection connection = harness.pools.loginPool().getConnection();
                PreparedStatement statement =
                        connection.prepareStatement(
                                "INSERT INTO rpg.player_statistic_daily"
                                        + " (player_id, metric, day, value) VALUES (?, ?, ?, ?)"
                                        + " ON CONFLICT (player_id, metric, day) DO NOTHING")) {
            for (int i = 0; i < count; i++) {
                statement.setObject(1, account);
                statement.setString(2, "mob_kills.kind-" + i);
                statement.setDate(3, java.sql.Date.valueOf(TODAY));
                statement.setLong(4, 1L);
                statement.addBatch();
            }
            statement.executeBatch();
            commit(connection);
        }
    }

    private void refreshViews() {
        new LeaderboardRefresh(harness.pools.loginPool(), java.util.logging.Logger.getLogger("test"))
                .refreshAll();
    }

    private long killRowsInTheAllTimeView() throws Exception {
        try (Connection connection = harness.pools.loginPool().getConnection();
                PreparedStatement statement =
                        connection.prepareStatement(
                                "SELECT COUNT(*) FROM rpg.mv_stat_alltime"
                                        + " WHERE metric LIKE 'mob_kills.%'");
                java.sql.ResultSet rows = statement.executeQuery()) {
            rows.next();
            return rows.getLong(1);
        }
    }

    /** Der Pool steht auf {@code autoCommit = false} — ohne das hier wäre nichts geschrieben. */
    private static void commit(Connection connection) throws SQLException {
        if (!connection.getAutoCommit()) {
            connection.commit();
        }
    }

    /**
     * Eine {@link DataSource}, die mitzählt, wie oft eine Abfrage vorbereitet wird.
     *
     * <p>Über einen dynamischen Stellvertreter statt einer handgeschriebenen Hülle: {@code
     * DataSource} und {@code Connection} haben zusammen über dreißig Methoden, und dreißig
     * Weiterleitungen von Hand wären dreißig Gelegenheiten, eine zu vergessen.
     */
    private static DataSource counting(DataSource delegate, AtomicInteger queries) {
        return (DataSource)
                java.lang.reflect.Proxy.newProxyInstance(
                        DataSource.class.getClassLoader(),
                        new Class<?>[] {DataSource.class},
                        (proxy, method, args) -> {
                            Object result = method.invoke(delegate, args);
                            if (result instanceof Connection connection) {
                                return countingConnection(connection, queries);
                            }
                            return result;
                        });
    }

    private static Connection countingConnection(Connection delegate, AtomicInteger queries) {
        return (Connection)
                java.lang.reflect.Proxy.newProxyInstance(
                        Connection.class.getClassLoader(),
                        new Class<?>[] {Connection.class},
                        (proxy, method, args) -> {
                            if (method.getName().startsWith("prepare")
                                    || method.getName().equals("createStatement")) {
                                queries.incrementAndGet();
                            }
                            return method.invoke(delegate, args);
                        });
    }
}
