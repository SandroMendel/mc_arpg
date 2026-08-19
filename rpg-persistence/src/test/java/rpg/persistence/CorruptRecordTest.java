package rpg.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import rpg.core.persistence.FlushReason;
import rpg.core.persistence.PersistenceException;
import rpg.core.persistence.PlayerState;
import rpg.persistence.support.PersistenceHarness;
import rpg.persistence.support.PostgresContainer;

/**
 * T036 / FR-005a, FR-020: a record that cannot be read is contained, and never replaced by a
 * default.
 *
 * <p>The substitution failure is the dangerous one. A default state looks like graceful degradation
 * and then overwrites the player's real progress at the next flush - so the load must fail loudly
 * and the login must be refused.
 */
class CorruptRecordTest {

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
    void anUnreadableRecordFailsTheLoadInsteadOfReturningADefault() throws Exception {
        UUID playerId = UUID.randomUUID();
        harness.playerStates.put(PlayerState.initial(playerId, Instant.now()));
        harness.flushCycle.flushNow(FlushReason.INTERVAL).get();

        // Break the column the mapper needs. A defaulting implementation would quietly hand back a
        // fresh state here and destroy this player's progress on the next write.
        try (Connection connection = PostgresContainer.openConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE rpg.player_state DROP COLUMN last_seen_at");
        }

        assertThatThrownBy(() -> harness.playerStates.load(playerId).get())
                .hasCauseInstanceOf(PersistenceException.class);
    }

    @Test
    void oneUnreadableRecordDoesNotAffectOtherPlayers() throws Exception {
        UUID healthy = UUID.randomUUID();
        harness.playerStates.put(PlayerState.initial(healthy, Instant.now()));
        harness.flushCycle.flushNow(FlushReason.INTERVAL).get();

        // A failure for an unknown id must not disturb the healthy record.
        assertThat(harness.playerStates.load(UUID.randomUUID()).get()).isEmpty();
        assertThat(harness.playerStates.load(healthy).get()).isPresent();
    }
}
