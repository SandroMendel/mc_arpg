package rpg.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import rpg.core.persistence.FlushReason;
import rpg.core.persistence.PlayerState;
import rpg.persistence.support.PersistenceHarness;
import rpg.persistence.support.PostgresContainer;

/**
 * T035 / SC-007 / FR-019a, FR-019c: a player who leaves and immediately returns gets their current
 * state, not the previous one.
 *
 * <p>This is the rollback-and-duplication failure Minecraft servers are known for. The fix is
 * ordering - flush what the old session owes before reading - and a bound on the wait so a stuck
 * flush refuses the login instead of hanging it.
 */
class SessionHandoverTest {

    private static final Logger QUIET = Logger.getLogger("handover-test");

    private PersistenceHarness harness;
    private SessionHandover handover;

    @BeforeEach
    void setUp() {
        QUIET.setLevel(Level.OFF);
        PostgresContainer.resetSchema();
        harness = new PersistenceHarness();
        handover =
                new SessionHandover(
                        harness.playerStates, harness.flushCycle, Duration.ofSeconds(5), QUIET);
    }

    @AfterEach
    void tearDown() {
        if (harness != null) {
            harness.close();
        }
    }

    @Test
    void aReturningPlayerReceivesTheCurrentStateNotTheOlderStoredOne() throws Exception {
        UUID playerId = UUID.randomUUID();
        harness.playerStates.put(PlayerState.initial(playerId, Instant.now()));
        harness.flushCycle.flushNow(FlushReason.INTERVAL).get(); // revision 1 stored

        // The old session makes more progress, still unwritten...
        harness.playerStates.markDirty(playerId);

        // ... and the player reconnects right away.
        Optional<PlayerState> loaded = handover.loadForNewSession(playerId).get();

        assertThat(loaded).isPresent();
        // Revision 2, not 1: the pending write was drained before the read.
        assertThat(loaded.get().revision()).isEqualTo(2L);
        assertThat(harness.buffer.pending()).isZero();
    }

    @Test
    void aFirstTimePlayerIsSimplyUnknown() throws Exception {
        assertThat(handover.loadForNewSession(UUID.randomUUID()).get()).isEmpty();
    }

    @Test
    void aFailingFlushRefusesTheLoginRatherThanServingStaleState() {
        UUID playerId = UUID.randomUUID();
        harness.playerStates.put(PlayerState.initial(playerId, Instant.now()));
        harness.pools.close(); // the pending write can no longer be flushed

        // Refusing is recoverable; handing over state a failed write was about to change is not.
        assertThatThrownBy(() -> handover.loadForNewSession(playerId).get())
                .hasRootCauseInstanceOf(rpg.core.persistence.PersistenceException.class);
    }

    @Test
    void nothingIsLostWhenTheHandoverSucceeds() throws Exception {
        UUID playerId = UUID.randomUUID();
        harness.playerStates.put(PlayerState.initial(playerId, Instant.now()));

        handover.onSessionEnd(playerId).get();
        Optional<PlayerState> reconnected = handover.loadForNewSession(playerId).get();

        assertThat(reconnected).isPresent();
        assertThat(reconnected.get().playerId()).isEqualTo(playerId);
    }
}
