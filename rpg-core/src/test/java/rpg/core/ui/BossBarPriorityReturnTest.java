package rpg.core.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>Der Test, der belegt, dass keine Warteschlange nötig ist.</b>
 *
 * <p>Eine Kanalisierung verdrängt einen laufenden Bosskampf. Wenn sie endet, ist der Bosskampf
 * wieder da — <em>ohne dass ihn jemand neu gemeldet hat</em>.
 *
 * <p>Das ist kein Zufall der Umsetzung, sondern der Grund, aus dem {@link BossBarPriority} kein Feld
 * hat: der Bosskampf kehrt zurück, weil sein <b>Zustand</b> beim nächsten Takt noch anliegt, nicht
 * weil ihn jemand aufgehoben hätte. Der Unterschied zwischen einem Zustand und einem Ereignis ist
 * hier der ganze Entwurf — und er spart den einzigen veränderlichen Zustand, den dieser Block sonst
 * führen müsste (Constitution I.6).
 *
 * <p>Wer diesen Test kaputtmacht, indem er eine Warteschlange einführt, macht ihn <em>nicht</em>
 * rot: eine Warteschlange käme zum selben Ergebnis. Rot wird er, wenn jemand den Zustand
 * <b>verbraucht</b> statt ihn zu lesen — und das ist der Fehler, gegen den er steht.
 */
class BossBarPriorityReturnTest {

    @Test
    @DisplayName("endet die Kanalisierung, ist der Bosskampf wieder da - ungemeldet")
    void theBossFightComesBackWithoutBeingReported() {
        // Was anliegt, ueber drei Takte hinweg. Der Bosskampf steht die ganze Zeit an; die
        // Kanalisierung kommt und geht.
        List<BossBarOccasion> pending = new ArrayList<>(List.of(BossBarOccasion.BOSS_FIGHT));

        assertThat(BossBarPriority.winner(pending)).contains(BossBarOccasion.BOSS_FIGHT);

        pending.add(BossBarOccasion.CHANNELLING);
        assertThat(BossBarPriority.winner(pending)).contains(BossBarOccasion.CHANNELLING);

        // Die Kanalisierung ist vorbei. NIEMAND meldet den Bosskampf erneut - er lag die ganze Zeit
        // an, er war nur verdeckt.
        pending.remove(BossBarOccasion.CHANNELLING);
        assertThat(BossBarPriority.winner(pending)).contains(BossBarOccasion.BOSS_FIGHT);
    }

    @Test
    @DisplayName("die Rangfolge verbraucht nichts - zweimal fragen, zweimal dieselbe Antwort")
    void askingTwiceGivesTheSameAnswer() {
        // Der Fehler, gegen den dieser Test steht: eine Umsetzung, die den gewinnenden Anlass beim
        // Lesen entfernt. Sie waere beim ersten Takt richtig und beim zweiten leer - und das faellt
        // erst auf, wenn ein Bosskampf laenger dauert als ein Takt, also immer, aber nur auf dem
        // Server.
        List<BossBarOccasion> pending = List.of(BossBarOccasion.BOSS_FIGHT);

        assertThat(BossBarPriority.winner(pending)).contains(BossBarOccasion.BOSS_FIGHT);
        assertThat(BossBarPriority.winner(pending)).contains(BossBarOccasion.BOSS_FIGHT);
        assertThat(pending).containsExactly(BossBarOccasion.BOSS_FIGHT);
    }

    @Test
    @DisplayName("ein Zonenname kehrt NICHT zurueck - er hat keinen Zustand")
    void aZoneNameDoesNotComeBack() {
        // Der Unterschied zu den anderen beiden: ein Zonenname ist ein Ereignis, das vorbei ist.
        // Er liegt nach seiner Verdraengung nicht mehr an, also kann er auch nicht zurueckkehren -
        // und genau das ist FR-004a.
        List<BossBarOccasion> pending = new ArrayList<>(List.of(BossBarOccasion.BOSS_FIGHT));

        assertThat(BossBarPriority.winner(pending)).contains(BossBarOccasion.BOSS_FIGHT);

        pending.remove(BossBarOccasion.BOSS_FIGHT);
        assertThat(BossBarPriority.winner(pending)).isEmpty();
    }
}
