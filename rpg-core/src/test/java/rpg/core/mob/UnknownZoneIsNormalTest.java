package rpg.core.mob;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.Optional;
import java.util.random.RandomGenerator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * FR-016: ein unbekannter Zonenschluessel und eine leere Bereichsliste sind ein normaler Zustand -
 * kein Fehler, keine Ausnahme, nichts erzeugt.
 */
class UnknownZoneIsNormalTest {

    private static final Budget BUDGET = new Budget(800, 130, 12, 25);

    @Test
    @DisplayName("eine fehlende Horde erzeugt nichts und wirft nicht")
    void aMissingHordeCreatesNothingAndDoesNotThrow() {
        RandomGenerator random = RandomGenerator.getDefault();

        assertThatCode(() -> SpawnPlanner.plan(Optional.empty(), BUDGET, 0, 0, 5, random))
                .doesNotThrowAnyException();
        assertThat(SpawnPlanner.plan(Optional.empty(), BUDGET, 0, 0, 5, random)).isEmpty();
    }
}
