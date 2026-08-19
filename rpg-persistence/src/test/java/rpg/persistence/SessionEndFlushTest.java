package rpg.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import rpg.core.persistence.PlayerState;
import rpg.persistence.support.PersistenceHarness;
import rpg.persistence.support.PostgresContainer;

/**
 * T033 / FR-004: a leaving player's changes are written immediately, independent of the interval.
 */
class SessionEndFlushTest {

    private PersistenceHarness harness;

    @BeforeEach
    void setUp() {
        PostgresContainer.resetSchema();
        harness = new PersistenceHarness();
    }

    @AfterEach
    void tearDown() {
        if (harness != null) {
            harness.close();
        }
    }

    @Test
    void leavingWritesWithoutWaitingForTheInterval() throws Exception {
        UUID playerId = UUID.randomUUID();
        harness.playerStates.put(PlayerState.initial(playerId, Instant.now()));

        // The autosave interval is 45s and no interval cycle is running in the harness; if the
        // session-end trigger did not write, nothing would.
        new SessionHandover(
                        harness.playerStates,
                        harness.flushCycle,
                        SessionHandover.DEFAULT_WAIT,
                        java.util.logging.Logger.getLogger("test"))
                .onSessionEnd(playerId)
                .get();

        assertThat(harness.buffer.pending()).isZero();
        assertThat(harness.playerStates.load(playerId).get()).isPresent();
    }

    @Test
    void theCacheEntryIsReleasedOnceTheFinalWriteSucceeded() throws Exception {
        UUID playerId = UUID.randomUUID();
        harness.playerStates.put(PlayerState.initial(playerId, Instant.now()));

        new SessionHandover(
                        harness.playerStates,
                        harness.flushCycle,
                        SessionHandover.DEFAULT_WAIT,
                        java.util.logging.Logger.getLogger("test"))
                .onSessionEnd(playerId)
                .get();

        // Keeping it would leak memory for every player who ever connected.
        assertThat(harness.playerStates.cached(playerId)).isEmpty();
    }
}
