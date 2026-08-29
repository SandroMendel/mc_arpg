package rpg.core.statistics;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Die rohe Leseseite — <b>ohne jede Regel.</b>
 *
 * <p>Sie summiert, was in einem Datumsbereich unter einem Schlüssel steht, und sonst nichts: keine
 * Zeitraumauflösung, keine Metrikart, keine Sichtbarkeit. All das entscheidet
 * {@link StatisticsView}, und zwar an <em>einer</em> Stelle.
 *
 * <p><b>Deshalb ist dieser Typ die Schnittstelle nach unten und nicht die nach außen.</b> Wäre er
 * öffentlich, gäbe es einen zweiten Weg an den privaten Werten vorbei — und FR-037 wäre eine
 * Empfehlung statt einer Regel. Wer B12 fragt, fragt {@link StatisticsView}.
 */
public interface RawStatisticsView {

    /** Die Summe eines einzelnen Schlüssels über einen Datumsbereich, beide Grenzen inklusive. */
    CompletableFuture<Long> sum(UUID account, String metricKey, LocalDate from, LocalDate to);

    /** Das Maximum eines einzelnen Schlüssels über einen Datumsbereich. */
    CompletableFuture<Long> max(UUID account, String metricKey, LocalDate from, LocalDate to);

    /**
     * Die Aufschlüsselung einer Familie: Dimension → Wert.
     *
     * <p>{@code mob_kills} liefert {@code {rotling: 12, dune-warlord: 1}} — die Schlüssel sind die
     * Dimensionen, nicht die vollständigen Metrikschlüssel.
     */
    CompletableFuture<Map<String, Long>> breakdown(
            UUID account, Metric family, LocalDate from, LocalDate to);
}
