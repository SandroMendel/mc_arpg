package rpg.persistence.statistics;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.persistence.FlushReason;
import rpg.core.persistence.PlayerState;
import rpg.core.statistics.MetricRegistry;
import rpg.persistence.support.PersistenceHarness;
import rpg.persistence.support.PostgresContainer;

/**
 * Die Leseseite gegen eine echte PostgreSQL — <b>vor allem das Muster der Aufschlüsselung.</b>
 *
 * <p>{@code metric LIKE 'mob_kills.%'} sieht harmlos aus und ist es nicht: der Unterstrich in
 * {@code mob_kills} ist in LIKE ein Platzhalter für <em>ein beliebiges Zeichen</em>. Ohne
 * {@code ESCAPE} träfe das Muster auch {@code mob-kills.x} oder {@code mobxkills.y} — und in einer
 * Aufschlüsselung stünden Zeilen, die zu einer anderen Familie gehören.
 *
 * <p>Gegen eine Attrappe wäre das nicht prüfbar: das Verhalten steckt in der Datenbank, nicht im
 * Java.
 */
class JdbcStatisticsViewTest {

    private static final LocalDate TODAY = LocalDate.now(ZoneOffset.UTC);

    private PersistenceHarness harness;
    private UUID account;

    @BeforeEach
    void setUp() throws Exception {
        PostgresContainer.resetSchema();
        harness = new PersistenceHarness();
        account = UUID.randomUUID();
        harness.playerStates.put(PlayerState.initial(account, Instant.now()));
        harness.flushCycle.flushNow(FlushReason.INTERVAL).get();
    }

    @AfterEach
    void tearDown() {
        if (harness != null) {
            harness.close();
        }
    }

    @Test
    @DisplayName("die Summe eines Schluessels ueber einen Datumsbereich")
    void thesumOfAKeyOverARange() throws Exception {
        insert("mob_kills.rotling", TODAY, 5);
        insert("mob_kills.rotling", TODAY.minusDays(1), 7);
        insert("mob_kills.rotling", TODAY.minusDays(30), 100);

        assertThat(view().sum(account, "mob_kills.rotling", TODAY.minusDays(6), TODAY).get())
                .isEqualTo(12L);
    }

    @Test
    @DisplayName("das Maximum wird maximiert, nicht summiert")
    void themaximumIsMaximised() throws Exception {
        insert("damage_max", TODAY, 700);
        insert("damage_max", TODAY.minusDays(1), 1200);

        assertThat(view().max(account, "damage_max", TODAY.minusDays(6), TODAY).get())
                .isEqualTo(1200L);
    }

    @Test
    @DisplayName("die Aufschluesselung liefert die DIMENSIONEN, nicht die vollen Schluessel")
    void thebreakdownYieldsDimensions() throws Exception {
        insert("mob_kills.rotling", TODAY, 5);
        insert("mob_kills.dune-warlord", TODAY, 1);

        assertThat(view().breakdown(account, MetricRegistry.MOB_KILLS, TODAY, TODAY).get())
                .containsOnlyKeys("rotling", "dune-warlord")
                .containsEntry("rotling", 5L);
    }

    @Test
    @DisplayName("der Unterstrich im Familiennamen ist KEIN Platzhalter")
    void theunderscoreInTheFamilyNameIsNoWildcard() throws Exception {
        insert("mob_kills.rotling", TODAY, 5);
        // Ein Schluessel, den das LIKE-Muster ohne ESCAPE mitnehmen wuerde: der Unterstrich
        // stuende dann fuer das Minus.
        insert("mob-kills.fremd", TODAY, 999);

        assertThat(view().breakdown(account, MetricRegistry.MOB_KILLS, TODAY, TODAY).get())
                .as("nur die eigene Familie")
                .containsOnlyKeys("rotling");
    }

    @Test
    @DisplayName("eine Familie ohne Daten ergibt eine leere Aufschluesselung, keinen Fehler")
    void afamilyWithoutDataYieldsAnEmptyBreakdown() throws Exception {
        assertThat(view().breakdown(account, MetricRegistry.DEATHS, TODAY, TODAY).get()).isEmpty();
    }

    @Test
    @DisplayName("ein Bereich ohne Zeilen ergibt null, keinen Fehler")
    void arangeWithoutRowsYieldsZero() throws Exception {
        assertThat(view().sum(account, "mob_kills.rotling", TODAY, TODAY).get()).isZero();
    }

    private JdbcStatisticsView view() {
        return new JdbcStatisticsView(harness.pools.loginPool(), harness.scheduler);
    }

    private void insert(String metric, LocalDate day, long value) throws Exception {
        try (Connection connection = harness.pools.loginPool().getConnection();
                PreparedStatement statement =
                        connection.prepareStatement(
                                "INSERT INTO rpg.player_statistic_daily"
                                        + " (player_id, metric, day, value) VALUES (?, ?, ?, ?)")) {
            statement.setObject(1, account);
            statement.setString(2, metric);
            statement.setDate(3, java.sql.Date.valueOf(day));
            statement.setLong(4, value);
            statement.executeUpdate();
            if (!connection.getAutoCommit()) {
                connection.commit();
            }
        }
    }
}
