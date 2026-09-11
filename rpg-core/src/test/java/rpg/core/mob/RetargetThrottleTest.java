package rpg.core.mob;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * FR-035: eine eigene Zielzuweisung fasst fruehestens nach dem konfigurierten Abstand wieder an -
 * derselbe zeitstempelbasierte Aufbau wie {@link BossState} (Prinzip II, keine laufende Aufgabe).
 */
class RetargetThrottleTest {

    private final Duration interval = Duration.ofMillis(500);

    @Test
    @DisplayName("eine frische Kreatur darf sofort zuweisen - kein Abstand ohne vorherige Zuweisung")
    void aFreshCreatureMayRetargetImmediately() {
        RetargetThrottle throttle = new RetargetThrottle();

        assertThat(throttle.mayRetarget(Instant.now(), interval)).isTrue();
        assertThat(throttle.lastRetargetAt()).isEmpty();
    }

    @Test
    @DisplayName("direkt nach einer Zuweisung ist der Abstand noch nicht um")
    void rightAfterAssigningTheIntervalHasNotPassedYet() {
        RetargetThrottle throttle = new RetargetThrottle();
        Instant assignedAt = Instant.parse("2026-08-26T20:00:00Z");

        throttle.retargeted(assignedAt);

        assertThat(throttle.mayRetarget(assignedAt, interval)).isFalse();
        assertThat(throttle.mayRetarget(assignedAt.plus(interval).minusMillis(1), interval)).isFalse();
    }

    @Test
    @DisplayName("mit Ablauf des Abstands darf wieder zugewiesen werden")
    void onceTheIntervalHasPassedItMayAssignAgain() {
        RetargetThrottle throttle = new RetargetThrottle();
        Instant assignedAt = Instant.parse("2026-08-26T20:00:00Z");

        throttle.retargeted(assignedAt);

        assertThat(throttle.mayRetarget(assignedAt.plus(interval), interval)).isTrue();
        assertThat(throttle.mayRetarget(assignedAt.plus(interval).plusMillis(1), interval)).isTrue();
    }
}
