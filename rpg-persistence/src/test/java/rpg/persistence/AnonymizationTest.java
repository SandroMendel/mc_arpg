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

        // Ein Charakter haengt zwischen Konto und allem, was ihm gehoert (ADR-011) - eine Ebene,
        // durch die die Anonymisierung hindurchreichen muss. Genau deshalb wird er hier angelegt
        // und nicht vermieden.
        //
        // Hier stand zusaetzlich ein item_instance-Eintrag als Beispiel fuer eine Kindzeile. Die
        // Tabelle ist mit ADR-039 zurueckgebaut (V11_1); die Ebene, um die es geht, ist der
        // Charakter selbst, und der steht unveraendert. Der erste Test unten sieht ohnehin JEDE
        // Tabelle durch - er passt sich also von allein an, was es gerade gibt.
        UUID characterId = insertCharacter(playerId);
        harness.statistics.increment(playerId, METRIC, 25);
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
        // Was ein Charakter besitzt, erreicht das Konto nur ueber ihn (ADR-011) - die Ebene, an der
        // eine vor B03 geschriebene Anonymisierung vorbeigelaufen waere. Die Abfrage ging bis
        // ADR-039 ueber rpg.item_instance; seit V11_1 gibt es die Tabelle nicht mehr, und ein
        // Gegenstand liegt im Inventar-Blob. Die geprueffte Ebene ist dieselbe geblieben.
        total +=
                count(
                        "SELECT count(*) FROM rpg.character_inventory ci"
                                + " JOIN rpg.character c ON c.character_id = ci.character_id"
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
