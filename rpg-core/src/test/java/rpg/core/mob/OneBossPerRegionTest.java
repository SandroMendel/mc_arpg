package rpg.core.mob;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * FR-029: eine Region laesst hoechstens einen lebenden Boss zu - kein zweiter, solange der erste
 * lebt, ganz gleich, wie viel Zeit vergangen ist.
 */
class OneBossPerRegionTest {

    private final Duration respawn = Duration.ofMinutes(30);

    @Test
    @DisplayName("ohne einen lebenden Boss darf der erste erscheinen")
    void withNobodyAliveTheFirstMayAppear() {
        BossState state = new BossState("greenfields");

        assertThat(state.mayAppear(Instant.parse("2026-08-26T20:00:00Z"), respawn)).isTrue();
        assertThat(state.alive()).isEmpty();
    }

    @Test
    @DisplayName("solange einer lebt, darf kein zweiter erscheinen - auch nicht nach der Respawnzeit")
    void whileOneIsAliveNoSecondOneMayAppearEvenAfterTheRespawnTime() {
        BossState state = new BossState("greenfields");
        UUID alive = UUID.randomUUID();
        Instant placedAt = Instant.parse("2026-08-26T20:00:00Z");
        state.placed(alive);

        assertThat(state.mayAppear(placedAt, respawn)).isFalse();
        assertThat(state.mayAppear(placedAt.plus(respawn).plus(Duration.ofDays(1)), respawn))
                .as("kein Timer laeuft, solange einer lebt - er ist nicht gefallen")
                .isFalse();
        assertThat(state.alive()).contains(alive);
    }

    @Test
    @DisplayName("zwei Regionen fuehren getrennte Zustaende - der eine beeinflusst den anderen nicht")
    void twoRegionsHaveIndependentState() {
        BossState greenfields = new BossState("greenfields");
        BossState dustlands = new BossState("dustlands");
        greenfields.placed(UUID.randomUUID());

        assertThat(greenfields.mayAppear(Instant.now(), respawn)).isFalse();
        assertThat(dustlands.mayAppear(Instant.now(), respawn))
                .as("dustlands hat noch nie einen Boss gesehen")
                .isTrue();
    }
}
