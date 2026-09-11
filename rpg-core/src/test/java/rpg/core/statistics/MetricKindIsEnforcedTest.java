package rpg.core.statistics;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * contracts/stats-api.md §1 — <b>abgewiesen, nicht umgedeutet.</b>
 *
 * <p>{@code count} auf einer Maximum-Metrik und {@code reportMax} auf einer Summen-Metrik sind
 * Programmierfehler. Sie freundlich auszulegen wäre die schlimmere Antwort: der höchste Schaden
 * würde sich still aufsummieren und wäre ab da eine Schadenssumme mit falschem Namen — eine Zahl,
 * die plausibel aussieht, gegen die kein Test anschlägt und die niemand mehr zurückrechnen kann.
 *
 * <p><b>Warum hier {@link Metric#requireKind} geprüft wird und nicht die Fassade.</b> Die
 * Zurückweisung gehört zur Metrik, nicht zu ihrem Aufrufer; {@code Statistics} (T059) ruft sie
 * auf, statt sie noch einmal zu formulieren. Läge sie in der Fassade, hätte der zweite Aufrufer —
 * etwa der Schreibweg aus ADR-040 — seine eigene Fassung davon.
 */
class MetricKindIsEnforcedTest {

    @Test
    @DisplayName("zaehlen auf einer MAX-Metrik wird abgewiesen")
    void countingAMaxMetricIsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> MetricRegistry.DAMAGE_MAX.requireKind(MetricKind.SUM))
                .withMessageContaining(MetricRegistry.DAMAGE_MAX.key())
                .withMessageContaining("MAX")
                .withMessageContaining("SUM");
    }

    @Test
    @DisplayName("ein Maximum auf einer SUM-Metrik wird abgewiesen")
    void reportingAMaximumOnASumMetricIsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> MetricRegistry.MOB_KILLS.requireKind(MetricKind.MAX))
                .withMessageContaining(MetricRegistry.MOB_KILLS.key());
    }

    @Test
    @DisplayName("die passende Art geht durch und liefert die Metrik zurueck")
    void theMatchingKindPassesThrough() {
        assertThatCode(() -> MetricRegistry.MOB_KILLS.requireKind(MetricKind.SUM))
                .doesNotThrowAnyException();
        assertThatCode(() -> MetricRegistry.DAMAGE_MAX.requireKind(MetricKind.MAX))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("ein Zustandswert passt auf keinen der beiden Schreibwege")
    void aStateValueFitsNeitherWritePath() {
        for (Metric state :
                new Metric[] {MetricRegistry.LEVEL, MetricRegistry.XP, MetricRegistry.COINS}) {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> state.requireKind(MetricKind.SUM));
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> state.requireKind(MetricKind.MAX));
        }
    }
}
