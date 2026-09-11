package rpg.core.mob;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * FR-027: die Zieldichte ist ein Ziel, das Budget die Grenze - {@code min(zieldichte, budget)}
 * (data-model.md). Die Skalierung wird am Budget gekappt, nicht umgekehrt.
 */
class ScalingStopsAtTheBudgetTest {

    @Test
    @DisplayName("eine rechnerisch riesige Zieldichte wird exakt auf die Budgetgrenze gekappt")
    void targetNeverExceedsTheBudgetCeiling() {
        Budget budget = new Budget(800, 30, 12, 25);

        int target = DensityScaling.targetDensity(budget, 5.0, 50, 0);

        int ceiling = budget.ceilingFor(50, 0);
        assertThat(target).isEqualTo(ceiling);
        assertThat(target).isLessThanOrEqualTo(budget.perZone());
    }

    @Test
    @DisplayName("das serverweite Budget kappt die Zieldichte, auch wenn die Zone selbst noch Platz haette")
    void targetIsCappedByWhatIsLeftOfTheServerWideBudget() {
        Budget budget = new Budget(100, 130, 12, 25);

        int target = DensityScaling.targetDensity(budget, 1.0, 10, 95);

        assertThat(target).isEqualTo(5);
    }
}
