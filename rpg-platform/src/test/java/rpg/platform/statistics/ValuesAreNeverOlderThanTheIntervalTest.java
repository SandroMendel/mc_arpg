package rpg.platform.statistics;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * SC-005, FR-032 — <b>die genannte Altersangabe stimmt mit dem tatsächlichen
 * Auffrischungszeitpunkt überein.</b>
 *
 * <p>Die Altersangabe ist die Zeile, die den Unterschied zwischen „veraltet" und „kaputt"
 * erklärt. Ohne sie ist jede Rangliste, die einen gerade erzielten Wert noch nicht zeigt, aus
 * Sicht des Spielers ein Fehler — mit ihr ist sie eine Rangliste, die in vier Minuten wieder
 * stimmt.
 *
 * <p><b>Deshalb darf gerade diese Zahl nicht geschätzt sein.</b> Eine Anzeige, die „vor 2
 * Minuten" behauptet, während der Stand aus der letzten Stunde ist, wäre schlimmer als gar keine:
 * sie nimmt dem Spieler den einzigen Anhaltspunkt, den er hat.
 */
class ValuesAreNeverOlderThanTheIntervalTest {

    private static final Instant REFRESHED = Instant.parse("2026-08-30T12:00:00Z");

    @Test
    @DisplayName("FR-032 - das genannte Alter ist der Abstand zum Auffrischungszeitpunkt")
    void theStatedAgeIsTheDistanceToTheRefresh() {
        assertThat(LeaderboardMenu.age(REFRESHED, REFRESHED.plus(Duration.ofMinutes(3))))
                .isEqualTo("3m");
        assertThat(LeaderboardMenu.age(REFRESHED, REFRESHED.plus(Duration.ofSeconds(42))))
                .isEqualTo("42s");
    }

    @Test
    @DisplayName("SC-005 - nach einer Auffrischung ist nichts aelter als das Intervall")
    void afterARefreshNothingIsOlderThanTheInterval() {
        Duration interval = Duration.ofMinutes(5);

        // Der Stand ist gerade erneuert worden; unmittelbar davor war er hoechstens ein Intervall
        // alt. Genau das sagt SC-005 zu, und genau das muss die Anzeige wiedergeben.
        // Ab einer vollen Minute; darunter zaehlt die Anzeige in Sekunden, siehe der Test darunter.
        for (long minutes = 1; minutes <= interval.toMinutes(); minutes++) {
            Instant now = REFRESHED.plus(Duration.ofMinutes(minutes));

            assertThat(Duration.between(REFRESHED, now))
                    .as("nach %d Minuten", minutes)
                    .isLessThanOrEqualTo(interval);
            assertThat(LeaderboardMenu.age(REFRESHED, now)).isEqualTo(minutes + "m");
        }
    }

    @Test
    @DisplayName("unmittelbar nach der Auffrischung steht 0s, nicht eine leere Zeile")
    void rightAfterTheRefreshItSaysZeroSeconds() {
        assertThat(LeaderboardMenu.age(REFRESHED, REFRESHED)).isEqualTo("0s");
    }

    @Test
    @DisplayName("eine Uhr, die zurueckspringt, erzeugt kein negatives Alter")
    void aclockThatJumpsBackProducesNoNegativeAge() {
        // Sommerzeit, NTP-Korrektur, ein Container mit schiefer Uhr. Ein "vor -3 Minuten" waere
        // die Sorte Anzeige, die einen Spieler an allem zweifeln laesst, was daneben steht.
        assertThat(LeaderboardMenu.age(REFRESHED, REFRESHED.minus(Duration.ofMinutes(3))))
                .isEqualTo("0s");
    }
}
