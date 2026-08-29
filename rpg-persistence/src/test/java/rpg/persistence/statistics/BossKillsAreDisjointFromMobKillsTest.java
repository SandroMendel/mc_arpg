package rpg.persistence.statistics;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
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
 * FR-009a — <b>Bosskills und Mob-Kills sind disjunkt.</b>
 *
 * <p>Beide entstehen aus derselben Familie {@code mob_kills.*}; getrennt wird nach dem
 * Boss-Kennzeichen aus {@code mobs.yml}. Die Trennung muss <em>disjunkt</em> sein, und zwar aus
 * einem Grund, der erst in Phase 7 sichtbar wird: die Saison-Gesamtwertung gewichtet beide
 * Ranglisten. Zählte ein Bosskill in beiden, ginge er mit Gewicht 1 <em>und</em> mit Gewicht 50 in
 * die Punktzahl — jeder Bossgegner wäre 51 Punkte wert statt 50, und niemand käme darauf, warum
 * die Summe nicht aufgeht.
 *
 * <p><b>Und die Trennung sitzt nicht in der Datenbank.</b> Welche Art ein Boss ist, steht in
 * {@code mobs.yml}; die gespeicherte Zeile lautet für beide {@code mob_kills.<art>}. Verliert eine
 * Art ihr Kennzeichen, wandern ihre Kills bei der nächsten Auffrischung von selbst in die andere
 * Rangliste — dieselbe Zusage, die B11 fürs Balancing gibt, und derselbe Preis.
 */
class BossKillsAreDisjointFromMobKillsTest {

    private static final String BOSS_KIND = "dune-warlord";
    private static final String ORDINARY_KIND = "rotling";

    private PersistenceHarness harness;
    private UUID player;

    @BeforeEach
    void setUp() throws Exception {
        PostgresContainer.resetSchema();
        harness = new PersistenceHarness();
        player = UUID.randomUUID();
        harness.playerStates.put(PlayerState.initial(player, Instant.now()));
        harness.flushCycle.flushNow(FlushReason.INTERVAL).get();
    }

    @AfterEach
    void tearDown() {
        if (harness != null) {
            harness.close();
        }
    }

    @Test
    @DisplayName("FR-009a - eine Boss-Art steht in der Bosskill-Rangliste und NICHT in der anderen")
    void abossKindAppearsInOneBoardOnly() throws Exception {
        insert(ORDINARY_KIND, 10);
        insert(BOSS_KIND, 3);
        refresh();

        Map<Aggregation, Map<UUID, Long>> boards = source().allTime();

        assertThat(boards.get(Aggregation.MOB_KILLS)).containsEntry(player, 10L);
        assertThat(boards.get(Aggregation.BOSS_KILLS)).containsEntry(player, 3L);
    }

    @Test
    @DisplayName("FR-009a - beide zusammen ergeben alle Kills, und keiner zaehlt doppelt")
    void bothTogetherMakeAllKillsAndNoneCountsTwice() throws Exception {
        insert(ORDINARY_KIND, 10);
        insert("gloom-sovereign", 2);
        insert(BOSS_KIND, 3);
        refresh();

        Map<Aggregation, Map<UUID, Long>> boards = source().allTime();
        long mobs = boards.get(Aggregation.MOB_KILLS).get(player);
        long bosses = boards.get(Aggregation.BOSS_KILLS).get(player);

        assertThat(mobs + bosses).as("alle Kills, jeder genau einmal").isEqualTo(15L);
        assertThat(mobs).isEqualTo(10L);
        assertThat(bosses).isEqualTo(5L);
    }

    @Test
    @DisplayName("verliert eine Art ihr Kennzeichen, wandern ihre Kills rueckwirkend hinueber")
    void whenAKindLosesItsFlagItsKillsMoveOver() throws Exception {
        insert(BOSS_KIND, 3);
        refresh();

        // Erst als Boss gefuehrt...
        assertThat(source().allTime().get(Aggregation.BOSS_KILLS)).containsEntry(player, 3L);

        // ...dann nicht mehr. Dieselben Zeilen in der Datenbank, andere Antwort - weil die
        // Zuordnung aus mobs.yml kommt und nicht aus der gespeicherten Zeile.
        JdbcLeaderboardSource afterBalancing =
                new JdbcLeaderboardSource(harness.pools.loginPool(), kind -> false);

        assertThat(afterBalancing.allTime().get(Aggregation.MOB_KILLS)).containsEntry(player, 3L);
        assertThat(afterBalancing.allTime().get(Aggregation.BOSS_KILLS)).isNull();
    }

    private JdbcLeaderboardSource source() {
        return new JdbcLeaderboardSource(
                harness.pools.loginPool(),
                kind -> Set.of(BOSS_KIND, "gloom-sovereign").contains(kind));
    }

    private void refresh() {
        assertThat(new LeaderboardRefresh(harness.pools.loginPool(), Logger.getLogger("test"))
                        .refreshAll())
                .isEqualTo(3);
    }

    private void insert(String kindKey, long kills) throws Exception {
        try (Connection connection = harness.pools.loginPool().getConnection();
                PreparedStatement statement =
                        connection.prepareStatement(
                                "INSERT INTO rpg.player_statistic_daily"
                                        + " (player_id, metric, day, value) VALUES (?, ?, ?, ?)")) {
            statement.setObject(1, player);
            statement.setString(2, "mob_kills." + kindKey);
            statement.setDate(3, java.sql.Date.valueOf(LocalDate.now(ZoneOffset.UTC)));
            statement.setLong(4, kills);
            statement.executeUpdate();
            if (!connection.getAutoCommit()) {
                connection.commit();
            }
        }
    }
}
