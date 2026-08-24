package rpg.core.mob;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die vier harten Grenzen, und dass die schaerfste entscheidet.
 *
 * <p>Ein Budget ist keine Zielgroesse. Das ist der Unterschied, an dem dieser ganze Block haengt:
 * die Dichte-Skalierung rechnet aus, wie voll eine Zone sein <em>sollte</em>, das Budget sagt, wie
 * voll sie hoechstens sein <em>darf</em>. Verschwimmen die beiden, ist ein ploetzlicher
 * Spieleransturm ein Weg, die Leistungszusage dieses Projekts zu ueberschreiten.
 */
class BudgetTest {

    private static final Budget BUDGET = new Budget(800, 130, 12, 25);

    @Test
    @DisplayName("mit Platz ueberall darf noch eine dazu")
    void withRoomEverywhereOneMoreIsAllowed() {
        assertThat(BUDGET.allows(100, 50, 3, 5)).isTrue();
    }

    @Test
    @DisplayName("ein voller Chunk verhindert es, auch wenn die Zone noch Platz hat")
    void aFullChunkStopsItEvenWithRoomInTheZone() {
        assertThat(BUDGET.allows(100, 50, 12, 5))
                .as("die schaerfste Grenze entscheidet, nicht die grosszuegigste")
                .isFalse();
    }

    @Test
    @DisplayName("eine volle Zone verhindert es, auch wenn der Chunk leer ist")
    void aFullZoneStopsItEvenInAnEmptyChunk() {
        assertThat(BUDGET.allows(100, 130, 0, 20)).isFalse();
    }

    @Test
    @DisplayName("das serverweite Budget haelt auch gegen die Summe der Zonen")
    void theServerWideBudgetHoldsAgainstTheSumOfTheZones() {
        // FR-013a: sechs Zonen zu je 200 waeren 1200. Hier stehen 800, und 800 gelten - sonst
        // stuende der Zielwert aus dem M4-Nachweis in der Vision und wuerde nirgends eingehalten.
        Budget generous = new Budget(800, 200, 12, 25);

        assertThat(generous.allows(800, 10, 0, 20))
                .as("die Zone haette Platz, der Server nicht")
                .isFalse();
    }

    @Test
    @DisplayName("ohne Spieler in der Zone entsteht nichts")
    void withoutPlayersNothingIsAllowed() {
        assertThat(BUDGET.allows(0, 0, 0, 0)).isFalse();
    }

    @Test
    @DisplayName("das Spielerbudget deckelt die Zone bei wenigen Anwesenden")
    void thePlayerBudgetCapsTheZoneWhenFewArePresent() {
        // Ein Spieler, 25 je Spieler: bei 25 lebenden Kreaturen ist Schluss, obwohl die Zone 130
        // erlaubt. Sonst stuende ein einzelner Spieler vor einer Horde, die fuer fuenf gedacht war.
        assertThat(BUDGET.allows(100, 24, 0, 1)).isTrue();
        assertThat(BUDGET.allows(100, 25, 0, 1)).isFalse();
    }

    @Test
    @DisplayName("die Obergrenze fuer eine Zone ist das Minimum aus allen dreien")
    void theCeilingIsTheMinimumOfAllThree() {
        assertThat(BUDGET.ceilingFor(1, 0)).as("ein Spieler: 25").isEqualTo(25);
        assertThat(BUDGET.ceilingFor(10, 0)).as("zehn Spieler: die Zone deckelt bei 130").isEqualTo(130);
        assertThat(BUDGET.ceilingFor(10, 750))
                .as("und wenn anderswo schon 750 stehen, bleiben 50")
                .isEqualTo(50);
    }

    @Test
    @DisplayName("ein Budget von null wird abgelehnt statt stillschweigend hingenommen")
    void aBudgetOfZeroIsRefused() {
        assertThatThrownBy(() -> new Budget(800, 0, 12, 25))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("budget.per-zone");
    }

    @Test
    @DisplayName("ein Chunk-Budget ueber dem Zonenbudget ist eine Zahl ohne Wirkung")
    void aChunkBudgetAboveTheZoneBudgetIsRefused() {
        assertThatThrownBy(() -> new Budget(800, 100, 200, 25))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("per-chunk");
    }
}
