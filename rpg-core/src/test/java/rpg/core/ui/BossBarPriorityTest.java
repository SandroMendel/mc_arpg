package rpg.core.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Rangfolge der Bossbar-Anlässe (FR-004): Kanalisierung vor Bosskampf vor Zonenname.
 *
 * <p><b>Und sie darf nicht von der Eintreffreihenfolge abhängen.</b> Das ist der eigentliche Punkt
 * der Anforderung — eine Rangfolge, die nur meistens gilt, fällt genau dann auf, wenn zwei Anlässe
 * zusammentreffen, also selten und unter Last.
 */
class BossBarPriorityTest {

    @Test
    @DisplayName("alle drei zugleich - die Kanalisierung gewinnt")
    void channellingWinsOverEverything() {
        assertThat(BossBarPriority.winner(EnumSet.allOf(BossBarOccasion.class)))
                .contains(BossBarOccasion.CHANNELLING);
    }

    @Test
    @DisplayName("Bosskampf und Zonenname - der Bosskampf gewinnt")
    void bossFightWinsOverZoneName() {
        assertThat(
                        BossBarPriority.winner(
                                List.of(BossBarOccasion.ZONE_NAME, BossBarOccasion.BOSS_FIGHT)))
                .contains(BossBarOccasion.BOSS_FIGHT);
    }

    @Test
    @DisplayName("jeder einzeln gewinnt fuer sich")
    void eachOneWinsAlone() {
        for (BossBarOccasion occasion : BossBarOccasion.values()) {
            assertThat(BossBarPriority.winner(List.of(occasion))).contains(occasion);
        }
    }

    @Test
    @DisplayName("nichts anliegend - kein Gewinner, keine Ausnahme")
    void nothingPendingYieldsNothing() {
        assertThat(BossBarPriority.winner(List.of())).isEmpty();
    }

    @Test
    @DisplayName("die Eintreffreihenfolge aendert nichts")
    void theArrivalOrderChangesNothing() {
        // Der Kern von FR-004. Beide Reihenfolgen, dasselbe Ergebnis - haette jemand statt der
        // Rangfolge "der letzte gewinnt" gebaut, faellt es hier und sonst nirgends auf.
        Optional<BossBarOccasion> zoneFirst =
                BossBarPriority.winner(
                        List.of(
                                BossBarOccasion.ZONE_NAME,
                                BossBarOccasion.BOSS_FIGHT,
                                BossBarOccasion.CHANNELLING));
        Optional<BossBarOccasion> channellingFirst =
                BossBarPriority.winner(
                        List.of(
                                BossBarOccasion.CHANNELLING,
                                BossBarOccasion.BOSS_FIGHT,
                                BossBarOccasion.ZONE_NAME));

        assertThat(zoneFirst).isEqualTo(channellingFirst).contains(BossBarOccasion.CHANNELLING);
    }

    @Test
    @DisplayName("derselbe Anlass mehrfach aendert nichts")
    void duplicatesChangeNothing() {
        assertThat(
                        BossBarPriority.winner(
                                List.of(
                                        BossBarOccasion.BOSS_FIGHT,
                                        BossBarOccasion.BOSS_FIGHT,
                                        BossBarOccasion.BOSS_FIGHT)))
                .contains(BossBarOccasion.BOSS_FIGHT);
    }

    @Test
    @DisplayName("die Reihenfolge der Aufzaehlung IST die Rangfolge")
    void theEnumOrderIsTheRanking() {
        // Sie steht nicht zusaetzlich als Zahl daneben - zwei Quellen fuer dieselbe Ordnung sind
        // genau eine zu viel. Dieser Test ist die Zusage, dass niemand die Aufzaehlung umsortiert,
        // ohne die Rangfolge zu meinen.
        assertThat(BossBarOccasion.values())
                .containsExactly(
                        BossBarOccasion.CHANNELLING,
                        BossBarOccasion.BOSS_FIGHT,
                        BossBarOccasion.ZONE_NAME);
    }

    @Test
    @DisplayName("beats: ein hoeherer Rang verdraengt einen niedrigeren")
    void aHigherRankDisplacesALowerOne() {
        assertThat(BossBarPriority.beats(BossBarOccasion.CHANNELLING, BossBarOccasion.BOSS_FIGHT))
                .isTrue();
        assertThat(BossBarPriority.beats(BossBarOccasion.ZONE_NAME, BossBarOccasion.BOSS_FIGHT))
                .isFalse();
    }

    @Test
    @DisplayName("beats: gegen eine leere Bossbar kommt jeder durch")
    void anythingBeatsNothing() {
        for (BossBarOccasion occasion : BossBarOccasion.values()) {
            assertThat(BossBarPriority.beats(occasion, null)).isTrue();
        }
    }

    @Test
    @DisplayName("beats: Gleichstand verliert - kein Neuzeichnen ohne Aenderung")
    void aTieLoses() {
        // Sonst ersetzte ein stehender Anlass sich selbst, und das waere ein Paket ohne Aenderung -
        // genau das, was FR-013 verbietet.
        assertThat(BossBarPriority.beats(BossBarOccasion.BOSS_FIGHT, BossBarOccasion.BOSS_FIGHT))
                .isFalse();
    }
}
