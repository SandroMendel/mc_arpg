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
import rpg.core.persistence.PlayerState;
import rpg.persistence.support.PersistenceHarness;
import rpg.persistence.support.PostgresContainer;

/**
 * ADR-040 — beide Schreibarten teilen sich <b>eine</b> Tabelle, und die vorhandene ändert sich
 * nicht.
 *
 * <p>Der zweite Schreibweg ist eine <em>additive</em> Änderung an B02: eine zweite Anweisung auf
 * derselben Zeile, kein zweiter Bestand und kein neuer {@code AggregateType} (R10). Das ist der
 * Teil, den ein Test festhalten muss — die Versuchung, für Maxima eine eigene Tabelle anzulegen,
 * ist beim nächsten Umbau groß und der Schaden still: zwei Bestände über dieselben Ereignisse
 * driften auseinander, sobald einer von beiden einmal nicht geschrieben wird.
 *
 * <p>Deshalb steht hier auch der Nachweis, dass die <b>Summenmetriken sich nicht anders verhalten
 * als vorher</b>. Ein {@code GREATEST}, das versehentlich für alle gälte, wäre an einer Kill-Zahl
 * nicht zu erkennen, solange jeder Kill einzeln geschrieben wird — erst bei zwei Kills in einem
 * Fluss stünde plötzlich 1 statt 2.
 */
class SumAndMaxShareOneTableTest {

    private static final String SUM_METRIC = "mob_kills";
    private static final String MAX_METRIC = "damage_max";

    private PersistenceHarness harness;
    private UUID playerId;

    @BeforeEach
    void setUp() {
        PostgresContainer.resetSchema();
        harness = new PersistenceHarness();
        playerId = UUID.randomUUID();
        harness.playerStates.put(PlayerState.initial(playerId, Instant.now()));
    }

    @AfterEach
    void tearDown() {
        if (harness != null) {
            harness.close();
        }
    }

    @Test
    @DisplayName("beide Arten landen in player_statistic_daily, je eine Zeile")
    void bothKindsLandInTheSameTable() throws Exception {
        harness.statistics.increment(playerId, SUM_METRIC, 3);
        harness.statistics.reportMax(playerId, MAX_METRIC, 1249);
        harness.flushCycle.flushNow(FlushReason.INTERVAL).get();

        assertThat(rowsFor(playerId)).isEqualTo(2);
        assertThat(harness.statistics.total(playerId, SUM_METRIC).get()).isEqualTo(3L);
        assertThat(harness.statistics.total(playerId, MAX_METRIC).get()).isEqualTo(1249L);
    }

    @Test
    @DisplayName("die vorhandene Summenmetrik verhaelt sich unveraendert additiv")
    void theExistingSumMetricStillAdds() throws Exception {
        // Der Fall, an dem ein versehentlich global gewordenes GREATEST auffliegt: zwei Kills in
        // EINEM Fluss. Einzeln geschrieben saehe die Zahl auch mit MAX richtig aus.
        harness.statistics.increment(playerId, SUM_METRIC, 1);
        harness.statistics.increment(playerId, SUM_METRIC, 1);
        harness.flushCycle.flushNow(FlushReason.INTERVAL).get();

        assertThat(harness.statistics.total(playerId, SUM_METRIC).get()).isEqualTo(2L);
    }

    @Test
    @DisplayName("ueber mehrere Fluesse hinweg addiert die Summe weiter")
    void theSumKeepsAddingAcrossFlushes() throws Exception {
        harness.statistics.increment(playerId, SUM_METRIC, 30);
        harness.flushCycle.flushNow(FlushReason.INTERVAL).get();
        harness.statistics.increment(playerId, SUM_METRIC, 12);
        harness.flushCycle.flushNow(FlushReason.INTERVAL).get();

        assertThat(harness.statistics.total(playerId, SUM_METRIC).get()).isEqualTo(42L);
    }

    @Test
    @DisplayName("die beiden Wege stoeren sich nicht, auch nicht im selben Fluss")
    void theTwoPathsDoNotInterfere() throws Exception {
        harness.statistics.increment(playerId, SUM_METRIC, 2);
        harness.statistics.reportMax(playerId, MAX_METRIC, 500);
        harness.statistics.increment(playerId, SUM_METRIC, 2);
        harness.statistics.reportMax(playerId, MAX_METRIC, 300);
        harness.flushCycle.flushNow(FlushReason.INTERVAL).get();

        assertThat(harness.statistics.total(playerId, SUM_METRIC).get()).isEqualTo(4L);
        assertThat(harness.statistics.total(playerId, MAX_METRIC).get()).isEqualTo(500L);
    }

    private int rowsFor(UUID player) throws Exception {
        try (Connection connection = harness.pools.loginPool().getConnection();
                PreparedStatement statement =
                        connection.prepareStatement(
                                "SELECT COUNT(*) FROM rpg.player_statistic_daily"
                                        + " WHERE player_id = ?")) {
            statement.setObject(1, player);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getInt(1) : 0;
            }
        }
    }
}
