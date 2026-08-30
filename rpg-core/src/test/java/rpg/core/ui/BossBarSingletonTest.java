package rpg.core.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Es entsteht <b>genau eine</b> Bossbar je Spieler (FR-004b), egal wie viele Anlässe eintreffen.
 *
 * <p>Mehrere stapeln sich am oberen Bildrand und machen die Fläche unlesbar. Auf der Regelseite ist
 * das eine Signaturfrage: {@link BossBarPriority#winner} gibt ein {@link Optional} zurück und keine
 * Liste. Ein Aufrufer <em>kann</em> gar nicht zwei bekommen.
 *
 * <p><b>Das ist die verlässlichere Hälfte der Zusage.</b> Die andere — dass auch die Paper-Seite
 * nur eine Leiste anlegt und sie wiederverwendet statt zu stapeln — steht in
 * {@code PaperBossBarTest}. Gemessene Grenzen wackeln, eine Signatur nicht.
 */
class BossBarSingletonTest {

    @Test
    @DisplayName("drei Anlaesse ergeben einen Gewinner, nicht drei")
    void threeOccasionsYieldOneWinner() {
        Optional<BossBarOccasion> winner = BossBarPriority.winner(EnumSet.allOf(BossBarOccasion.class));

        assertThat(winner).isPresent();
    }

    @Test
    @DisplayName("die Signatur laesst zwei gar nicht zu")
    void theSignatureAdmitsNoSecond() {
        // Keine Messung, sondern eine Aussage ueber den Rueckgabetyp: Optional und nicht List.
        // Ein Test, der stattdessen Leisten zaehlte, waere von der Testkonfiguration abhaengig -
        // dieser ist es nicht.
        assertThat(BossBarPriority.class.getDeclaredMethods())
                .filteredOn(method -> method.getName().equals("winner"))
                .allSatisfy(method -> assertThat(method.getReturnType()).isEqualTo(Optional.class));
    }

    @Test
    @DisplayName("auch bei Duplikaten bleibt es einer")
    void duplicatesStillYieldOne() {
        Optional<BossBarOccasion> winner =
                BossBarPriority.winner(
                        List.of(
                                BossBarOccasion.BOSS_FIGHT,
                                BossBarOccasion.BOSS_FIGHT,
                                BossBarOccasion.CHANNELLING,
                                BossBarOccasion.CHANNELLING));

        assertThat(winner).contains(BossBarOccasion.CHANNELLING);
    }
}
