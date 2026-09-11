package rpg.persistence.statistics;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import javax.sql.DataSource;

import rpg.core.persistence.PersistenceException;
import rpg.core.scheduler.Scheduler;
import rpg.core.statistics.Metric;
import rpg.core.statistics.MetricKeys;
import rpg.core.statistics.MetricKind;
import rpg.core.statistics.RawStatisticsView;

/**
 * Die Leseseite auf der Tagestabelle — <b>immer asynchron, nie im Tick.</b>
 *
 * <p>Diese Abfragen laufen, wenn ein Spieler sein Profil öffnet. Sie sind klein (ein Konto, ein
 * Datumsbereich) und trotzdem Datenbankarbeit; im Tick hätten sie nichts verloren. Deshalb geht
 * jede über den Scheduler und liefert ein {@code CompletableFuture} — dieselbe Bauart wie B02s
 * {@code JdbcStatisticsRepository}.
 *
 * <p><b>Kein Cache.</b> Das eigene Profil ist keine Rangliste: es fragt nach <em>einem</em> Konto,
 * und der Fragende ist der, dessen Zahlen es sind. Ein Speicherstand dafür wäre eine zweite
 * Wahrheit über Werte, die sich mit jedem Kill ändern — und er würde ausgerechnet dem Spieler
 * veraltete Zahlen zeigen, der gerade etwas getan hat.
 */
public final class JdbcStatisticsView implements RawStatisticsView {

    private static final String SUM =
            "SELECT COALESCE(SUM(value), 0) FROM rpg.player_statistic_daily"
                    + " WHERE player_id = ? AND metric = ? AND day BETWEEN ? AND ?";

    private static final String MAX =
            "SELECT COALESCE(MAX(value), 0) FROM rpg.player_statistic_daily"
                    + " WHERE player_id = ? AND metric = ? AND day BETWEEN ? AND ?";

    /**
     * Die Aufschlüsselung einer Familie.
     *
     * <p>{@code metric LIKE 'mob_kills.%'} — und der Punkt ist wörtlich gemeint. Ohne ihn träfe
     * das Muster auch {@code mob_kills_total}, gäbe es das je; mit ihm trifft es genau die
     * Dimensionen dieser Familie. Der Unterstrich in {@code mob_kills} ist in LIKE ein
     * Platzhalter für ein Zeichen, deshalb wird er mit {@code ESCAPE} entschärft — sonst passte
     * {@code mob-kills.x} ebenso, und niemand sähe den Unterschied.
     */
    private static final String BREAKDOWN_SUM =
            "SELECT metric, SUM(value) FROM rpg.player_statistic_daily"
                    + " WHERE player_id = ? AND metric LIKE ? ESCAPE '\\'"
                    + "   AND day BETWEEN ? AND ?"
                    + " GROUP BY metric";

    private static final String BREAKDOWN_MAX =
            "SELECT metric, MAX(value) FROM rpg.player_statistic_daily"
                    + " WHERE player_id = ? AND metric LIKE ? ESCAPE '\\'"
                    + "   AND day BETWEEN ? AND ?"
                    + " GROUP BY metric";

    private final DataSource readPool;
    private final Scheduler scheduler;

    public JdbcStatisticsView(DataSource readPool, Scheduler scheduler) {
        this.readPool = Objects.requireNonNull(readPool, "readPool");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public CompletableFuture<Long> sum(UUID account, String metricKey, LocalDate from, LocalDate to) {
        return single(SUM, account, metricKey, from, to);
    }

    @Override
    public CompletableFuture<Long> max(UUID account, String metricKey, LocalDate from, LocalDate to) {
        return single(MAX, account, metricKey, from, to);
    }

    @Override
    public CompletableFuture<Map<String, Long>> breakdown(
            UUID account, Metric family, LocalDate from, LocalDate to) {
        String sql = family.kind() == MetricKind.MAX ? BREAKDOWN_MAX : BREAKDOWN_SUM;
        String pattern = escapeLike(family.key()) + ".%";

        CompletableFuture<Map<String, Long>> future = new CompletableFuture<>();
        scheduler.runAsync(
                () -> {
                    try (Connection connection = readPool.getConnection();
                            PreparedStatement statement = connection.prepareStatement(sql)) {
                        statement.setObject(1, account);
                        statement.setString(2, pattern);
                        statement.setDate(3, Date.valueOf(from));
                        statement.setDate(4, Date.valueOf(to));
                        try (ResultSet rows = statement.executeQuery()) {
                            Map<String, Long> byDimension = new LinkedHashMap<>();
                            while (rows.next()) {
                                // Nach aussen gehen die DIMENSIONEN, nicht die vollstaendigen
                                // Schluessel: das Fenster zeigt "rotling", nicht
                                // "mob_kills.rotling".
                                String storedKey = rows.getString(1);
                                long value = rows.getLong(2);
                                // Kein ifPresent mit Lambda: rows.getLong wirft eine gepruefte
                                // Ausnahme, und die kaeme aus einem Lambda nicht heraus.
                                var dimension = MetricKeys.dimensionOf(storedKey);
                                if (dimension.isPresent()) {
                                    byDimension.put(dimension.get(), value);
                                }
                            }
                            future.complete(byDimension);
                        }
                    } catch (SQLException failure) {
                        future.completeExceptionally(
                                new PersistenceException("statistics breakdown failed", failure));
                    }
                });
        return future;
    }

    private CompletableFuture<Long> single(
            String sql, UUID account, String metricKey, LocalDate from, LocalDate to) {
        CompletableFuture<Long> future = new CompletableFuture<>();
        scheduler.runAsync(
                () -> {
                    try (Connection connection = readPool.getConnection();
                            PreparedStatement statement = connection.prepareStatement(sql)) {
                        statement.setObject(1, account);
                        statement.setString(2, metricKey);
                        statement.setDate(3, Date.valueOf(from));
                        statement.setDate(4, Date.valueOf(to));
                        try (ResultSet rows = statement.executeQuery()) {
                            future.complete(rows.next() ? rows.getLong(1) : 0L);
                        }
                    } catch (SQLException failure) {
                        future.completeExceptionally(
                                new PersistenceException("statistics query failed", failure));
                    }
                });
        return future;
    }

    /** Entschärft die LIKE-Platzhalter in einem Familiennamen. */
    private static String escapeLike(String raw) {
        return raw.replace("\\", "\\\\").replace("_", "\\_").replace("%", "\\%");
    }
}
