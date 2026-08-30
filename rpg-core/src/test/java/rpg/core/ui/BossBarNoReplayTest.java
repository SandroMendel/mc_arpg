package rpg.core.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Ein verdrängter Anlass wird <b>nicht nachgeholt</b> (FR-004a).
 *
 * <p>Ein Zonenname, der während eines Bosskampfs anfällt, entfällt. Nachgereicht wäre er eine
 * Meldung über etwas, das längst vorbei ist — der Spieler hat die Zone vor zwei Minuten betreten
 * und steht inzwischen woanders.
 *
 * <p><b>Der Test prüft eine Abwesenheit</b>, und das ist die schwierigere Sorte: er kann nur
 * scheitern, wenn jemand aktiv eine Warteschlange einbaut. Er steht trotzdem hier, weil eine
 * Warteschlange genau die naheliegende „Verbesserung" ist, die jemand später gut gemeint einführt.
 */
class BossBarNoReplayTest {

    @Test
    @DisplayName("ein waehrend des Bosskampfs anfallender Zonenname entfaellt")
    void aZoneNameDuringABossFightIsDropped() {
        List<BossBarOccasion> pending =
                new ArrayList<>(List.of(BossBarOccasion.BOSS_FIGHT, BossBarOccasion.ZONE_NAME));

        // Der Bosskampf gewinnt.
        assertThat(BossBarPriority.winner(pending)).contains(BossBarOccasion.BOSS_FIGHT);

        // Der Zonenname war Sekunden lang gueltig und ist danach abgelaufen - die Quelle nimmt ihn
        // heraus, nicht die Rangfolge.
        pending.remove(BossBarOccasion.ZONE_NAME);
        // Der Bosskampf endet.
        pending.remove(BossBarOccasion.BOSS_FIGHT);

        // NICHTS kommt nach. Waere der Zonenname gemerkt worden, staende er jetzt hier.
        assertThat(BossBarPriority.winner(pending)).isEmpty();
    }

    @Test
    @DisplayName("die Rangfolge merkt sich nichts zwischen zwei Aufrufen")
    void nothingIsRememberedBetweenCalls() {
        // Ein voller Aufruf mit allen drei, danach ein leerer. Ein Rest waere eine Warteschlange.
        BossBarPriority.winner(List.of(BossBarOccasion.values()));

        assertThat(BossBarPriority.winner(List.of())).isEmpty();
    }
}
