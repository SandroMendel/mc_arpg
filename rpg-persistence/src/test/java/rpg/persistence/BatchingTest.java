package rpg.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import rpg.core.persistence.FlushReason;
import rpg.core.persistence.FlushResult;
import rpg.core.persistence.PlayerState;
import rpg.persistence.support.PersistenceHarness;
import rpg.persistence.support.PostgresContainer;

/**
 * T050 / T051 / SC-005: game events cost no database access, and a change made during a running
 * flush is not lost.
 */
class BatchingTest {

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
    void aThousandChangesProduceOneWrite() throws Exception {
        UUID playerId = UUID.randomUUID();
        harness.playerStates.put(PlayerState.initial(playerId, Instant.now()));

        int asyncBefore = harness.scheduler.asyncRuns();
        for (int i = 0; i < 1_000; i++) {
            harness.playerStates.markDirty(playerId);
        }

        // Measured on the scheduler rather than by timing: marking must not schedule anything at
        // all, which is what "no database access per game event" actually means.
        assertThat(harness.scheduler.asyncRuns()).isEqualTo(asyncBefore);

        FlushResult result = harness.flushCycle.flushNow(FlushReason.INTERVAL).get();
        assertThat(result.written()).isEqualTo(1);
    }

    @Test
    void manyAggregatesAreWrittenInOneFlush() throws Exception {
        for (int i = 0; i < 100; i++) {
            harness.playerStates.put(PlayerState.initial(UUID.randomUUID(), Instant.now()));
        }

        FlushResult result = harness.flushCycle.flushNow(FlushReason.INTERVAL).get();

        assertThat(result.written()).isEqualTo(100);
        assertThat(result.complete()).isTrue();
    }

    @Test
    void aChangeMadeAfterTheSnapshotLandsInTheNextFlush() throws Exception {
        UUID playerId = UUID.randomUUID();
        harness.playerStates.put(PlayerState.initial(playerId, Instant.now()));
        harness.flushCycle.flushNow(FlushReason.INTERVAL).get();

        // Change arrives after the previous flush cleared its snapshot.
        harness.playerStates.markDirty(playerId);
        assertThat(harness.buffer.pending()).isEqualTo(1);

        FlushResult second = harness.flushCycle.flushNow(FlushReason.INTERVAL).get();

        assertThat(second.written()).isEqualTo(1);
        assertThat(harness.buffer.pending()).isZero();
    }

    @Test
    void mixedAggregateTypesAreAllWritten() throws Exception {
        UUID playerId = UUID.randomUUID();
        harness.playerStates.put(PlayerState.initial(playerId, Instant.now()));
        harness.statistics.increment(playerId, "mob_kills", 5);
        harness.auditLog.append(
                new rpg.core.persistence.AuditEntry(
                        Instant.now(), "admin", "item_granted", java.util.Optional.of(playerId),
                        java.util.Map.of()));

        FlushResult result = harness.flushCycle.flushNow(FlushReason.INTERVAL).get();

        assertThat(result.complete()).isTrue();
        assertThat(harness.buffer.pending()).isZero();
        assertThat(harness.statistics.total(playerId, "mob_kills").get()).isEqualTo(5L);
    }
}
