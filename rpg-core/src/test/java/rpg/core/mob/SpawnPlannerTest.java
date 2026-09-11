package rpg.core.mob;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.random.RandomGenerator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * WAS wo gesetzt werden soll (FR-011, FR-012, FR-015, FR-016).
 *
 * <p>Ohne Bukkit und ohne Entitaet - der Planer entscheidet nur Bereich und Art, nie einen
 * tatsaechlichen Ort. Das ist die Trennung, die diesen Teil des Blocks serverlos testbar macht.
 */
class SpawnPlannerTest {

    private static final Budget BUDGET = new Budget(800, 130, 12, 25);

    @Test
    @DisplayName("nur innerhalb der konfigurierten Bereiche, nie ausserhalb")
    void onlyWithinConfiguredAreas() {
        HordeSpec horde =
                new HordeSpec(
                        "greenfields",
                        List.of(new HordeSpec.Entry("greenfields-east", "greenfields.rotling", 1)),
                        null);

        Optional<SpawnPlanner.Decision> decision =
                SpawnPlanner.plan(Optional.of(horde), BUDGET, 0, 0, 1, fixed(0));

        assertThat(decision).isPresent();
        assertThat(decision.get().areaKey()).isEqualTo("greenfields-east");
        assertThat(decision.get().kindKey()).isEqualTo("greenfields.rotling");
    }

    @Test
    @DisplayName("die Gewichte entscheiden, wer gewuerfelt wird")
    void weightsDecideTheRoll() {
        HordeSpec horde =
                new HordeSpec(
                        "greenfields",
                        List.of(
                                new HordeSpec.Entry("greenfields-east", "a", 1),
                                new HordeSpec.Entry("greenfields-east", "b", 3)),
                        null);

        // totalWeight = 4. Rolls 0 -> "a", Rolls 1..3 -> "b".
        assertThat(
                        SpawnPlanner.plan(Optional.of(horde), BUDGET, 0, 0, 1, fixed(0))
                                .orElseThrow()
                                .kindKey())
                .isEqualTo("a");
        assertThat(
                        SpawnPlanner.plan(Optional.of(horde), BUDGET, 0, 0, 1, fixed(1))
                                .orElseThrow()
                                .kindKey())
                .isEqualTo("b");
        assertThat(
                        SpawnPlanner.plan(Optional.of(horde), BUDGET, 0, 0, 1, fixed(3))
                                .orElseThrow()
                                .kindKey())
                .isEqualTo("b");
    }

    @Test
    @DisplayName("eine Zone an ihrer Obergrenze bekommt nichts mehr")
    void aZoneAtItsCeilingGetsNothingMore() {
        HordeSpec horde =
                new HordeSpec(
                        "greenfields",
                        List.of(new HordeSpec.Entry("greenfields-east", "greenfields.rotling", 1)),
                        null);

        // zoneTotal ist bereits an der Obergrenze - nichts Neues.
        Optional<SpawnPlanner.Decision> decision =
                SpawnPlanner.plan(Optional.of(horde), BUDGET, 0, 130, 5, fixed(0));

        assertThat(decision).isEmpty();
    }

    private static RandomGenerator fixed(int value) {
        return new RandomGenerator() {
            @Override
            public long nextLong() {
                return value;
            }

            @Override
            public int nextInt(int bound) {
                return value;
            }
        };
    }
}
