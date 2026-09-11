package rpg.core.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Der Füllstand gegen eine <b>gestellte</b> Uhr (Constitution II.2).
 *
 * <p>Mit {@code Instant.now()} würde dieser Test entweder flackern oder nichts beweisen — und
 * ausgerechnet die Ränder, an denen die Rechnung schiefgeht, sind mit einer laufenden Uhr nicht
 * ansteuerbar.
 */
class BarProgressTest {

    private static final Instant T0 = UiFixtures.T0;
    private static final Instant DUE = T0.plus(Duration.ofSeconds(4));

    private final BarProgress progress = new BarProgress(T0, DUE);

    @Test
    @DisplayName("bei startedAt ist der Balken leer")
    void atTheStartItIsEmpty() {
        assertThat(progress.fraction(T0)).isEqualTo(0.0);
    }

    @Test
    @DisplayName("in der Mitte ist er halb")
    void inTheMiddleItIsHalf() {
        assertThat(progress.fraction(T0.plus(Duration.ofSeconds(2)))).isCloseTo(0.5, within(1e-9));
    }

    @Test
    @DisplayName("bei dueAt ist er voll")
    void atTheEndItIsFull() {
        assertThat(progress.fraction(DUE)).isEqualTo(1.0);
    }

    @Test
    @DisplayName("darueber hinaus bleibt er voll und laeuft nicht ueber")
    void beyondTheEndItStaysFull() {
        // Ein Balken ueber eins ist ein Paketfehler. Der Fall tritt auf, sobald ein Aufruf einen
        // Takt zu spaet kommt - also regelmaessig.
        assertThat(progress.fraction(DUE.plus(Duration.ofMinutes(5)))).isEqualTo(1.0);
    }

    @Test
    @DisplayName("davor bleibt er leer und wird nicht negativ")
    void beforeTheStartItStaysEmpty() {
        // Tritt auf, wenn die Uhr zurueckspringt. Ein negativer Wert ist eine Ausnahme in Papers
        // Bossbar, also ein Fehler in einem Pfad, der jedes Ereignis beruehrt.
        assertThat(progress.fraction(T0.minus(Duration.ofSeconds(10)))).isEqualTo(0.0);
    }

    @Test
    @DisplayName("startedAt == dueAt ergibt 1 und keine Division durch null")
    void aZeroLengthBarIsDone() {
        // Eine Faehigkeit ohne Kanalisierungszeit ist sofort fertig. Das ist der richtige Wert und
        // nicht der Sonderfall, den ein Aufrufer abfangen muesste.
        BarProgress instant = new BarProgress(T0, T0);

        assertThat(instant.fraction(T0)).isEqualTo(1.0);
        assertThat(instant.fraction(T0.minus(Duration.ofSeconds(1)))).isEqualTo(1.0);
    }

    @Test
    @DisplayName("der Fuellstand liegt nie ausserhalb von [0,1]")
    void theFractionNeverLeavesTheUnitInterval() {
        for (long offsetMillis = -10_000; offsetMillis <= 20_000; offsetMillis += 137) {
            double value = progress.fraction(T0.plusMillis(offsetMillis));

            assertThat(value).isBetween(0.0, 1.0);
        }
    }

    @Test
    @DisplayName("remaining ist die Gegenrichtung und laeuft nicht rueckwaerts")
    void remainingIsTheOtherDirection() {
        // Ein eigener Aufruf statt 1 - fraction an jeder Aufrufstelle: die Umkehrung ist die Sorte
        // Rechnung, die einmal vergessen wird und dann rueckwaerts laeuft.
        assertThat(progress.remaining(T0)).isEqualTo(1.0);
        assertThat(progress.remaining(DUE)).isEqualTo(0.0);
        assertThat(progress.remaining(T0.plus(Duration.ofSeconds(1)))).isCloseTo(0.75, within(1e-9));
    }

    @Test
    @DisplayName("isDone stimmt mit dem vollen Balken ueberein")
    void isDoneAgreesWithAFullBar() {
        assertThat(progress.isDone(T0)).isFalse();
        assertThat(progress.isDone(DUE)).isTrue();
        assertThat(progress.isDone(DUE.plusMillis(1))).isTrue();
    }

    @Test
    @DisplayName("ein Ende vor dem Anfang wird abgewiesen")
    void anEndBeforeTheStartIsRejected() {
        assertThatThrownBy(() -> new BarProgress(DUE, T0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("liegt vor");
    }
}
