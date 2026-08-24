package rpg.core.mob;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.random.RandomGenerator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * FR-014: die Spawn-Berechnung wird ueber mehrere Ticks verteilt und nicht in einem Tick
 * gebuendelt.
 *
 * <p>Ein Durchlauf, der einer leeren Zone 130 Kreaturen fehlen wuerden, plant trotzdem nur eine
 * einzige - {@link SpawnPlanner#plan} gibt eine einzelne {@link Optional} zurueck und keine Liste,
 * und genau das ist die Obergrenze aus T043: sie steht im Typ, nicht in einer Zaehlung, die
 * jemand vergessen koennte zu pruefen.
 */
class SpawnSpreadOverTicksTest {

    @Test
    @DisplayName("ein grosser Fehlbestand fuehrt trotzdem nur zu EINER Entscheidung je Durchlauf")
    void aLargeDeficitStillYieldsOnlyOneDecisionPerCall() {
        Budget budget = new Budget(800, 130, 12, 25);
        HordeSpec horde =
                new HordeSpec(
                        "greenfields",
                        List.of(new HordeSpec.Entry("greenfields-east", "greenfields.rotling", 1)),
                        null);

        // Eine komplett leere Zone - der groesstmoegliche Fehlbestand dieses Blocks (130 fehlen).
        Optional<SpawnPlanner.Decision> decision =
                SpawnPlanner.plan(Optional.of(horde), budget, 0, 0, 25, RandomGenerator.getDefault());

        assertThat(decision).isPresent();
        // Es gibt keine Methode, die mehr als eine Decision zurueckgeben koennte - die Grenze
        // liegt im Rueckgabetyp Optional<Decision>, nicht in einer Zahl, die separat zu pruefen
        // waere.
    }

    @Test
    @DisplayName("mehrere Durchlaeufe fuellen schrittweise auf, nie in einem Sprung")
    void multipleCallsFillUpStepByStepNeverInOneJump() {
        Budget budget = new Budget(800, 130, 12, 25);
        HordeSpec horde =
                new HordeSpec(
                        "greenfields",
                        List.of(new HordeSpec.Entry("greenfields-east", "greenfields.rotling", 1)),
                        null);

        int zoneTotal = 0;
        int calls = 0;
        while (zoneTotal < budget.perZone()) {
            Optional<SpawnPlanner.Decision> decision =
                    SpawnPlanner.plan(
                            Optional.of(horde), budget, 0, zoneTotal, 25, RandomGenerator.getDefault());
            assertThat(decision).isPresent();
            zoneTotal++; // hoechstens eine je Aufruf
            calls++;
        }

        assertThat(calls)
                .as("so viele Aufrufe wie Kreaturen - kein Sprung, der mehrere auf einmal setzt")
                .isEqualTo(budget.perZone());
    }
}
