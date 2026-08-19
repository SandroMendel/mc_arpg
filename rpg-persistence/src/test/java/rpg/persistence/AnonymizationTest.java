package rpg.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import rpg.core.persistence.AuditEntry;
import rpg.core.persistence.FlushReason;
import rpg.core.persistence.ItemInstance;
import rpg.core.persistence.PlayerState;
import rpg.persistence.support.PersistenceHarness;
import rpg.persistence.support.PostgresContainer;

/**
 * T077 / SC-010 / FR-017a to FR-017c: a player's personal reference disappears while the aggregates
 * survive.
 *
 * <p>The searches below go across <em>every</em> table rather than just {@code player_state}. A
 * deletion request that leaves the identifier in the statistics or the audit log has not been
 * fulfilled, and checking only the obvious table is how that gets missed.
 */
class AnonymizationTest {

    private static final String METRIC = "mob_kills";

    private PersistenceHarness harness;
    private UUID playerId;

    @BeforeEach
    void setUp() throws Exception {
        PostgresContainer.resetSchema();
        harness = new PersistenceHarness();
        playerId = UUID.randomUUID();

        harness.playerStates.put(PlayerState.initial(playerId, Instant.now()));
        harness.flushCycle.flushNow(FlushReason.INTERVAL).get();

        // The item hangs off a character since ADR-011, which adds a level anonymisation has to
        // reach through. That is exactly why it is set up here rather than avoided.
        UUID characterId = insertCharacter(playerId);
        harness.statistics.increment(playerId, METRIC, 25);
        harness.itemInstances.create(
                new ItemInstance(UUID.randomUUID(), characterId, "sword.iron", Map.of(), 0L));
        harness.auditLog.append(
                new AuditEntry(
                        Instant.now(),
                        playerId.toString(),
                        "item_granted",
                        java.util.Optional.of(playerId),
                        Map.of()));
        harness.flushCycle.flushNow(FlushReason.INTERVAL).get();
    }

    @AfterEach
    void tearDown() {
        if (harness != null) {
            harness.close();
        }
    }

    @Test
    void noTableStillContainsTheOriginalIdentifier() throws Exception {
        harness.playerStates.anonymize(playerId).get();

        assertThat(rowsMentioning(playerId))
                .as("FR-017b: the original identifier must be gone from every table")
                .isZero();
    }

    @Test
    void allTimeTotalsAreUnchanged() throws Exception {
        long before = totalKills();

        harness.playerStates.anonymize(playerId).get();

        // The point of anonymising rather than deleting: leaderboards keep their history.
        assertThat(totalKills()).isEqualTo(before).isEqualTo(25L);
    }

    @Test
    void theStatisticRowSurvivesUnderTheSubstitute() throws Exception {
        harness.playerStates.anonymize(playerId).get();

        try (Connection connection = PostgresContainer.openConnection();
                Statement statement = connection.createStatement();
                ResultSet rows =
                        statement.executeQuery(
                                "SELECT count(*) FROM rpg.player_statistic_daily WHERE metric = '"
                                        + METRIC
                                        + "'")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getLong(1)).isEqualTo(1L);
        }
    }

    @Test
    void theActIsRecordedButWithoutTheAnonymisedIdentifier() throws Exception {
        harness.playerStates.anonymize(playerId).get();

        try (Connection connection = PostgresContainer.openConnection();
                Statement statement = connection.createStatement();
                ResultSet rows =
                        statement.executeQuery(
                                "SELECT count(*) FROM rpg.audit_log"
                                        + " WHERE action = 'player_anonymized'"
                                        + " AND target_player_id IS NULL")) {
            assertThat(rows.next()).isTrue();
            // FR-017c: recorded, but the entry must not preserve the very reference that was
            // removed.
            assertThat(rows.getLong(1)).isEqualTo(1L);
        }
    }

    @Test
    void anonymisingAnUnknownPlayerIsHarmless() throws Exception {
        harness.playerStates.anonymize(UUID.randomUUID()).get();

        // The original player is untouched.
        assertThat(rowsMentioning(playerId)).isPositive();
    }

    @Test
    void thePlayerRecordItselfIsGone() throws Exception {
        harness.playerStates.anonymize(playerId).get();

        assertThat(harness.playerStates.load(playerId).get()).isEmpty();
        assertThat(harness.playerStates.cached(playerId)).isEmpty();
    }

    /** Inserts a character directly; this test is about anonymisation, not about the repository. */
    private static UUID insertCharacter(UUID playerId) throws Exception {
        UUID characterId = UUID.randomUUID();
        try (Connection connection = PostgresContainer.openConnection();
                PreparedStatement statement =
                        connection.prepareStatement(
                                "INSERT INTO rpg.character (character_id, player_id,"
                                        + " character_class) VALUES (?, ?, 'WARRIOR')")) {
            statement.setObject(1, characterId);
            statement.setObject(2, playerId);
            statement.executeUpdate();
        }
        return characterId;
    }

    /** Counts rows in any table that still mention the identifier. */
    private long rowsMentioning(UUID id) throws Exception {
        long total = 0;
        total += count("SELECT count(*) FROM rpg.player_state WHERE player_id = ?", id, true);
        total +=
                count(
                        "SELECT count(*) FROM rpg.player_statistic_daily WHERE player_id = ?",
                        id,
                        true);
        total += count("SELECT count(*) FROM rpg.character WHERE player_id = ?", id, true);
        // Items reach the account only through their character (ADR-011), which is the level an
        // anonymisation written before B03 would have walked straight past.
        total +=
                count(
                        "SELECT count(*) FROM rpg.item_instance i"
                                + " JOIN rpg.character c ON c.character_id = i.owner_character_id"
                                + " WHERE c.player_id = ?",
                        id,
                        true);
        total += count("SELECT count(*) FROM rpg.audit_log WHERE target_player_id = ?", id, true);
        total += count("SELECT count(*) FROM rpg.audit_log WHERE actor = ?", id, false);
        return total;
    }

    private long count(String sql, UUID id, boolean asUuid) throws Exception {
        try (Connection connection = PostgresContainer.openConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            if (asUuid) {
                statement.setObject(1, id);
            } else {
                statement.setString(1, id.toString());
            }
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getLong(1) : 0L;
            }
        }
    }

    private long totalKills() throws Exception {
        try (Connection connection = PostgresContainer.openConnection();
                Statement statement = connection.createStatement();
                ResultSet rows =
                        statement.executeQuery(
                                "SELECT COALESCE(SUM(value), 0) FROM rpg.player_statistic_daily"
                                        + " WHERE metric = '"
                                        + METRIC
                                        + "'")) {
            return rows.next() ? rows.getLong(1) : 0L;
        }
    }
}
