package rpg.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import rpg.core.persistence.PersistenceConfig;
import rpg.core.persistence.PlayerState;
import rpg.persistence.support.PersistenceHarness;
import rpg.persistence.support.PostgresContainer;

/**
 * T034 / SC-002 / SC-011 / FR-011, FR-011a: the final flush writes everything and stays inside its
 * budget.
 */
class ShutdownFlushTest {

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
    void anOrderlyShutdownLosesNothing() throws Exception {
        UUID[] players = new UUID[25];
        for (int i = 0; i < players.length; i++) {
            players[i] = UUID.randomUUID();
            harness.playerStates.put(PlayerState.initial(players[i], Instant.now()));
        }

        harness.flushCycle.shutdownFlush();

        assertThat(harness.buffer.pending()).isZero();
        for (UUID player : players) {
            assertThat(harness.playerStates.load(player).get()).isPresent();
        }
    }

    @Test
    void theShutdownFlushStaysWellInsideItsBudget() {
        for (int i = 0; i < 200; i++) {
            harness.playerStates.put(PlayerState.initial(UUID.randomUUID(), Instant.now()));
        }

        long startedAt = System.nanoTime();
        harness.flushCycle.shutdownFlush();
        Duration took = Duration.ofNanos(System.nanoTime() - startedAt);

        // 8s budget, itself inside B01's 10s per-module allowance. 200 players should be orders of
        // magnitude below it against a local database.
        assertThat(took).isLessThan(PersistenceConfig.MAX_SHUTDOWN_FLUSH);
    }

    @Test
    void aShutdownFlushWithAnUnreachableDatabaseTerminatesRatherThanHanging() {
        harness.playerStates.put(PlayerState.initial(UUID.randomUUID(), Instant.now()));
        harness.pools.close();

        long startedAt = System.nanoTime();
        harness.flushCycle.shutdownFlush();
        Duration took = Duration.ofNanos(System.nanoTime() - startedAt);

        // The whole point of the budget: the server stop must not hang on a dead database.
        assertThat(took).isLessThan(PersistenceConfig.MODULE_SHUTDOWN_BUDGET);
        // And the unwritten changes are still there rather than silently gone.
        assertThat(harness.buffer.pending()).isPositive();
    }

    @Test
    void theConfiguredBudgetCanNeverExceedTheModuleAllowance() {
        // A guard on the guard: if someone widened the cap, this fails before production does.
        assertThat(PersistenceConfig.MAX_SHUTDOWN_FLUSH)
                .isLessThan(PersistenceConfig.MODULE_SHUTDOWN_BUDGET);
    }
}
