package rpg.core.ability;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T072b - die Geometrie hinter Sneaky Backstab (FR-052a).
 *
 * <p>Serverfrei prüfbar, weil die Regel eine Winkelrechnung ist und kein Weltzugriff. Nur das Ablesen
 * der beiden Richtungen braucht Paper.
 */
class BehindTargetCheckTest {

    private static final double ANGLE = BehindTargetCheck.DEFAULT_ANGLE;

    @Test
    @DisplayName("genau von hinten zählt")
    void straightBehindCounts() {
        // Ziel schaut nach Norden (-Z), Angreifer steht südlich davon (+Z).
        assertThat(BehindTargetCheck.isBehind(0.0, -1.0, 0.0, 1.0, ANGLE)).isTrue();
    }

    @Test
    @DisplayName("frontal zählt nicht")
    void straightAheadDoesNot() {
        assertThat(BehindTargetCheck.isBehind(0.0, -1.0, 0.0, -1.0, ANGLE)).isFalse();
    }

    @Test
    @DisplayName("genau seitlich zählt nicht - die Schulterlinie ist die Grenze")
    void exactlySidewaysDoesNot() {
        assertThat(BehindTargetCheck.isBehind(0.0, -1.0, 1.0, 0.0, ANGLE)).isFalse();
        assertThat(BehindTargetCheck.isBehind(0.0, -1.0, -1.0, 0.0, ANGLE)).isFalse();
    }

    @Test
    @DisplayName("schräg von hinten zählt")
    void diagonallyBehindCounts() {
        assertThat(BehindTargetCheck.isBehind(0.0, -1.0, 0.7, 0.7, ANGLE)).isTrue();
    }

    @Test
    @DisplayName("die Höhe spielt keine Rolle - nur die waagerechte Richtung")
    void heightIsIrrelevant() {
        // Dieselbe waagerechte Lage, egal ob der Angreifer über oder unter dem Ziel steht: die
        // Y-Achse geht gar nicht erst ein.
        assertThat(BehindTargetCheck.isBehind(0.0, -1.0, 0.0, 5.0, ANGLE)).isTrue();
    }

    @Test
    @DisplayName("übereinander stehend zählt NICHT - ein unentscheidbarer Fall gilt nicht als günstig")
    void standingOnTopOfEachOtherDoesNot() {
        assertThat(BehindTargetCheck.isBehind(0.0, -1.0, 0.0, 0.0, ANGLE)).isFalse();
        assertThat(BehindTargetCheck.isBehind(0.0, 0.0, 0.0, 1.0, ANGLE)).isFalse();
    }

    @Test
    @DisplayName("ein engerer Kegel weist ab, was der weite noch durchlässt")
    void aNarrowerConeIsStricter() {
        // 45 Grad hinter der Schulter: beim Standardkegel drin, bei einem engen draußen.
        assertThat(BehindTargetCheck.isBehind(0.0, -1.0, 0.9, 0.4, ANGLE)).isTrue();
        assertThat(BehindTargetCheck.isBehind(0.0, -1.0, 0.9, 0.4, 30.0)).isFalse();
    }

    @Test
    @DisplayName("die Vorgabe ist nie-hinten, nicht immer-hinten")
    void theDefaultIsNever() {
        assertThat(
                        BehindTargetCheck.never()
                                .test(java.util.UUID.randomUUID(), java.util.UUID.randomUUID()))
                .isFalse();
    }
}
