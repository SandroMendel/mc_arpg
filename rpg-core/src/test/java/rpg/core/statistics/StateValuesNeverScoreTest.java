package rpg.core.statistics;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * FR-050c, ADR-046 — <b>Zustandswerte und private Werte tragen keine Gewichtung.</b>
 *
 * <p>Zwei Gründe, und sie sind verschieden:
 *
 * <ul>
 *   <li><b>Level und Coins</b> trügen den Fortschritt vergangener Saisons in die laufende. Wer
 *       seit einem Jahr spielt, hätte schon am ersten Tag der neuen Saison einen Vorsprung, den
 *       niemand mehr einholt — und eine Saison hörte auf, ein Neuanfang zu sein.
 *   <li><b>Die Onlinezeit</b> ist privat. Eine öffentliche Platzierung, die sich aus einer Zahl
 *       begründet, die niemand nachsehen kann, ist von Willkür nicht zu unterscheiden.
 * </ul>
 *
 * <p><b>Geprüft wird an zwei Stellen, und das ist Absicht.</b> Das Konfigurationsschema weist eine
 * solche Gewichtung beim Start ab; {@link ScoreWeights} weist sie noch einmal ab. Die zweite
 * Prüfung ist keine Dopplung, sondern der Fall, für den es kein Schema gibt: die Gewichtung eines
 * eingefrorenen Endstands kommt aus der <em>Datenbank</em>, und dort steht niemand mehr dazwischen.
 */
class StateValuesNeverScoreTest {

    @Test
    @DisplayName("ADR-046 - eine Gewichtung auf Level wird abgewiesen")
    void aweightOnLevelIsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ScoreWeights(Map.of(Aggregation.LEVEL, 1.0)))
                .withMessageContaining("level")
                .withMessageContaining("ADR-046");
    }

    @Test
    @DisplayName("ADR-046 - eine Gewichtung auf Coins wird abgewiesen")
    void aweightOnCoinsIsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ScoreWeights(Map.of(Aggregation.COINS, 1.0)))
                .withMessageContaining("coins");
    }

    @Test
    @DisplayName("FR-050c - eine Gewichtung auf einem privaten Wert wird abgewiesen")
    void aweightOnAPrivateValueIsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ScoreWeights(Map.of(Aggregation.PLAYTIME_ONLINE, 1.0)))
                .withMessageContaining("playtime_online");
    }

    @Test
    @DisplayName("ein negatives Gewicht wird abgewiesen")
    void anegativeWeightIsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ScoreWeights(Map.of(Aggregation.MOB_KILLS, -1.0)))
                .withMessageContaining("negativ");
    }

    @Test
    @DisplayName("die zweite Pruefung greift auch dort, wo kein Schema mehr dazwischensteht")
    void thesecondCheckAlsoActsWhereNoSchemaStandsBetween() {
        // Der eingefrorene Endstand traegt seine Gewichtung als JSONB. Beim Lesen entsteht daraus
        // wieder ein ScoreWeights - ohne Konfigurationsschema, ohne Startpruefung. Waere die Regel
        // nur im Schema, koennte eine von Hand veraenderte Zeile sie umgehen.
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                new ScoreWeights(
                                        Map.of(Aggregation.LEVEL, 5.0, Aggregation.MOB_KILLS, 1.0)));
    }
}
