package rpg.core.mob;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * FR-031, FR-032: nach dem Tod eines Bosses laeuft ein Respawn-Timer, vor dessen Ablauf entsteht
 * kein zweiter - und der Timer wird aus zwei Zeitstempeln gerechnet, nicht ueber eine laufende
 * Aufgabe (Prinzip II).
 */
class BossRespawnTimerTest {

    private final Duration respawn = Duration.ofMinutes(30);

    @Test
    @DisplayName("direkt nach dem Tod darf noch keiner erscheinen")
    void rightAfterDeathNobodyMayAppearYet() {
        BossState state = new BossState("greenfields");
        Instant diedAt = Instant.parse("2026-08-26T20:00:00Z");

        state.killed(diedAt);

        assertThat(state.mayAppear(diedAt, respawn)).isFalse();
        assertThat(state.mayAppear(diedAt.plus(respawn).minusMillis(1), respawn))
                .as("eine Millisekunde vor Ablauf ist noch nicht Ablauf")
                .isFalse();
    }

    @Test
    @DisplayName("mit Ablauf der Respawnzeit darf ein neuer erscheinen")
    void onceTheRespawnTimeHasPassedANewOneMayAppear() {
        BossState state = new BossState("greenfields");
        Instant diedAt = Instant.parse("2026-08-26T20:00:00Z");

        state.killed(diedAt);

        assertThat(state.mayAppear(diedAt.plus(respawn), respawn)).isTrue();
        assertThat(state.mayAppear(diedAt.plus(respawn).plusSeconds(1), respawn)).isTrue();
    }

    @Test
    @DisplayName("ein frischer Server ohne je gefallenen Boss zeigt ihn sofort")
    void aFreshServerWithNoDeathEverShowsHimImmediately() {
        BossState state = new BossState("greenfields");

        assertThat(state.mayAppear(Instant.now(), respawn))
                .as("kein Tod, keine Wartezeit - sonst braeuchte ein frischer Server 30 Minuten")
                .isTrue();
        assertThat(state.lastKilledAt()).isEmpty();
    }

    @Test
    @DisplayName("der Timer haengt an zwei Zeitstempeln, nicht an einer laufenden Aufgabe (FR-032)")
    void theTimerIsTwoTimestampsNotARunningTask() {
        for (Field field : BossState.class.getDeclaredFields()) {
            assertThat(rpg.core.scheduler.Scheduler.class.isAssignableFrom(field.getType()))
                    .as(field.getName() + " haelt keinen Scheduler - der Timer ist lazy ausgewertet")
                    .isFalse();
            assertThat(Runnable.class.isAssignableFrom(field.getType()))
                    .as(field.getName() + " haelt keine wiederkehrende Aufgabe")
                    .isFalse();
        }
        assertThat(BossState.class.getDeclaredFields()).hasSize(3);
    }
}
