package rpg.core.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * FR-047, FR-048, SC-011 — <b>die Kurve über die ganze Spanne, nicht nur an den Enden.</b>
 *
 * <p>Die Enden allein wären die bequeme Prüfung und die falsche: eine Kurve, die bei 100 und bei 0
 * stimmt, kann dazwischen springen, und ein Sprung ist der Punkt, an dem ein einzelner Treffer
 * spürbar mehr kostet als der davor — ohne dass jemand erklären könnte warum.
 *
 * <p>Die Ansage des Auftraggebers war wörtlich: <em>„bei 0/100 volle 80 %, bei 10/100 wird sie schon
 * schwächer"</em>. Genau das rechnet dieser Test nach.
 */
class WearCurveTest {

    private final WearCurve curve = WearCurve.defaults();

    @Nested
    @DisplayName("Der Faktor")
    class TheFactor {

        @Test
        @DisplayName("die vier Stuetzstellen aus der Anforderung")
        void thefourStatedPoints() {
            // Mit Toleranz, und nicht aus Bequemlichkeit: 0,20 + 0,80 * 0,5 ergibt in double
            // 0,6000000000000001. Auf die exakte Bitfolge zu prüfen hiesse, einen Test zu haben,
            // der bei der naechsten Umstellung der Formel aus dem falschen Grund rot wird.
            org.assertj.core.data.Offset<Double> exact = org.assertj.core.data.Offset.offset(1e-9);
            assertThat(curve.factorFor(50.0)).isCloseTo(1.00, exact);
            assertThat(curve.factorFor(25.0)).isCloseTo(0.60, exact);
            assertThat(curve.factorFor(10.0)).isCloseTo(0.36, exact);
            assertThat(curve.factorFor(0.0)).isCloseTo(0.20, exact);
        }

        @Test
        @DisplayName("oberhalb der Schwelle ist der Beitrag voll - Verschleiss faengt spaeter an")
        void aboveTheThresholdTheContributionIsFull() {
            assertThat(curve.factorFor(100.0)).isEqualTo(1.0);
            assertThat(curve.factorFor(75.0)).isEqualTo(1.0);
            assertThat(curve.factorFor(50.0)).isEqualTo(1.0);
        }

        @Test
        @DisplayName("STETIG - zwei verschiedene Zustaende ergeben nie denselben Faktor (FR-048)")
        void thecurveIsStrictlyMonotonicBelowTheThreshold() {
            // Die eigentliche Zusage. In einem Prozentschritt ueber die ganze Spanne unterhalb der
            // Schwelle: jeder Schritt muss den Faktor echt erhoehen. Ein Plateau waere ein Bereich,
            // in dem Reparieren nichts braechte; ein Sprung waere ein Treffer, der weh tut.
            double previous = -1.0;
            for (int permille = 0; permille <= 500; permille++) {
                double factor = curve.factorFor(permille / 10.0);
                assertThat(factor)
                        .as("Zustand " + (permille / 10.0) + " muss ueber dem vorigen liegen")
                        .isGreaterThan(previous);
                previous = factor;
            }
        }

        @Test
        @DisplayName("und nie unter dem Restanteil oder ueber eins")
        void thefactorStaysInsideItsBand() {
            for (int condition = -20; condition <= 120; condition++) {
                assertThat(curve.factorFor(condition))
                        .isBetween(curve.floor(), 1.0);
            }
        }

        @Test
        @DisplayName("Werte ausserhalb der Spanne werden begrenzt, nicht abgelehnt")
        void valuesOutsideTheRangeAreClamped() {
            assertThat(curve.factorFor(-5.0)).isEqualTo(curve.factorFor(0.0));
            assertThat(curve.factorFor(500.0)).isEqualTo(1.0);
        }
    }

    @Nested
    @DisplayName("Der Abtrag")
    class TheReduction {

        @Test
        @DisplayName("erlittener Schaden senkt um Rate mal Schaden")
        void damageTakenReducesByRateTimesDamage() {
            assertThat(curve.afterDamageTaken(100.0, 200.0))
                    .as("0,01 je Schadenspunkt, 200 Punkte - also zwei Zustandspunkte")
                    .isCloseTo(98.0, org.assertj.core.data.Offset.offset(1e-9));
        }

        @Test
        @DisplayName("und ausgeteilter ebenso, mit seiner eigenen Rate")
        void damageDealtUsesItsOwnRate() {
            assertThat(curve.afterDamageDealt(100.0, 100.0))
                    .isCloseTo(99.0, org.assertj.core.data.Offset.offset(1e-9));
        }

        @Test
        @DisplayName("negativer Schaden senkt nichts - eine Heilung ist kein Verschleiss")
        void negativeDamageReducesNothing() {
            assertThat(curve.afterDamageTaken(80.0, -50.0)).isEqualTo(80.0);
        }

        @Test
        @DisplayName("und unter null geht es nicht")
        void itNeverFallsBelowZero() {
            assertThat(curve.afterDeath(2.0)).isEqualTo(0.0);
            assertThat(curve.afterDamageTaken(0.5, 10_000.0)).isEqualTo(0.0);
        }
    }

    @Nested
    @DisplayName("Die Warnschwellen")
    class TheWarningThresholds {

        @Test
        @DisplayName("ein Uebergang unter eine Schwelle meldet sie")
        void crossingAThresholdReportsIt() {
            assertThat(curve.crossedWarning(51.0, 49.0)).hasValue(50.0);
            assertThat(curve.crossedWarning(26.0, 24.0)).hasValue(25.0);
        }

        @Test
        @DisplayName("wer schon darunter war, unterschreitet sie nicht noch einmal")
        void beingAlreadyBelowIsNoCrossing() {
            assertThat(curve.crossedWarning(40.0, 39.0)).isEmpty();
        }

        @Test
        @DisplayName("ein Sturz ueber ZWEI Schwellen meldet genau EINE - die hoechste")
        void afallAcrossTwoThresholdsReportsOnlyTheHigher() {
            // Zwei Meldungen fuer einen Treffer waeren zwei zu viel, und die zweite verdeckt die
            // erste in genau dem Moment, in dem sie zaehlt.
            assertThat(curve.crossedWarning(60.0, 20.0)).hasValue(50.0);
        }

        @Test
        @DisplayName("und eine Reparatur nach oben meldet nichts")
        void goingUpReportsNothing() {
            assertThat(curve.crossedWarning(10.0, 100.0)).isEmpty();
        }
    }

    @Nested
    @DisplayName("Die Startpruefung")
    class TheStartupCheck {

        @Test
        @DisplayName("die Vorgabewerte gehen durch")
        void thedefaultsPass() {
            curve.validate("items.yml wear");
        }

        @Test
        @DisplayName("eine Schwelle ausserhalb (0, 100] bricht ab und nennt den Schluessel")
        void athresholdOutsideItsRangeAborts() {
            assertThatThrownBy(() -> with(0.0, 0.2, 0.01, 0.01, 10.0, 100.0).validate("items.yml wear"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("items.yml wear.threshold");
        }

        @Test
        @DisplayName("ein Restanteil von 1 hiesse: Verschleiss ohne Wirkung")
        void afloorOfOneIsRefused() {
            assertThatThrownBy(() -> with(50.0, 1.0, 0.01, 0.01, 10.0, 100.0).validate("wear"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("floor");
        }

        @Test
        @DisplayName("eine Rate von null hiesse: kein Verschleiss")
        void arateOfZeroIsRefused() {
            assertThatThrownBy(() -> with(50.0, 0.2, 0.0, 0.01, 10.0, 100.0).validate("wear"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("per-damage-taken");
        }
    }

    private static WearCurve with(
            double threshold,
            double floor,
            double perTaken,
            double perDealt,
            double perDeath,
            double deathFactorMin) {
        return new WearCurve(
                threshold,
                floor,
                perTaken,
                perDealt,
                perDeath,
                deathFactorMin,
                List.of(50.0, 25.0, 10.0),
                Duration.ofSeconds(60));
    }
}
