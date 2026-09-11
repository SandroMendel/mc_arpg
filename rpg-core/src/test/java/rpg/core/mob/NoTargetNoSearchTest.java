package rpg.core.mob;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * FR-037: eine Kreatur ohne Ziel in Reichweite darf keine Zielsuche durchfuehren.
 *
 * <p>Vanillas eigene Suche erfuellt das schon ueber {@code Attribute.FOLLOW_RANGE} (research.md
 * R6, package-info von {@code rpg.platform.mob}) - dort gibt es nichts, was dieser Block noch
 * pruefen koennte, ohne Bukkit nachzubauen. Was diesem Block gehoert, ist die EINE eigene
 * Zielzuweisung ({@link RetargetThrottle}, fuer den Klon aus US7): ein Aufrufer ohne Kandidaten
 * ruft {@link RetargetThrottle#retargeted} gar nicht erst auf, und {@link
 * RetargetThrottle#mayRetarget} allein ist eine reine Abfrage ohne Nebenwirkung - beliebig oft
 * fragen, ohne dass je einer zugewiesen hat, aendert nichts und kostet nichts.
 */
class NoTargetNoSearchTest {

    @Test
    @DisplayName("mayRetarget allein veraendert nichts - erst retargeted() zaehlt als Zuweisung")
    void askingAloneChangesNothingOnlyRetargetedCountsAsAnAssignment() {
        RetargetThrottle throttle = new RetargetThrottle();
        Duration interval = Duration.ofMillis(500);

        for (int i = 0; i < 1000; i++) {
            assertThat(throttle.mayRetarget(Instant.now(), interval)).isTrue();
        }

        assertThat(throttle.lastRetargetAt())
                .as("tausend Abfragen ohne einen einzigen Kandidaten haben nichts zugewiesen")
                .isEmpty();
    }

    @Test
    @DisplayName("kein Kandidat in tausend Runden heisst: die naechste echte Gelegenheit ist nicht gedrosselt")
    void noCandidateOverAThousandRoundsMeansTheNextRealChanceIsNotThrottled() {
        RetargetThrottle throttle = new RetargetThrottle();
        Duration interval = Duration.ofMillis(500);
        Instant now = Instant.parse("2026-08-26T20:00:00Z");

        // Tausend Ticks ohne Kandidaten: der Aufrufer haette RetargetThrottle nie gefragt. Selbst
        // wenn doch, aendert das Fragen allein nichts - dieselbe Zusicherung wie oben, nur ueber
        // die Zeit statt ueber die Wiederholung geprueft.
        for (int i = 0; i < 1000; i++) {
            now = now.plusMillis(1);
        }

        assertThat(throttle.mayRetarget(now, interval))
                .as("ohne je zugewiesen zu haben, darf die erste echte Gelegenheit sofort zugreifen")
                .isTrue();
    }
}
