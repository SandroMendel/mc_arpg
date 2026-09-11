package rpg.platform.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.platform.hud.CombatStatusSource;

/**
 * Die Herzleiste zeigt in jeder Lage den korrekten Prozentwert (T152, FR-007, SC-003).
 *
 * <h2>Warum das eine eigene Zusage ist</h2>
 *
 * <p>ADR-003: die zwanzig Vanilla-Herzen sind eine <b>Prozentanzeige</b> und keine Lebensanzeige.
 * Ein Charakter mit 2000 Leben hat dieselben zwanzig Herzen wie einer mit 200 — was sich ändert, ist
 * der Anteil. Das ist der einzige Weg, echte Zahlen im vierstelligen Bereich mit einer Leiste
 * darzustellen, die Minecraft auf zwanzig Punkte festlegt.
 *
 * <p><b>B13 hat daran nichts geändert</b> und darf es auch nicht: die Herzleiste ist keine der drei
 * Flächen, die dieser Block ordnet (FR-001), sondern eine vierte, die
 * {@code PaperVanillaAttributeBridge} seit B04 bespielt. Dieser Test hält die Zusage fest, ohne den
 * fremden Block anzufassen — er prüft die <em>Rechnung</em>, nicht die Klasse.
 *
 * <h2>Was er prüft, und was die Ränder sind</h2>
 *
 * <p>Die Ränder sind die interessante Hälfte: 0 Leben, volles Leben, ein Charakter, dessen Maximum
 * sich gerade geändert hat, und der Fall, in dem beides null ist — ein Halter ohne Werte. Der letzte
 * ist der, der eine Division durch null wäre.
 */
class HeartBarPercentTest {

    @Test
    @DisplayName("T152: volles Leben sind 100 Prozent")
    void fullHealthIsAHundredPercent() {
        assertThat(status(2000.0, 2000.0).percent()).isEqualTo(100);
    }

    @Test
    @DisplayName("T152: die Haelfte sind 50 Prozent - unabhaengig von der Groesse")
    void halfIsFiftyPercentAtAnyScale() {
        // Der Kern von ADR-003: ein Charakter mit 2000 Leben und einer mit 200 sehen bei halbem
        // Leben dasselbe. Die Leiste zeigt den ANTEIL, nicht die Zahl.
        assertThat(status(1000.0, 2000.0).percent()).isEqualTo(50);
        assertThat(status(100.0, 200.0).percent()).isEqualTo(50);
        assertThat(status(10.0, 20.0).percent()).isEqualTo(50);
    }

    @Test
    @DisplayName("T152: null Leben sind null Prozent")
    void noHealthIsZeroPercent() {
        assertThat(status(0.0, 2000.0).percent()).isZero();
    }

    @Test
    @DisplayName("T152: ein Halter ohne Werte ergibt keine Division durch null")
    void aholderWithoutValuesDoesNotDivideByZero() {
        // Der Fall zwischen Anmeldung und Charakterwahl, und der einzige, der rechnerisch
        // gefaehrlich ist. Er darf keine Ausnahme werfen - die Herzleiste liegt in einem Pfad, den
        // jedes Ereignis beruehrt.
        assertThat(status(0.0, 0.0).percent()).isBetween(0, 100);
    }

    @Test
    @DisplayName("T152: die HERZLEISTE bleibt in [0,20] - auch bei Ueberheilung")
    void theheartBarStaysInRange() {
        // ZWEI VERSCHIEDENE RECHNUNGEN, und der erste Entwurf dieses Tests hat sie verwechselt:
        //
        //   Status.percent()                     -> die ZAHL auf der Actionbar
        //   PaperVanillaAttributeBridge          -> die HERZEN
        //     .displayedHealth(current, max)
        //
        // percent() begrenzt NICHT: 2500 von 2000 ergibt 125. Das ist auf einer Textzeile eine
        // seltsame Zahl und sonst nichts. displayedHealth begrenzt sehr wohl - und muss es, weil
        // mehr als zwanzig Herzen in Vanilla ein Anzeigefehler waeren und keine Zugabe.
        //
        // SC-003 spricht von der HERZLEISTE. Also gehoert der Test hierher und nicht zu percent().
        assertThat(
                        rpg.platform.stats.PaperVanillaAttributeBridge.displayedHealth(
                                2500.0, 2000.0))
                .isLessThanOrEqualTo(20.0);
        assertThat(
                        rpg.platform.stats.PaperVanillaAttributeBridge.displayedHealth(
                                -50.0, 2000.0))
                .isGreaterThanOrEqualTo(0.0);
    }

    @Test
    @DisplayName("T152: die Herzleiste zeigt denselben Anteil bei jeder Groesse")
    void theheartBarShowsTheSameShareAtAnyScale() {
        // ADR-003 am eigentlichen Ort: zwanzig Herzen sind eine Prozentanzeige. Halbes Leben sind
        // zehn Herzen, ob der Charakter 2000 oder 200 traegt.
        assertThat(rpg.platform.stats.PaperVanillaAttributeBridge.displayedHealth(1000.0, 2000.0))
                .isEqualTo(10.0);
        assertThat(rpg.platform.stats.PaperVanillaAttributeBridge.displayedHealth(100.0, 200.0))
                .isEqualTo(10.0);
    }

    @Test
    @DisplayName("T152: ein Toter zeigt null Herzen, ein Halter ohne Maximum den kleinsten Rest")
    void theedgesOfTheHeartBar() {
        // Die zwei Raender, die eine Ausnahme in Papers setHealth waeren.
        assertThat(rpg.platform.stats.PaperVanillaAttributeBridge.displayedHealth(0.0, 2000.0))
                .isZero();
        assertThat(rpg.platform.stats.PaperVanillaAttributeBridge.displayedHealth(100.0, 0.0))
                .isPositive();
    }

    @Test
    @DisplayName("T152: die Rechnung rundet und schneidet nicht ab")
    void thecalculationRoundsRatherThanTruncating() {
        // 1999 von 2000 sind 99,95 Prozent. Abgeschnitten waeren das 99 - und ein Spieler mit einem
        // Kratzer saehe ein sichtbar fehlendes Herz.
        assertThat(status(1999.0, 2000.0).percent()).isEqualTo(100);
    }

    @Test
    @DisplayName("die Herzleiste ist KEINE der drei Flaechen, die B13 ordnet")
    void theheartBarIsNotOneOfTheThreeSurfaces() {
        // FR-007 gegen FR-001: sie zeigt Leben als PROZENTWERT, die Actionbar als Zahl. Zwei
        // verschiedene Groessen, keine doppelte Wahrheit - deshalb steht sie nicht in
        // DisplayedValue und ist trotzdem keine Ausnahme.
        assertThat(rpg.core.ui.HudSurface.values()).hasSize(3);
        assertThat(rpg.core.ui.DisplayedValue.HEALTH.surface())
                .isEqualTo(rpg.core.ui.HudSurface.ACTION_BAR);
    }

    private static CombatStatusSource.Status status(double health, double maxHealth) {
        return new CombatStatusSource.Status(health, maxHealth, 0.0, 0.0, 0.0);
    }
}
