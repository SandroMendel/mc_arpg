package rpg.core.statistics;

import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import rpg.core.persistence.StatisticsRepository;

/**
 * Die übliche {@link Statistics}: sie schreibt in B02s Tagestabelle.
 *
 * <h2>Warum sie jede Ausnahme schluckt</h2>
 *
 * <p>FR-004. Diese Klasse hängt in Kampf-, Bewegungs- und Sitzungspfaden — an Stellen also, an
 * denen ein durchgereichter Fehler etwas kaputt macht, das mit Statistik nichts zu tun hat: einen
 * Kill, der nicht zu Ende geführt wird, eine Beute, die nicht fällt, ein Levelaufstieg, der hängen
 * bleibt.
 *
 * <p><b>Ein nicht gezählter Kill ist hinnehmbar, ein verlorener Kill nicht.</b> Deshalb wird hier
 * geloggt und weitergegangen. Das ist ausdrücklich <em>nicht</em> die übliche Regel des Projekts —
 * eine verschluckte Ausnahme ist sonst ein Fehler für sich —, und sie gilt hier nur, weil die
 * Statistik der Beobachter des Spiels ist und nicht sein Schiedsrichter.
 *
 * <p>Damit sie nicht zur stillen Ausrede wird, geht sie auf {@link Level#WARNING} mit dem
 * betroffenen Metrikschlüssel. Eine Zählung, die dauerhaft ausfällt, steht dann im Protokoll,
 * statt sich nur in einer Zahl zu verstecken, die niemand nachrechnen kann.
 */
public final class RecordedStatistics implements Statistics {

    private final StatisticsRepository repository;
    private final Logger logger;

    public RecordedStatistics(StatisticsRepository repository, Logger logger) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public void count(UUID playerId, Metric metric, long delta) {
        if (metric.dimensioned()) {
            // Abgewiesen, nicht umgedeutet - aber nach aussen getragen wuerde es das
            // Spielereignis abbrechen, und das verbietet FR-004. Also dieselbe Behandlung wie
            // jeder andere Verstoss hier: nichts schreiben, ins Protokoll damit.
            logger.warning(
                    "[statistics] not counted: '"
                            + metric.key()
                            + "' is dimensioned - use the overload that takes a dimension");
            return;
        }
        write(playerId, metric, metric.key(), delta, false);
    }

    @Override
    public void count(UUID playerId, Metric family, String dimension, long delta) {
        String key;
        try {
            key = MetricKeys.compose(family, dimension);
        } catch (RuntimeException malformed) {
            // Ein unbrauchbarer Dimensionsschluessel ist ein Programmier- oder Konfigurationsfehler
            // und keine Ausnahme, die das Spielereignis betrifft - aber er darf es trotzdem nicht
            // abbrechen.
            logger.log(Level.WARNING, "[statistics] unusable dimension for " + family.key(), malformed);
            return;
        }
        write(playerId, family, key, delta, false);
    }

    @Override
    public void reportMax(UUID playerId, Metric metric, long value) {
        write(playerId, metric, metric.key(), value, true);
    }

    private void write(UUID playerId, Metric metric, String key, long value, boolean maximum) {
        try {
            metric.requireKind(maximum ? MetricKind.MAX : MetricKind.SUM);
            if (maximum) {
                repository.reportMax(playerId, key, value);
            } else {
                repository.increment(playerId, key, value);
            }
        } catch (RuntimeException failure) {
            logger.log(Level.WARNING, "[statistics] not counted: " + key, failure);
        }
    }
}
