package rpg.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.persistence.AggregateType;
import rpg.core.persistence.FlushReason;
import rpg.core.persistence.PlayerState;
import rpg.persistence.support.PersistenceHarness;
import rpg.persistence.support.PostgresContainer;

/**
 * Game events mark; only a flush writes (SC-005, Principle II).
 *
 * <p>The number under test is a ratio: accesses per unit of time against game events per unit of
 * time. Principle II forbids a database access per game event outright, because at 150 players under
 * combat load that ratio is what decides whether the server holds 20 TPS.
 *
 * <p>What makes this measurable rather than a matter of belief: the write-behind buffer counts what is
 * pending, and a flush reports what it wrote. Ten thousand marks and one flush is one write cycle -
 * not ten thousand.
 */
class NoDatabaseAccessPerGameEventTest {

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
    @DisplayName("ten thousand marks on one aggregate collapse into a single pending entry")
    void repeatedMarksCollapse() {
        UUID playerId = UUID.randomUUID();

        for (int i = 0; i < 10_000; i++) {
            harness.flushCycle.markDirty(AggregateType.PLAYER_STATE, playerId.toString());
        }

        // The buffer is a set of identities, not a queue of events. Ten thousand hits on the same
        // player are one row to write - which is the whole reason a game event may not write itself.
        assertThat(harness.buffer.pending()).isEqualTo(1);
    }

    @Test
    @DisplayName("marking never touches the database - the buffer holds everything until a flush")
    void markingDoesNotWrite() throws Exception {
        int players = 200;
        for (int i = 0; i < players; i++) {
            UUID playerId = UUID.randomUUID();
            harness.playerStates.put(PlayerState.initial(playerId, Instant.now()));
            // Five "game events" each: the ratio that matters is events to accesses.
            for (int event = 0; event < 5; event++) {
                harness.flushCycle.markDirty(AggregateType.PLAYER_STATE, playerId.toString());
            }
        }

        assertThat(harness.buffer.pending())
                .as("1000 events, 200 rows pending - nothing written yet")
                .isEqualTo(players);
        assertThat(rowCount()).as("SC-005: not one access from a game event").isZero();

        var result = harness.flushCycle.flushNow(FlushReason.INTERVAL).get();

        assertThat(result.written()).isEqualTo(players);
        assertThat(rowCount()).isEqualTo(players);
    }

    @Test
    @DisplayName("the ratio holds as the event count grows: accesses stay at one cycle")
    void ratioHoldsAsEventsGrow() throws Exception {
        UUID playerId = UUID.randomUUID();
        harness.playerStates.put(PlayerState.initial(playerId, Instant.now()));

        for (int events : new int[] {10, 100, 1_000, 10_000}) {
            for (int i = 0; i < events; i++) {
                harness.flushCycle.markDirty(AggregateType.PLAYER_STATE, playerId.toString());
            }
            var result = harness.flushCycle.flushNow(FlushReason.INTERVAL).get();

            // However many events arrive between two flushes, the cost is one row per aggregate.
            assertThat(result.written())
                    .as(events + " events must still cost one write")
                    .isEqualTo(1);
        }
    }

    @Test
    @DisplayName("a flush with nothing pending does no work at all")
    void emptyFlushIsFree() throws Exception {
        var result = harness.flushCycle.flushNow(FlushReason.INTERVAL).get();

        // The autosave cycle runs on a quiet server too; it has to cost nothing when nothing changed.
        assertThat(result.written()).isZero();
        assertThat(result.failed()).isZero();
    }

    @Test
    @DisplayName("no aggregate type is missing from the write order")
    void everyAggregateTypeIsInTheWriteOrder() {
        // A type absent from WRITE_ORDER has its marks counted as failed on every flush and is never
        // written - which looks like a database fault and is a forgotten line. B06 hit exactly that
        // with CHARACTER_PROGRESS, so the invariant is checked against the enum rather than against
        // the types somebody remembered.
        assertThat(FlushCycle.writeOrder())
                .as("adding a value to AggregateType is not enough; WRITE_ORDER has to list it")
                .containsExactlyInAnyOrder(AggregateType.values());
    }

    @Test
    @DisplayName("the write order puts a child after its parent")
    void writeOrderRespectsForeignKeys() {
        var order = FlushCycle.writeOrder();

        // The foreign keys demand it: a character references an account, and items, resources and
        // progress reference a character. Writing a child first fails against a row that does not
        // exist yet.
        assertThat(order.indexOf(AggregateType.PLAYER_STATE))
                .isLessThan(order.indexOf(AggregateType.CHARACTER));
        for (AggregateType child :
                new AggregateType[] {
                    AggregateType.CHARACTER_STATS,
                    AggregateType.CHARACTER_PROGRESS,
                    AggregateType.ITEM_INSTANCE
                }) {
            assertThat(order.indexOf(child))
                    .as(child + " must come after CHARACTER")
                    .isGreaterThan(order.indexOf(AggregateType.CHARACTER));
        }
    }

    private int rowCount() throws Exception {
        try (var connection = PostgresContainer.openConnection();
                var statement = connection.createStatement();
                var rows = statement.executeQuery("SELECT count(*) FROM rpg.player_state")) {
            rows.next();
            return rows.getInt(1);
        }
    }
}
