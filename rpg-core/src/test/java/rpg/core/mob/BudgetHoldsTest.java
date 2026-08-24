package rpg.core.mob;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.random.RandomGenerator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * SC-003: Das Zonen-, Chunk- und Spielerbudget wird in keinem Testlauf ueberschritten, auch nicht
 * bei ploetzlichem Andrang.
 *
 * <p>Simuliert einen Zonendurchlauf ueber viele Ticks: nach jeder Entscheidung wird der simulierte
 * Bestand fortgeschrieben, genau wie es {@code HordeRegistry} in der Plattformschicht tut. Die
 * Zusage ist, dass {@code zoneTotal} nie ueber die Obergrenze steigt - auch dann nicht, wenn die
 * Zieldichte (hier: die schiere Anzahl an Durchlaeufen) rechnerisch weit darueber liegt.
 */
class BudgetHoldsTest {

    @Test
    @DisplayName("plötzlicher Andrang - viele Durchläufe hintereinander - überschreitet die Zonengrenze nicht")
    void suddenOnslaughtNeverExceedsTheZoneLimit() {
        Budget budget = new Budget(800, 130, 12, 25);
        HordeSpec horde =
                new HordeSpec(
                        "greenfields",
                        List.of(new HordeSpec.Entry("greenfields-east", "greenfields.rotling", 1)),
                        null);

        int zoneTotal = 0;
        // Weit mehr Durchlaeufe, als noetig waeren, um die Grenze zu erreichen - der plötzliche
        // Andrang aus SC-003.
        for (int i = 0; i < 500; i++) {
            Optional<SpawnPlanner.Decision> decision =
                    SpawnPlanner.plan(Optional.of(horde), budget, 0, zoneTotal, 100, always(0));
            if (decision.isPresent()) {
                zoneTotal++;
            }
            assertThat(zoneTotal).isLessThanOrEqualTo(budget.perZone());
        }
        assertThat(zoneTotal).isEqualTo(budget.perZone());
    }

    @Test
    @DisplayName("auch bei einer rechnerisch zu hohen Zieldichte haelt die Zonengrenze")
    void evenWhenTheTargetDensityWouldExceedItTheCeilingHolds() {
        // "Zieldichte" ist hier die Spielerzahl selbst: 100 Spieler wollen 100*999=99900 erlauben,
        // aber per-zone bleibt bei 130 stehen.
        Budget budget = new Budget(800, 130, 130, 999);
        HordeSpec horde =
                new HordeSpec(
                        "greenfields",
                        List.of(new HordeSpec.Entry("greenfields-east", "greenfields.rotling", 1)),
                        null);

        int zoneTotal = 130; // bereits an der Zonengrenze
        Optional<SpawnPlanner.Decision> decision =
                SpawnPlanner.plan(Optional.of(horde), budget, 0, zoneTotal, 100, always(0));

        assertThat(decision).as("die Zonengrenze gewinnt gegen die Spielerzahl").isEmpty();
    }

    private static RandomGenerator always(int value) {
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
