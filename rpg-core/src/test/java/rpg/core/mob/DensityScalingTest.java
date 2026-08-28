package rpg.core.mob;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * FR-025: mehr Spieler in einer Zone heben die Zieldichte an und verkuerzen den Nachschubabstand -
 * niemals umgekehrt.
 */
class DensityScalingTest {

    private final Budget budget = new Budget(800, 130, 12, 25);

    @Test
    @DisplayName("fuenf Spieler haben eine hoehere Zieldichte als einer, bei gleichem Budget")
    void moreDensityWithMorePlayers() {
        int onePlayer = DensityScaling.targetDensity(budget, 0.20, 1, 0);
        int fivePlayers = DensityScaling.targetDensity(budget, 0.20, 5, 0);

        assertThat(fivePlayers).isGreaterThan(onePlayer);
    }

    @Test
    @DisplayName("ohne zusaetzliche Spieler bleibt die Zieldichte die eines einzelnen (densityPerPlayer=0)")
    void noScalingMeansFlatDensity() {
        int onePlayer = DensityScaling.targetDensity(budget, 0.0, 1, 0);
        int fivePlayers = DensityScaling.targetDensity(budget, 0.0, 5, 0);

        assertThat(fivePlayers).isEqualTo(onePlayer);
    }

    @Test
    @DisplayName("fuenf Spieler fuellen eine geraeumte Horde schneller nach als einer")
    void fasterRespawnWithMorePlayers() {
        Duration base = Duration.ofSeconds(2);

        Duration onePlayer = DensityScaling.respawnInterval(base, 0.20, 1);
        Duration fivePlayers = DensityScaling.respawnInterval(base, 0.20, 5);

        assertThat(fivePlayers).isLessThan(onePlayer);
    }

    @Test
    @DisplayName("kein Spieler in der Zone heisst Zieldichte null")
    void noPlayersMeansNoTarget() {
        assertThat(DensityScaling.targetDensity(budget, 0.20, 0, 0)).isZero();
    }
}
