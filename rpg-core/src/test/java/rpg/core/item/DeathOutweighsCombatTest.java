package rpg.core.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * FR-044 und SC-013 — <b>ein Tod wiegt schwerer als ein ganzer gewöhnlicher Kampf, und das ist eine
 * Startprüfung.</b>
 *
 * <p>Die Todesstrafe aus ADR-017 ist die einzige, die dieses Projekt kennt. Sie besteht seit
 * ADR-039 aus Verschleiß — und Verschleiß entsteht auch im normalen Kampf. Damit hängt die ganze
 * Strafe an einem Zahlenverhältnis, und ein Zahlenverhältnis lässt sich in einer YAML-Datei
 * umdrehen, ohne dass irgendetwas kaputtgeht: der Server startet, alles funktioniert, und der Tod
 * kostet weniger als drei Treffer.
 *
 * <p>Deshalb ist die Ordnung eine <b>Regel</b> und keine Zahlenwahl. Der Start weist eine
 * Konfiguration zurück, die sie verletzt.
 */
class DeathOutweighsCombatTest {

    @Test
    @DisplayName("die ausgelieferten Werte erfuellen die Ordnung")
    void theShippedValuesPass() {
        assertThatCode(() -> WearCurve.defaults().validate("items.yml.wear")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("ein Tod, der weniger wiegt als tausend Schadenspunkte, startet nicht")
    void aCheapDeathIsRefused() {
        // 0.01 je Schadenspunkt, Faktor 100 -> per-death muss mindestens 1.0 sein. 0.5 ist die
        // Sorte Aenderung, die jemand vornimmt, weil "der Tod sich zu hart anfuehlt" - und die
        // die Todesstrafe faktisch abschaltet.
        WearCurve tooCheap = curve(0.01, 0.01, 0.5, 100.0);

        assertThatThrownBy(() -> tooCheap.validate("items.yml.wear"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("per-death")
                .hasMessageContaining("death-factor-min")
                .hasMessageContaining("FR-044");
    }

    @Test
    @DisplayName("die Meldung nennt den Schluessel und den geforderten Wert")
    void theMessageNamesTheKeyAndTheRequirement() {
        WearCurve tooCheap = curve(0.02, 0.01, 1.0, 100.0);

        assertThatThrownBy(() -> tooCheap.validate("items.yml.wear"))
                .hasMessageContaining("items.yml.wear.per-death")
                // Die groessere der beiden Raten zaehlt, nicht die erste - sonst liesse sich die
                // Pruefung umgehen, indem man nur eine davon hochsetzt.
                .hasMessageContaining("2.0");
    }

    @Test
    @DisplayName("genau an der Grenze reicht es")
    void exactlyAtTheBoundaryPasses() {
        assertThatCode(() -> curve(0.01, 0.01, 1.0, 100.0).validate("items.yml.wear"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("SC-013 - ein Tod kostet mehr als ein ganzer gewoehnlicher Kampf")
    void oneDeathOutweighsAWholeFight() {
        WearCurve curve = WearCurve.defaults();

        // Ein gewoehnlicher Kampf: der Spieler steckt 300 Schadenspunkte ein und teilt 500 aus.
        double armourAfterFight = curve.afterDamageTaken(WearCurve.FULL, 300.0);
        double weaponAfterFight = curve.afterDamageDealt(WearCurve.FULL, 500.0);
        double fightCost =
                (WearCurve.FULL - armourAfterFight) + (WearCurve.FULL - weaponAfterFight);

        double deathCost = 2 * (WearCurve.FULL - curve.afterDeath(WearCurve.FULL));

        assertThat(deathCost)
                .as("ein Tod (%s) muss mehr kosten als ein ganzer Kampf (%s)", deathCost, fightCost)
                .isGreaterThan(fightCost);
    }

    @Test
    @DisplayName("zehn Tode fuehren von voll auf null")
    void tenDeathsEmptyTheBar() {
        WearCurve curve = WearCurve.defaults();
        double condition = WearCurve.FULL;
        for (int death = 0; death < 10; death++) {
            condition = curve.afterDeath(condition);
        }
        assertThat(condition).isEqualTo(0.0);
    }

    private static WearCurve curve(double taken, double dealt, double perDeath, double factor) {
        return new WearCurve(
                50.0, 0.20, taken, dealt, perDeath, factor, List.of(50.0), Duration.ofSeconds(60));
    }
}
