package rpg.persistence.progression;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.persistence.AggregateType;
import rpg.core.persistence.FlushReason;
import rpg.core.progression.ProgressState;
import rpg.persistence.support.PersistenceHarness;
import rpg.persistence.support.PostgresContainer;

/**
 * The last gains of a session must reach the database (FR-056).
 *
 * <p><b>This test found a real bug.</b> The write-behind flush is asynchronous and normally runs
 * <em>after</em> the character was released. At that moment the live state is gone, so the writer had
 * nothing to write, dropped the mark, and the last experience of every single session was lost - a
 * loss no unit test in {@code rpg-core} could see, because it only appears where the release and the
 * flush meet. The fix is a stash filled before the release, exactly as B04 does for resources.
 */
class ProgressSessionEndFlushTest {

    private static final Logger QUIET = Logger.getLogger("progress-session-end-test");

    private PersistenceHarness harness;
    private JdbcCharacterProgressRepository repository;

    /** Stands in for the module's live source: the loaded state, falling back to the stash. */
    private final ConcurrentHashMap<UUID, ProgressState> live = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<UUID, ProgressState> stash = new ConcurrentHashMap<>();

    @BeforeEach
    void setUp() {
        QUIET.setLevel(Level.OFF);
        PostgresContainer.resetSchema();
        harness = new PersistenceHarness();
        repository =
                new JdbcCharacterProgressRepository(
                        harness.pools.loginPool(),
                        harness.scheduler,
                        harness.flushCycle,
                        Clock.systemUTC());
        harness.flushCycle.register(AggregateType.CHARACTER_PROGRESS, repository);
        repository.setLiveSource(this::stateOf);
    }

    @AfterEach
    void tearDown() {
        if (harness != null) {
            harness.close();
        }
    }

    private Optional<ProgressState> stateOf(UUID characterId) {
        ProgressState loaded = live.get(characterId);
        return loaded != null ? Optional.of(loaded) : Optional.ofNullable(stash.remove(characterId));
    }

    /** What the module's attachment does on session close: stash, mark, release - in that order. */
    private void closeSession(UUID characterId) {
        ProgressState last = live.get(characterId);
        if (last != null) {
            stash.put(characterId, last);
        }
        repository.markDirty(characterId);
        live.remove(characterId);
    }

    @Test
    @DisplayName("the state a session ended with is written, even though the flush comes later")
    void endStateSurvivesTheRelease() throws Exception {
        UUID characterId = insertCharacter();
        live.put(characterId, new ProgressState(14, 275L));

        // Exactly the real order: the character is gone before the flush gets its turn.
        closeSession(characterId);
        harness.flushCycle.flushNow(FlushReason.SESSION_END).get();

        assertThat(levelOf(characterId)).as("without the stash this row would not exist").isEqualTo(14);
        assertThat(xpOf(characterId)).isEqualTo(275L);
    }

    @Test
    @DisplayName("leaving writes without waiting for the autosave interval")
    void writesWithoutWaitingForTheInterval() throws Exception {
        UUID characterId = insertCharacter();
        live.put(characterId, new ProgressState(3, 40L));

        // The interval is 45s and no interval cycle runs in the harness; if the session-end trigger
        // did not write, nothing would.
        closeSession(characterId);
        harness.flushCycle.flushNow(FlushReason.SESSION_END).get();

        assertThat(harness.buffer.pending()).isZero();
        assertThat(levelOf(characterId)).isEqualTo(3);
    }

    @Test
    @DisplayName("a second flush does not write again - the stash is for exactly one final write")
    void stashIsConsumed() throws Exception {
        UUID characterId = insertCharacter();
        live.put(characterId, new ProgressState(5, 10L));
        closeSession(characterId);
        harness.flushCycle.flushNow(FlushReason.SESSION_END).get();
        long revisionAfterFirst = revisionOf(characterId);

        repository.markDirty(characterId);
        harness.flushCycle.flushNow(FlushReason.SESSION_END).get();

        // Keeping the entry would leak one per player who ever connected, and the row must not be
        // rewritten from a value nobody owns any more.
        assertThat(revisionOf(characterId)).isEqualTo(revisionAfterFirst);
    }

    @Test
    @DisplayName("a flush while the character is still online writes the live value")
    void liveValueWinsWhileOnline() throws Exception {
        UUID characterId = insertCharacter();
        live.put(characterId, new ProgressState(7, 60L));
        repository.markDirty(characterId);

        harness.flushCycle.flushNow(FlushReason.INTERVAL).get();

        assertThat(levelOf(characterId)).isEqualTo(7);

        // And a later gain is written on the next flush, from the rules rather than a stale copy.
        live.put(characterId, new ProgressState(8, 5L));
        repository.markDirty(characterId);
        harness.flushCycle.flushNow(FlushReason.INTERVAL).get();

        assertThat(levelOf(characterId)).isEqualTo(8);
        assertThat(xpOf(characterId)).isEqualTo(5L);
    }

    @Test
    @DisplayName("a character that was never loaded produces no row and no failure")
    void unknownCharacterIsSkipped() throws Exception {
        UUID characterId = insertCharacter();

        repository.markDirty(characterId);
        harness.flushCycle.flushNow(FlushReason.SESSION_END).get();

        // Nothing to salvage and nothing to retry - the mark is consumed rather than left to jam the
        // buffer forever.
        assertThat(rowCount(characterId)).isZero();
        assertThat(harness.buffer.pending()).isZero();
    }

    // --- helpers ---------------------------------------------------------

    private static UUID insertCharacter() throws Exception {
        UUID playerId = UUID.randomUUID();
        UUID characterId = UUID.randomUUID();
        try (Connection connection = PostgresContainer.openConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO rpg.player_state (player_id) VALUES ('" + playerId + "')");
            statement.execute(
                    "INSERT INTO rpg.character (character_id, player_id, character_class)"
                            + " VALUES ('"
                            + characterId
                            + "', '"
                            + playerId
                            + "', 'ROGUE')");
        }
        return characterId;
    }

    private static int levelOf(UUID characterId) throws Exception {
        return (int) scalar(characterId, "level");
    }

    private static long xpOf(UUID characterId) throws Exception {
        return scalar(characterId, "xp_in_level");
    }

    private static long revisionOf(UUID characterId) throws Exception {
        return scalar(characterId, "revision");
    }

    private static long scalar(UUID characterId, String column) throws Exception {
        try (Connection connection = PostgresContainer.openConnection();
                Statement statement = connection.createStatement();
                ResultSet rows =
                        statement.executeQuery(
                                "SELECT "
                                        + column
                                        + " FROM rpg.character_progress WHERE character_id = '"
                                        + characterId
                                        + "'")) {
            assertThat(rows.next()).as("a row for " + characterId + " must exist").isTrue();
            return rows.getLong(1);
        }
    }

    private static int rowCount(UUID characterId) throws Exception {
        try (Connection connection = PostgresContainer.openConnection();
                Statement statement = connection.createStatement();
                ResultSet rows =
                        statement.executeQuery(
                                "SELECT count(*) FROM rpg.character_progress"
                                        + " WHERE character_id = '"
                                        + characterId
                                        + "'")) {
            rows.next();
            return rows.getInt(1);
        }
    }
}
