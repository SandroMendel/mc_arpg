package rpg.core.mob;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.random.RandomGenerator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * FR-018a: der Nachschub ist kontinuierlich - getoetete Kreaturen werden laufend ersetzt, bis die
 * Zieldichte wieder erreicht ist. Es gibt keinen Wellenzustand je Zone.
 *
 * <p>Der Test setzt genau das um: es gibt keinen Zonenzustand, den er vorher setzen muesste. Jeder
 * Aufruf von {@link SpawnPlanner#plan} sieht nur den aktuellen Bestand - ein Kill mitten in einer
 * vollen Zone oeffnet sofort wieder Platz, ohne dass die Zone erst geraeumt sein muesste.
 */
class SupplyIsContinuousTest {

    @Test
    @DisplayName("nach einem Kill kommt sofort wieder Nachschub - ohne dass die Zone erst leer sein muss")
    void replacementComesRightAfterAKillWithoutTheZoneBeingCleared() {
        Budget budget = new Budget(800, 130, 12, 25);
        HordeSpec horde =
                new HordeSpec(
                        "greenfields",
                        List.of(new HordeSpec.Entry("greenfields-east", "greenfields.rotling", 1)),
                        null);
        RandomGenerator random = RandomGenerator.getDefault();

        // Zone ist bis auf eine Kreatur voll.
        int zoneTotal = budget.perZone() - 1;
        assertThat(SpawnPlanner.plan(Optional.of(horde), budget, 0, zoneTotal, 25, random))
                .as("ein einziger freier Platz reicht fuer eine Entscheidung")
                .isPresent();

        // Und voll: kein Wellenzustand, einfach die Grenze.
        zoneTotal = budget.perZone();
        assertThat(SpawnPlanner.plan(Optional.of(horde), budget, 0, zoneTotal, 25, random)).isEmpty();

        // Ein Kill mitten in der vollen Zone - kein "Welle geraeumt", nur ein Platz weniger belegt.
        zoneTotal -= 1;
        assertThat(SpawnPlanner.plan(Optional.of(horde), budget, 0, zoneTotal, 25, random))
                .as("Nachschub sofort nach dem Kill, ohne dass die ganze Horde vorher weg war")
                .isPresent();
    }
}
