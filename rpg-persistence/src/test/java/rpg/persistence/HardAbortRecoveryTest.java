package rpg.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import rpg.core.persistence.FlushReason;
import rpg.core.persistence.PlayerState;
import rpg.persistence.support.PersistenceHarness;
import rpg.persistence.support.PostgresContainer;

/**
 * T032 / SC-001: after a hard process abort, at most the changes since the last completed write are
 * lost - never more.
 *
 * <p>The abort is simulated by discarding the stack without flushing, which is exactly what a
 * {@code kill -9} does to the buffer: whatever was written is in the database, whatever was only
 * marked is gone. Asserting the boundary matters, because "we lose at most one interval" is the
 * promise this whole block is built to keep, and it is the one that fails silently if the write
 * path is subtly wrong.
 */
class HardAbortRecoveryTest {

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
    void everythingWrittenBeforeTheAbortSurvivesIt() throws Exception {
        UUID playerId = UUID.randomUUID();
        harness.playerStates.put(PlayerState.initial(playerId, Instant.now()));
        harness.flushCycle.flushNow(FlushReason.INTERVAL).get(); // the last completed autosave

        // ... more progress, never flushed ...
        harness.playerStates.markDirty(playerId);
        assertThat(harness.buffer.pending()).isEqualTo(1);

        abortWithoutFlushing();

        Optional<PlayerState> afterRestart = harness.playerStates.load(playerId).get();
        assertThat(afterRestart).isPresent();
        assertThat(afterRestart.get().revision()).isEqualTo(1L);
    }

    @Test
    void onlyTheUnflushedChangesAreLost() throws Exception {
        UUID persisted = UUID.randomUUID();
        UUID lost = UUID.randomUUID();

        harness.playerStates.put(PlayerState.initial(persisted, Instant.now()));
        harness.flushCycle.flushNow(FlushReason.INTERVAL).get();

        // Marked after the last flush - this one is the acceptable loss.
        harness.playerStates.put(PlayerState.initial(lost, Instant.now()));

        abortWithoutFlushing();

        assertThat(harness.playerStates.load(persisted).get()).isPresent();
        assertThat(harness.playerStates.load(lost).get()).isEmpty();
    }

    @Test
    void aPlayerWhoNeverReachedAFlushIsSimplyUnknownAfterwards() throws Exception {
        UUID playerId = UUID.randomUUID();
        harness.playerStates.put(PlayerState.initial(playerId, Instant.now()));

        abortWithoutFlushing();

        // Not a corrupt half-record: the player is simply new again, which is the correct outcome.
        assertThat(harness.playerStates.load(playerId).get()).isEmpty();
    }

    /** Drops the whole stack without flushing - the buffer's contents are gone, as after a kill. */
    private void abortWithoutFlushing() {
        harness.close();
        harness = new PersistenceHarness();
    }
}
