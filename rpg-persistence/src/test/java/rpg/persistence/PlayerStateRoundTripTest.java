package rpg.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.Optional;
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
 * T031 / User Story 1: state written by one run is read back intact by the next.
 *
 * <p>Runs against a real PostgreSQL container (Constitution VII). A mock would happily confirm the
 * write and prove nothing about the {@code ON CONFLICT} statement, which is where the actual
 * behaviour lives.
 */
class PlayerStateRoundTripTest {

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
    void aWrittenStateIsReadBackAfterAFreshStart() throws Exception {
        UUID playerId = UUID.randomUUID();
        harness.playerStates.put(PlayerState.initial(playerId, Instant.now()));

        FlushResult result = harness.flushCycle.flushNow(FlushReason.SESSION_END).get();
        assertThat(result.complete()).isTrue();
        assertThat(result.written()).isEqualTo(1);

        // A fresh stack, as after a restart - nothing carried over in memory.
        harness.close();
        harness = new PersistenceHarness();

        Optional<PlayerState> reloaded = harness.playerStates.load(playerId).get();

        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().playerId()).isEqualTo(playerId);
        assertThat(reloaded.get().revision()).isEqualTo(1L);
    }

    @Test
    void anUnknownPlayerIsEmptyRatherThanAnError() throws Exception {
        // "never seen before" and "could not be read" must not look the same to the login path.
        assertThat(harness.playerStates.load(UUID.randomUUID()).get()).isEmpty();
    }

    @Test
    void repeatedFlushesAdvanceTheRevisionExactlyOncePerFlush() throws Exception {
        UUID playerId = UUID.randomUUID();
        harness.playerStates.put(PlayerState.initial(playerId, Instant.now()));

        harness.flushCycle.flushNow(FlushReason.INTERVAL).get();
        harness.playerStates.markDirty(playerId);
        harness.flushCycle.flushNow(FlushReason.INTERVAL).get();
        harness.playerStates.markDirty(playerId);
        harness.flushCycle.flushNow(FlushReason.INTERVAL).get();

        assertThat(storedRevision(playerId)).isEqualTo(3L);
    }

    @Test
    void aFlushWithNothingPendingWritesNothing() throws Exception {
        FlushResult result = harness.flushCycle.flushNow(FlushReason.INTERVAL).get();

        assertThat(result.written()).isZero();
        assertThat(result.complete()).isTrue();
    }

    @Test
    void manyChangesBetweenFlushesStillProduceOneRow() throws Exception {
        UUID playerId = UUID.randomUUID();
        harness.playerStates.put(PlayerState.initial(playerId, Instant.now()));
        for (int i = 0; i < 500; i++) {
            harness.playerStates.markDirty(playerId);
        }

        FlushResult result = harness.flushCycle.flushNow(FlushReason.INTERVAL).get();

        // 500 marks, one write - the coalescing property, proven end to end against the database.
        assertThat(result.written()).isEqualTo(1);
        assertThat(storedRevision(playerId)).isEqualTo(1L);
    }

    private long storedRevision(UUID playerId) throws Exception {
        try (Connection connection = PostgresContainer.openConnection();
                PreparedStatement statement =
                        connection.prepareStatement(
                                "SELECT revision FROM rpg.player_state WHERE player_id = ?")) {
            statement.setObject(1, playerId);
            try (ResultSet rows = statement.executeQuery()) {
                assertThat(rows.next()).as("player row must exist").isTrue();
                return rows.getLong(1);
            }
        }
    }
}
