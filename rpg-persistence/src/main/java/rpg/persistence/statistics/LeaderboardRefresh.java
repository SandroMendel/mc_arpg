package rpg.persistence.statistics;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.sql.DataSource;

/**
 * Frischt die drei Materialized Views auf — <b>{@code CONCURRENTLY}, und niemals im Tick</b>
 * (FR-031).
 *
 * <h2>Warum {@code CONCURRENTLY} keine Feinheit ist</h2>
 *
 * <p>Ein gewöhnliches {@code REFRESH MATERIALIZED VIEW} nimmt eine exklusive Sperre auf die Sicht.
 * Jede laufende Abfrage darauf wartet — und da die Auffrischung im konfigurierten Takt läuft,
 * hieße das: alle fünf Minuten steht jede Ranglistenabfrage für die Dauer der Neuberechnung.
 * Sichtbar wäre das als gelegentliches Hängen beim Öffnen eines Fensters, ohne erkennbares Muster.
 *
 * <p>{@code CONCURRENTLY} baut die Sicht daneben neu auf und tauscht sie am Ende. Das ist der
 * Grund, aus dem jede der drei Sichten einen eindeutigen Index trägt — ohne ihn lehnt PostgreSQL
 * die nebenläufige Auffrischung ab.
 *
 * <h2>Jede Sicht für sich</h2>
 *
 * <p>Ein Fehler an einer Sicht darf die anderen nicht mitnehmen: eine kaputte Wochensicht wäre
 * ärgerlich, eine deswegen ausgefallene Allzeitsicht wäre schlimmer. Dieselbe Aufteilung, die
 * {@code HordeSweep} je Zone trifft.
 */
public final class LeaderboardRefresh {

    /** Die drei Sichten aus {@code V12_1}. Die vierte gibt es nicht — siehe ADR-049. */
    static final List<String> VIEWS =
            List.of("rpg.mv_stat_alltime", "rpg.mv_stat_week", "rpg.mv_stat_day");

    private final DataSource dataSource;
    private final Logger logger;

    public LeaderboardRefresh(DataSource dataSource, Logger logger) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * Frischt alle drei Sichten auf.
     *
     * <p><b>Aus einem Hintergrundfaden zu rufen</b> — diese Methode blockiert, solange PostgreSQL
     * rechnet. Der Aufrufer ist der Auffrischungstakt, nicht der Tick.
     *
     * @return wie viele Sichten erfolgreich aufgefrischt wurden
     */
    public int refreshAll() {
        int refreshed = 0;
        for (String view : VIEWS) {
            if (refresh(view)) {
                refreshed++;
            }
        }
        return refreshed;
    }

    private boolean refresh(String view) {
        // Der Sichtname stammt aus der Konstante oben und niemals aus einer Eingabe - ein
        // PreparedStatement kann einen Bezeichner ohnehin nicht binden.
        String sql = "REFRESH MATERIALIZED VIEW CONCURRENTLY " + view;
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute(sql);
            // Der Pool gibt Verbindungen mit autoCommit=false heraus ("writes are batched inside
            // explicit transactions", ConnectionPools). Ohne dieses commit wird die Auffrischung
            // beim Schliessen der Verbindung ZURUECKGEROLLT - und zwar lautlos: execute() meldet
            // Erfolg, diese Methode meldet Erfolg, und die Sicht bleibt leer. Genau so ist es
            // hier zuerst gebaut worden, und nur der Vergleich mit den Rohdaten hat es gezeigt.
            if (!connection.getAutoCommit()) {
                connection.commit();
            }
            return true;
        } catch (SQLException failure) {
            // Eine Sicht, die nicht aufgefrischt werden konnte, zeigt weiterhin ihren letzten
            // Stand. Das ist die richtige Antwort: veraltete Zahlen sind brauchbar, fehlende
            // nicht - und die Altersangabe im Fenster macht den Unterschied sichtbar (FR-032).
            logger.log(Level.WARNING, "[statistics] refresh of " + view + " failed", failure);
            return false;
        }
    }
}
