package rpg.persistence.statistics;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.persistence.FlushReason;
import rpg.core.persistence.FlushResult;
import rpg.core.persistence.PlayerState;
import rpg.persistence.support.PersistenceHarness;
import rpg.persistence.support.PostgresContainer;

/**
 * SC-002 — tausend Ereignisse, <b>ein</b> Schreibvorgang und <b>eine</b> Zeile.
 *
 * <p>Das ist die Zusage, an der die ganze Bauart hängt: die Erfassung im Spielpfad ist ein
 * Zählerinkrement im Speicher, und erst der Fluss macht daraus Datenbankarbeit. Ohne sie wäre
 * jeder Kill eine Schreibanweisung, und ein Kampf mit dreißig Kreaturen dreißig davon — im Tick.
 *
 * <p><b>Gemessen wird beides</b>, und zwar getrennt: dass die tausend Ereignisse selbst nichts
 * einplanen (am Scheduler abgelesen), und dass am Ende genau eine Zeile in der Tabelle steht.
 * Die zweite Hälfte ist die interessantere — ein Batch aus tausend Anweisungen wäre auch „ein
 * Fluss", aber eben nicht ein Schreibvorgang.
 */
class ThousandIncrementsOneWriteTest {

    private static final String METRIC = "mob_kills";

    private PersistenceHarness harness;
    private UUID playerId;

    @BeforeEach
    void setUp() throws Exception {
        PostgresContainer.resetSchema();
        harness = new PersistenceHarness();
        playerId = UUID.randomUUID();
        harness.playerStates.put(PlayerState.initial(playerId, Instant.now()));

        // Den Spielerdatensatz aus dem Aufbau JETZT wegschreiben. Sonst laege er im selben Fluss
        // wie die Statistik, und written() zaehlte zwei Bestaende - was diesen Test rot machte,
        // ohne dass an der Statistik etwas falsch waere.
        harness.flushCycle.flushNow(FlushReason.INTERVAL).get();
    }

    @AfterEach
    void tearDown() {
        if (harness != null) {
            harness.close();
        }
    }

    @Test
    @DisplayName("SC-002 - tausend Inkremente erzeugen einen Schreibvorgang und eine Zeile")
    void athousandIncrementsProduceOneWriteAndOneRow() throws Exception {
        int asyncBefore = harness.scheduler.asyncRuns();

        for (int i = 0; i < 1_000; i++) {
            harness.statistics.increment(playerId, METRIC, 1);
        }

        assertThat(harness.scheduler.asyncRuns())
                .as("die Ereignisse selbst planen nichts ein")
                .isEqualTo(asyncBefore);

        FlushResult result = harness.flushCycle.flushNow(FlushReason.INTERVAL).get();

        assertThat(result.written()).as("ein geschriebener Bestand").isEqualTo(1);
        assertThat(rowsFor(playerId, METRIC)).as("eine Zeile").isEqualTo(1);
        assertThat(harness.statistics.total(playerId, METRIC).get())
                .as("und keiner der tausend ist verloren")
                .isEqualTo(1_000L);
    }

    @Test
    @DisplayName("SC-002 - dasselbe gilt fuer den Maximum-Weg")
    void thesameHoldsForTheMaximumPath() throws Exception {
        int asyncBefore = harness.scheduler.asyncRuns();

        for (int i = 1; i <= 1_000; i++) {
            harness.statistics.reportMax(playerId, "damage_max", i);
        }

        assertThat(harness.scheduler.asyncRuns()).isEqualTo(asyncBefore);

        harness.flushCycle.flushNow(FlushReason.INTERVAL).get();

        assertThat(rowsFor(playerId, "damage_max")).isEqualTo(1);
        assertThat(harness.statistics.total(playerId, "damage_max").get()).isEqualTo(1_000L);
    }

    private int rowsFor(UUID player, String metric) throws Exception {
        try (Connection connection = harness.pools.loginPool().getConnection();
                PreparedStatement statement =
                        connection.prepareStatement(
                                "SELECT COUNT(*) FROM rpg.player_statistic_daily"
                                        + " WHERE player_id = ? AND metric = ?")) {
            statement.setObject(1, player);
            statement.setString(2, metric);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getInt(1) : 0;
            }
        }
    }
}
