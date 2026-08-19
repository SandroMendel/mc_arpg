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
 * T051 / T064 / User Story 3: a real outage costs no progress.
 *
 * <p>The outage is produced by closing the pools, which makes every subsequent write fail against a
 * real driver and a real database. That covers the behaviour this block owns: marks are kept, the
 * flush does not throw outwards, and recovery writes everything through.
 *
 * <p>What it deliberately does <em>not</em> cover is stopping the container itself. The suite shares
 * one container, so restarting it here would reach into every other test's environment. The
 * driver's own behaviour when a server vanishes mid-connection is therefore not exercised - that
 * belongs in the operational validation of quickstart.md section 4, against a dedicated instance.
 */
class OutageRecoveryTest {

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
    void marksSurviveAFailedFlushAndAreWrittenAfterwards() throws Exception {
        UUID playerId = UUID.randomUUID();
        harness.playerStates.put(PlayerState.initial(playerId, Instant.now()));

        // Close the pools underneath the cycle: every write now fails, exactly as during an outage.
        harness.pools.close();

        FlushResult failed = harness.flushCycle.flushNow(FlushReason.INTERVAL).get();

        assertThat(failed.complete()).isFalse();
        assertThat(failed.written()).isZero();
        // Nothing discarded - the whole point of FR-009.
        assertThat(harness.buffer.pending()).isEqualTo(1);
        assertThat(harness.outageState.isReachable()).isFalse();
    }

    @Test
    void aFailedFlushDoesNotThrowOutwards() throws Exception {
        harness.playerStates.put(PlayerState.initial(UUID.randomUUID(), Instant.now()));
        harness.pools.close();

        // If this threw, the self-rescheduling interval cycle would die with it and persistence
        // would stand still for the rest of the outage.
        FlushResult result = harness.flushCycle.flushNow(FlushReason.INTERVAL).get();

        assertThat(result).isNotNull();
        assertThat(result.failed()).isPositive();
    }

    @Test
    void everythingBufferedDuringTheOutageIsWrittenOnRecovery() throws Exception {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        harness.playerStates.put(PlayerState.initial(first, Instant.now()));
        harness.flushCycle.flushNow(FlushReason.INTERVAL).get();

        // --- outage ---
        harness.pools.close();
        harness.playerStates.put(PlayerState.initial(second, Instant.now()));
        harness.flushCycle.flushNow(FlushReason.INTERVAL).get();
        assertThat(harness.buffer.pending()).isPositive();

        // --- recovery: a new stack against the same, still-running database ---
        PersistenceHarness recovered = new PersistenceHarness();
        try {
            recovered.playerStates.put(PlayerState.initial(second, Instant.now()));
            FlushResult result = recovered.flushCycle.flushNow(FlushReason.RECOVERY).get();

            assertThat(result.complete()).isTrue();
            assertThat(recovered.playerStates.load(first).get()).isPresent();
            assertThat(recovered.playerStates.load(second).get()).isPresent();
            assertThat(recovered.outageState.isReachable()).isTrue();
        } finally {
            recovered.close();
        }
    }

    @Test
    void theOutageStateOnlyClearsAfterAWriteActuallySucceeded() throws Exception {
        harness.playerStates.put(PlayerState.initial(UUID.randomUUID(), Instant.now()));
        harness.pools.close();
        harness.flushCycle.flushNow(FlushReason.INTERVAL).get();

        assertThat(harness.outageState.isReachable()).isFalse();
        assertThat(harness.outageState.consecutiveFailures()).isPositive();

        // Merely being able to open a connection is not enough - only a successful write clears it.
        assertThat(harness.outageState.nextRetryDelay()).isNotNull();
        assertThat(harness.outageState.outageDuration()).isGreaterThanOrEqualTo(java.time.Duration.ZERO);
    }
}
