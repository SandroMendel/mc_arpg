package rpg.persistence.statistics;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import javax.sql.DataSource;

import rpg.core.persistence.PersistenceException;
import rpg.core.statistics.Aggregation;
import rpg.core.statistics.MetricKeys;
import rpg.core.statistics.MetricKind;
import rpg.core.statistics.MetricRegistry;

/**
 * Füllt den Speicherstand — <b>eine Abfrage je Zeitraum, unabhängig von der Zahl der
 * Ranglisten</b> (R1).
 *
 * <h2>Was hier zusammengefasst wird und warum nicht in SQL</h2>
 *
 * <p>Die Sichten liefern Zeilen je <em>vollständigem</em> Metrikschlüssel:
 * {@code mob_kills.rotling}, {@code mob_kills.dune-warlord}, {@code deaths.void}. Erst hier
 * entstehen daraus Ranglisten — und das aus zwei Gründen, die beide in der Datenbank nicht
 * lösbar wären:
 *
 * <ul>
 *   <li><b>Welche Art ein Boss ist, steht in {@code mobs.yml}</b> (FR-009a). Die Datenbank kennt
 *       das Kennzeichen nicht, und es dorthin zu spiegeln wäre eine zweite Wahrheit über eine
 *       Zahl, die sich beim Balancing ändert. Verliert eine Art ihr Kennzeichen, wandern ihre
 *       Kills bei der nächsten Auffrischung von selbst hinüber.
 *   <li><b>Die Metrikart entscheidet, ob summiert oder maximiert wird</b>, und das Verzeichnis
 *       steht im Code (FR-018).
 * </ul>
 *
 * <h2>Der Saisonstand ist kein Sichtabruf</h2>
 *
 * <p>Er kommt aus einer parametrisierten Abfrage über die Rohtabelle, deren Grenzen der
 * Saisonkalender liefert (ADR-049) — eine Materialized View kennt keine Parameter, und den
 * Kalender in die Datenbank zu spiegeln hieße, ihn zweimal zu führen.
 */
public final class JdbcLeaderboardSource {

    private static final String ALLTIME =
            "SELECT metric, player_id, value FROM rpg.mv_stat_alltime";

    private static final String WEEK =
            "SELECT metric, player_id, value FROM rpg.mv_stat_week"
                    + " WHERE iso_year = ? AND iso_week = ?";

    private static final String DAY =
            "SELECT metric, player_id, value FROM rpg.mv_stat_day WHERE day = ?";

    /**
     * Der Saisonstand.
     *
     * <p>Über die Rohtabelle statt über eine Sicht, mit demselben Anonymisierungsfilter, den die
     * Sichten im Verbund tragen (FR-039) — er darf hier nicht fehlen, sonst erschiene ein
     * anonymisiertes Konto ausgerechnet in der Rangliste, die belohnt wird.
     */
    private static final String SEASON =
            "SELECT d.metric, d.player_id,"
                    + " CASE WHEN split_part(d.metric, '.', 1) IN ('damage_max')"
                    + "      THEN MAX(d.value) ELSE SUM(d.value) END AS value"
                    + " FROM rpg.player_statistic_daily d"
                    + " JOIN rpg.player_state ps"
                    + "   ON ps.player_id = d.player_id AND ps.anonymized = FALSE"
                    + " WHERE d.day BETWEEN ? AND ?"
                    + " GROUP BY d.metric, d.player_id";

    private final DataSource dataSource;
    private final Function<String, Boolean> isBossKind;

    /**
     * @param isBossKind ob ein Artenschlüssel ein Boss ist — B10s Verzeichnis, nicht ein eigenes
     */
    public JdbcLeaderboardSource(DataSource dataSource, Function<String, Boolean> isBossKind) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.isBossKind = Objects.requireNonNull(isBossKind, "isBossKind");
    }

    /** Der Allzeitstand je Rangliste und Konto. */
    public Map<Aggregation, Map<UUID, Long>> allTime() {
        return query(ALLTIME, statement -> {});
    }

    /** Der Stand einer ISO-Woche. */
    public Map<Aggregation, Map<UUID, Long>> week(int isoYear, int isoWeek) {
        return query(
                WEEK,
                statement -> {
                    statement.setInt(1, isoYear);
                    statement.setInt(2, isoWeek);
                });
    }

    /** Der Stand eines Kalendertages. */
    public Map<Aggregation, Map<UUID, Long>> day(LocalDate day) {
        return query(DAY, statement -> statement.setDate(1, Date.valueOf(day)));
    }

    /** Der Stand einer Saison, aus ihren konfigurierten Grenzen (ADR-049). */
    public Map<Aggregation, Map<UUID, Long>> season(LocalDate from, LocalDate to) {
        return query(
                SEASON,
                statement -> {
                    statement.setDate(1, Date.valueOf(from));
                    statement.setDate(2, Date.valueOf(to));
                });
    }

    private Map<Aggregation, Map<UUID, Long>> query(String sql, StatementBinder binder) {
        Map<Aggregation, Map<UUID, Long>> byBoard = new LinkedHashMap<>();

        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    String metric = rows.getString(1);
                    UUID playerId = rows.getObject(2, UUID.class);
                    long value = rows.getLong(3);

                    Aggregation board = boardOf(metric);
                    if (board == null) {
                        // Ein Schluessel, den das Verzeichnis nicht kennt - etwa aus einer
                        // aelteren Fassung. Er bleibt in der Tabelle stehen (nichts wird
                        // geloescht, ADR-044) und erscheint auf keiner Rangliste.
                        continue;
                    }
                    merge(byBoard.computeIfAbsent(board, ignored -> new HashMap<>()), playerId, value, board);
                }
            }
        } catch (SQLException failure) {
            throw new PersistenceException("leaderboard query failed", failure);
        }
        return byBoard;
    }

    /**
     * Fasst zwei Werte derselben Rangliste zusammen.
     *
     * <p>Summe oder Maximum — <b>je nach Metrikart, nicht je nach Aufrufstelle</b>. Fasste man
     * {@code damage_max} über mehrere Zeilen summierend zusammen, entstünde eine Schadenssumme mit
     * dem Namen „höchster Treffer": eine plausible Zahl an einer falschen Stelle, gegen die kein
     * Test anschlägt.
     */
    private static void merge(Map<UUID, Long> board, UUID playerId, long value, Aggregation of) {
        if (of.source().kind() == MetricKind.MAX) {
            board.merge(playerId, value, Math::max);
        } else {
            board.merge(playerId, value, Long::sum);
        }
    }

    /** Welche Rangliste dieser gespeicherte Schlüssel speist — {@code null}, wenn keine. */
    private Aggregation boardOf(String storedKey) {
        String family = MetricKeys.familyOf(storedKey);

        if (family.equals(MetricRegistry.MOB_KILLS.key())) {
            // Die einzige Familie, die sich auf ZWEI Ranglisten aufteilt, und die Trennung ist
            // disjunkt: beide zusammen ergeben alle Kills, keine Art zaehlt in beiden (FR-009a).
            String kindKey = MetricKeys.dimensionOf(storedKey).orElse("");
            return Boolean.TRUE.equals(isBossKind.apply(kindKey))
                    ? Aggregation.BOSS_KILLS
                    : Aggregation.MOB_KILLS;
        }
        for (Aggregation aggregation : Aggregation.values()) {
            if (aggregation != Aggregation.BOSS_KILLS && aggregation.source().key().equals(family)) {
                return aggregation;
            }
        }
        return null;
    }

    /** Alle Ranglisten, die ein Stand tragen kann — auch die derzeit leeren. */
    public static Set<Aggregation> boards() {
        return Set.of(Aggregation.values());
    }

    @FunctionalInterface
    private interface StatementBinder {
        void bind(PreparedStatement statement) throws SQLException;
    }
}
