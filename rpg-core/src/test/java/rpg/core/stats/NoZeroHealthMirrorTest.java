package rpg.core.stats;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Ein Halter ohne gesetzte Ressourcen wird nicht gespiegelt - sonst tötet ihn die erste Berechnung.
 *
 * <p>Ein Halter entsteht, bevor irgendwer seine Maxima kennt: die kommen aus der ersten Berechnung.
 * Deshalb startet er mit dem Nullpool und wird unmittelbar danach gefüllt. In genau diesem Fenster
 * bedeutet ein Spiegel nach Vanilla {@code setHealth(0)}, und das ist der Tod.
 *
 * <p>Sichtbar wurde das erst, als B07 den Halter beim <em>Betreten des Spielzustands</em> baute statt
 * im asynchronen Pre-Login. Vorher gab es dort kein Spielerobjekt, der Spiegel lief ins Leere und
 * niemand bemerkte, worauf er zielte. Danach starb jeder Spieler einmal direkt nach der Klassenwahl.
 */
class NoZeroHealthMirrorTest {

    @Test
    @DisplayName("die erste Berechnung eines frischen Halters spiegelt keine Gesundheit")
    void theFirstCalculationDoesNotMirrorHealth() {
        List<Double> mirrored = new ArrayList<>();
        EngineFixture fixture = new EngineFixture();
        fixture.engine.registerVanillaBridge(recording(mirrored));

        UUID playerId = UUID.randomUUID();
        fixture.engine.createForCharacter(playerId, UUID.randomUUID(), new ResourcePool(0.0, 0.0));
        fixture.engine.recalculateNow(playerId);

        assertThat(mirrored)
                .as("nichts gespiegelt, solange der Pool nur der Platzhalter ist")
                .isEmpty();
    }

    @Test
    @DisplayName("nach dem Füllen wird gespiegelt - und zwar der volle Wert, nicht null")
    void afterRestoringTheRealValueIsMirrored() {
        List<Double> mirrored = new ArrayList<>();
        EngineFixture fixture = new EngineFixture();
        fixture.engine.registerVanillaBridge(recording(mirrored));

        UUID playerId = UUID.randomUUID();
        fixture.engine.createForCharacter(playerId, UUID.randomUUID(), new ResourcePool(0.0, 0.0));
        StatSnapshot first = fixture.engine.recalculateNow(playerId);
        fixture.engine.restoreResources(
                playerId,
                ResourcePool.full(first.get(Attribute.HEALTH), first.get(Attribute.MANA)));

        assertThat(mirrored).isNotEmpty();
        assertThat(mirrored.get(mirrored.size() - 1))
                .as("die volle Gesundheit der Klasse, nicht der Platzhalter")
                .isEqualTo(first.get(Attribute.HEALTH));
    }

    @Test
    @DisplayName("eine echte Null wird sehr wohl gespiegelt - ein Toter sieht tot aus")
    void agenuineZeroIsStillMirrored() {
        List<Double> mirrored = new ArrayList<>();
        EngineFixture fixture = new EngineFixture();
        fixture.engine.registerVanillaBridge(recording(mirrored));
        UUID holder = fixture.character();
        mirrored.clear();

        // Nicht der Platzhalter, sondern ein gesetzter Wert: der Halter ist erschöpft.
        fixture.engine.restoreResources(holder, new ResourcePool(0.0, 0.0));

        assertThat(mirrored).as("die Unterscheidung ist 'gesetzt', nicht 'ungleich null'").contains(0.0);
    }

    private static VanillaAttributeBridge recording(List<Double> mirrored) {
        return new VanillaAttributeBridge() {
            @Override
            public void mirrorHealth(UUID holderId, double currentHealth, double maxHealth) {
                mirrored.add(currentHealth);
            }

            @Override
            public void mirrorAttackSpeed(UUID holderId, double value) {
                // nicht Gegenstand dieses Tests
            }

            @Override
            public void mirrorMovementSpeed(UUID holderId, double value) {
                // nicht Gegenstand dieses Tests
            }
        };
    }
}
