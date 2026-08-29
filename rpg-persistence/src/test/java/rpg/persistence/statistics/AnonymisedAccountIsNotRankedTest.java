package rpg.persistence.statistics;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.persistence.FlushReason;
import rpg.core.persistence.PlayerState;
import rpg.core.statistics.Aggregation;
import rpg.persistence.support.PersistenceHarness;
import rpg.persistence.support.PostgresContainer;

/**
 * FR-039, SC-007 — <b>die Zahlen bleiben gezählt, das Konto erscheint nirgends.</b>
 *
 * <p>B02s Anonymisierung hängt die Statistikzeilen auf eine Ersatzkennung um und löscht die
 * {@code player_state}-Zeile. Beides ist gewollt: die Zahlen sollen erhalten bleiben — sie sind
 * Teil der Serverhistorie und nach der Umhängung an keine Person mehr gebunden —, aber sie dürfen
 * niemanden mehr benennen.
 *
 * <p><b>Der Filter sitzt beim Füllen, nicht beim Anzeigen.</b> Genauer: schon im Verbund der
 * Sichten mit {@code player_state}. Eine Ansicht, die filtern müsste, ist eine Ansicht, die es
 * vergessen kann — und bei drei Ansichten (Fenster, Hologramm, Command) wäre es eine Frage der
 * Zeit, welche es zuerst vergisst.
 */
class AnonymisedAccountIsNotRankedTest {

    private PersistenceHarness harness;
    private UUID staying;
    private UUID leaving;

    @BeforeEach
    void setUp() throws Exception {
        PostgresContainer.resetSchema();
        harness = new PersistenceHarness();
        staying = UUID.randomUUID();
        leaving = UUID.randomUUID();
        harness.playerStates.put(PlayerState.initial(staying, Instant.now()));
        harness.playerStates.put(PlayerState.initial(leaving, Instant.now()));
        harness.flushCycle.flushNow(FlushReason.INTERVAL).get();
    }

    @AfterEach
    void tearDown() {
        if (harness != null) {
            harness.close();
        }
    }

    @Test
    @DisplayName("SC-007 - nach der Anonymisierung steht das Konto auf keiner Rangliste")
    void afterAnonymisationTheAccountIsOnNoBoard() throws Exception {
        harness.statistics.increment(staying, "mob_kills.rotling", 5);
        harness.statistics.increment(leaving, "mob_kills.rotling", 99);
        harness.flushCycle.flushNow(FlushReason.INTERVAL).get();
        refresh();

        assertThat(source().allTime().get(Aggregation.MOB_KILLS))
                .as("vorher steht der Spitzenreiter noch da")
                .containsEntry(leaving, 99L);

        harness.playerStates.anonymize(leaving).get();
        refresh();

        Map<UUID, Long> board = source().allTime().get(Aggregation.MOB_KILLS);

        assertThat(board).doesNotContainKey(leaving).containsEntry(staying, 5L);
    }

    @Test
    @DisplayName("SC-007 - die Zahlen selbst bleiben in der Tabelle stehen")
    void thenumbersThemselvesRemainInTheTable() throws Exception {
        harness.statistics.increment(leaving, "mob_kills.rotling", 99);
        harness.flushCycle.flushNow(FlushReason.INTERVAL).get();

        harness.playerStates.anonymize(leaving).get();

        // Umgehaengt, nicht geloescht: ADR-044 sagt unbegrenzte Aufbewahrung zu, und
        // NoDirectDatabaseAccessTest verbietet jedes DELETE gegen diese Tabelle. Die 99 stehen
        // weiterhin da - nur unter einer Kennung, die zu niemandem mehr gehoert.
        assertThat(rowsWithValue(99L)).isEqualTo(1);
        assertThat(rowsFor(leaving)).isZero();
    }

    private JdbcLeaderboardSource source() {
        return new JdbcLeaderboardSource(harness.pools.loginPool(), kind -> false);
    }

    private void refresh() {
        assertThat(new LeaderboardRefresh(harness.pools.loginPool(), Logger.getLogger("test"))
                        .refreshAll())
                .isEqualTo(3);
    }

    private int rowsFor(UUID playerId) throws Exception {
        try (Connection connection = harness.pools.loginPool().getConnection();
                PreparedStatement statement =
                        connection.prepareStatement(
                                "SELECT COUNT(*) FROM rpg.player_statistic_daily WHERE player_id = ?")) {
            statement.setObject(1, playerId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getInt(1) : -1;
            }
        }
    }

    private int rowsWithValue(long value) throws Exception {
        try (Connection connection = harness.pools.loginPool().getConnection();
                PreparedStatement statement =
                        connection.prepareStatement(
                                "SELECT COUNT(*) FROM rpg.player_statistic_daily WHERE value = ?")) {
            statement.setLong(1, value);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getInt(1) : -1;
            }
        }
    }
}
