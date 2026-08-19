package rpg.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import rpg.core.persistence.FlushReason;
import rpg.core.persistence.PlayerState;
import rpg.persistence.support.PersistenceHarness;
import rpg.persistence.support.PostgresContainer;

/**
 * T054 / T065 / FR-008, FR-005a: the login path has its own connections, and a login during an
 * outage is refused rather than served a default.
 */
class LoginPoolIsolationTest {

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
    void theWritePoolAndTheLoginPoolAreDifferentPools() {
        // The structural claim behind FR-008: write load cannot reach the login connections at all,
        // so it cannot starve them however long a batch runs.
        assertThat(harness.pools.writePool()).isNotSameAs(harness.pools.loginPool());
    }

    @Test
    void everyLoginConnectionRemainsAvailableWhileTheWritePoolIsExhausted() throws Exception {
        // Hold every write connection open...
        List<Connection> held = new ArrayList<>();
        try {
            for (int i = 0; i < harness.config.writePoolSize(); i++) {
                held.add(harness.pools.writePool().getConnection());
            }

            // ... a login still gets a connection immediately.
            try (Connection login = harness.pools.loginPool().getConnection()) {
                assertThat(login.isValid(2)).isTrue();
            }
        } finally {
            for (Connection connection : held) {
                connection.close();
            }
        }
    }

    @Test
    void concurrentLoadsAllComplete() throws Exception {
        List<UUID> players = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            UUID id = UUID.randomUUID();
            players.add(id);
            harness.playerStates.put(PlayerState.initial(id, Instant.now()));
        }
        harness.flushCycle.flushNow(FlushReason.INTERVAL).get();

        List<CompletableFuture<?>> loads = new ArrayList<>();
        for (UUID id : players) {
            loads.add(harness.playerStates.load(id));
        }
        CompletableFuture.allOf(loads.toArray(new CompletableFuture[0])).get();

        assertThat(loads).allMatch(CompletableFuture::isDone);
        assertThat(loads).noneMatch(CompletableFuture::isCompletedExceptionally);
    }

    @Test
    void aLoadDuringAnOutageFailsRatherThanReturningADefaultState() {
        harness.pools.close();

        // FR-005a: the login path must turn this into a rejection. Returning a fresh state here
        // would overwrite the player's real progress at the next successful flush.
        assertThat(harness.playerStates.load(UUID.randomUUID()).isCompletedExceptionally()).isTrue();
    }
}
