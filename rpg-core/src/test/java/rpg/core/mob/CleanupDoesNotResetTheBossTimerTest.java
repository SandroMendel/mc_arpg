package rpg.core.mob;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * FR-034: ein Boss wird beim Aufraeumen wie jede andere Kreatur behandelt, aber sein
 * Respawn-Timer bleibt davon unberuehrt - aufgeraeumt ist nicht gefallen.
 */
class CleanupDoesNotResetTheBossTimerTest {

    private final Duration respawn = Duration.ofMinutes(30);

    @Test
    @DisplayName("aufgeraeumt ist nicht gefallen - kein Timer beginnt, ein neuer darf sofort")
    void cleanedUpIsNotKilledNoTimerStartsANewOneMayAppearImmediately() {
        BossState state = new BossState("greenfields");
        state.placed(UUID.randomUUID());

        state.cleanedUp();

        assertThat(state.alive()).as("er ist weg").isEmpty();
        assertThat(state.lastKilledAt())
                .as("aber er ist nicht gefallen - kein Zeitstempel, kein Timer")
                .isEmpty();
        assertThat(state.mayAppear(Instant.parse("2026-08-26T20:00:00Z"), respawn))
                .as("ohne Timer darf sofort ein neuer erscheinen")
                .isTrue();
    }

    @Test
    @DisplayName("ein bereits laufender Respawn-Timer nach einem echten Tod bleibt von einem spaeteren Aufraeumen unberuehrt")
    void aRunningTimerAfterARealDeathSurvivesALaterCleanup() {
        BossState state = new BossState("greenfields");
        Instant diedAt = Instant.parse("2026-08-26T20:00:00Z");
        state.killed(diedAt);

        // Ein neuer erscheint, und wird dann - waehrend der Timer eigentlich schon lief -
        // aufgeraeumt statt getoetet. Der urspruengliche Zeitstempel darf nicht verschwinden.
        state.placed(UUID.randomUUID());
        state.cleanedUp();

        assertThat(state.mayAppear(diedAt.plus(respawn).minusMillis(1), respawn))
                .as("der Timer aus dem echten Tod laeuft unveraendert weiter")
                .isFalse();
        assertThat(state.mayAppear(diedAt.plus(respawn), respawn)).isTrue();
    }
}
