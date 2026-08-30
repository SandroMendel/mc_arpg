package rpg.persistence.statistics;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.persistence.FlushReason;
import rpg.core.persistence.PlayerState;
import rpg.core.statistics.RewardClaim;
import rpg.core.statistics.StatisticsConfig;
import rpg.persistence.support.PersistenceHarness;
import rpg.persistence.support.PostgresContainer;

/**
 * FR-053a, SC-018 — <b>ein Anspruch verfällt nicht. Nie.</b>
 *
 * <p>Wer während des Saisonendes nicht online war, hat nichts falsch gemacht. Eine Frist bestrafte
 * Urlaub, Krankheit und Zeitverschiebung — und zwar ausgerechnet denjenigen, der drei Monate lang
 * gespielt hat, um den Platz zu bekommen.
 *
 * <p><b>Dieser Test bewacht zwei Dinge, und das zweite ist das wichtigere:</b>
 *
 * <ol>
 *   <li>ein ein Jahr alter Anspruch ist unverändert einlösbar,
 *   <li><b>es gibt keine Ablaufspalte</b> — und es kommt auch keine dazu. Eine Frist einzuführen
 *       wäre eine kleine Migration und eine große Änderung an einer Zusage; sie würde als
 *       Aufräumarbeit durchgehen, wenn niemand hinsieht.
 * </ol>
 */
class ClaimNeverExpiresTest {

    private static final String OLD_SEASON = "2025-q3";

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
    @DisplayName("SC-018 - ein Anspruch aus einer Saison vor einem Jahr ist unveraendert einloesbar")
    void aclaimFromASeasonAYearAgoIsStillClaimable() throws Exception {
        JdbcRewardClaimRepository claims = claims();
        claims.create(
                List.of(
                        RewardClaim.open(
                                OLD_SEASON,
                                account,
                                1,
                                new StatisticsConfig.Reward(5_000L, List.of()))));

        // Den Anspruch ein Jahr alt machen - so, wie er nach einem Jahr Serverlaufzeit daliegen
        // wuerde.
        ageBy(365);

        assertThat(claims.openFor(account)).as("nach einem Jahr immer noch offen").hasSize(1);
        assertThat(claims.markClaimed(OLD_SEASON, account, UUID.randomUUID(), Instant.now()))
                .as("und einloesbar")
                .isTrue();
    }

    @Test
    @DisplayName("FR-053a - die Tabelle traegt keine Ablaufspalte")
    void thetableCarriesNoExpiryColumn() throws Exception {
        List<String> columns = columnsOf("season_reward_claim");

        assertThat(columns).isNotEmpty();
        assertThat(columns)
                .as(
                        "eine Frist einzufuehren waere eine kleine Migration und eine grosse"
                                + " Aenderung an einer Zusage (FR-053a, ADR-045)")
                .noneMatch(
                        column ->
                                column.contains("expire")
                                        || column.contains("valid_until")
                                        || column.contains("deadline")
                                        || column.contains("lapse"));
    }

    @Test
    @DisplayName("FR-053a - und die Migration selbst nennt keinen Ablauf")
    void andThemigrationItselfMentionsNoExpiry() throws Exception {
        // Die Spaltenpruefung oben findet eine Spalte. Diese hier findet den Versuch, den Ablauf
        // anders zu bauen - ueber einen DELETE-Job oder einen Trigger.
        String migration =
                java.nio.file.Files.readString(
                                repositoryRoot()
                                        .resolve(
                                                "rpg-persistence/src/main/resources/db/migration/"
                                                        + "V12_3__season_reward_claim.sql"))
                        .toLowerCase(Locale.ROOT);

        assertThat(migration).doesNotContain("create trigger");
        assertThat(migration).doesNotContain("delete from");
    }

    private void ageBy(int days) throws Exception {
        try (Connection connection = harness.pools.loginPool().getConnection();
                PreparedStatement statement =
                        connection.prepareStatement(
                                "UPDATE rpg.season_reward_claim SET created_at = ?"
                                        + " WHERE player_id = ?")) {
            statement.setTimestamp(
                    1, java.sql.Timestamp.from(Instant.now().minus(days, ChronoUnit.DAYS)));
            statement.setObject(2, account);
            statement.executeUpdate();
            if (!connection.getAutoCommit()) {
                connection.commit();
            }
        }
    }

    private List<String> columnsOf(String table) throws Exception {
        List<String> columns = new ArrayList<>();
        try (Connection connection = harness.pools.loginPool().getConnection();
                PreparedStatement statement =
                        connection.prepareStatement(
                                "SELECT column_name FROM information_schema.columns"
                                        + " WHERE table_schema = 'rpg' AND table_name = ?")) {
            statement.setString(1, table);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    columns.add(rows.getString(1).toLowerCase(Locale.ROOT));
                }
            }
        }
        return columns;
    }

    private JdbcRewardClaimRepository claims() {
        return new JdbcRewardClaimRepository(harness.pools.loginPool());
    }

    private static java.nio.file.Path repositoryRoot() {
        java.nio.file.Path here = java.nio.file.Path.of("").toAbsolutePath();
        while (here != null && !java.nio.file.Files.isDirectory(here.resolve("rpg-core"))) {
            here = here.getParent();
        }
        if (here == null) {
            throw new IllegalStateException("Wurzelverzeichnis nicht gefunden");
        }
        return here;
    }
}
