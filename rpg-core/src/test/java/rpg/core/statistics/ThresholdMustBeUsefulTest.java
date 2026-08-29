package rpg.core.statistics;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import rpg.core.config.ConfigSchema;
import rpg.core.config.ConfigView;

/**
 * FR-007d — <b>die Schwelle ist eine Regel, keine Zahlenwahl.</b>
 *
 * <p>{@code kill-credit-share} entscheidet, ab welchem Schadensanteil ein Kill einem Spieler
 * gutgeschrieben wird. Zwei Werte machen die Zusage aus FR-007 wirkungslos, und beide sehen im
 * YAML völlig unauffällig aus:
 *
 * <ul>
 *   <li><b>Null</b> — jeder Streiftreffer zählt. Wer einem vorbeilaufenden Boss einmal ins Bein
 *       schlägt, hat einen Bosskill in seiner Statistik, und die Rangliste misst Anwesenheit statt
 *       Beteiligung.
 *   <li><b>Eins oder mehr</b> — die Schwelle greift nie. Nur wer den <em>gesamten</em> Schaden
 *       allein austeilt, bekommt etwas gutgeschrieben; jede Party hörte auf zu zählen, und niemand
 *       fände heraus, warum.
 * </ul>
 *
 * <p>Deshalb bricht der Start ab, statt zu warnen. Es ist dieselbe Bauart, mit der B11 die Ordnung
 * „ein Tod wiegt schwerer als viele Treffer" zur Startprüfung gemacht hat (FR-044 dort): eine
 * Zusage, die eine spätere Balancing-Änderung sonst still aushebeln könnte, wird zur Bedingung
 * dafür, dass der Server überhaupt hochkommt.
 */
class ThresholdMustBeUsefulTest {

    @ParameterizedTest(name = "kill-credit-share = {0} wird abgewiesen")
    @ValueSource(doubles = {0.0, -0.1, 1.0, 1.5, 2.0})
    void anUnusableThresholdIsRejected(double share) {
        Map<String, Object> doc = document();
        capture(doc).put("kill-credit-share", share);

        assertThatThrownBy(() -> bind(doc))
                .hasMessageContaining("statistics.yml")
                .hasMessageContaining("capture.kill-credit-share")
                .hasMessageContaining("echt zwischen 0 und 1");
    }

    @Test
    @DisplayName("die Meldung erklaert beide Fehlerrichtungen, nicht nur die Grenze")
    void theMessageExplainsBothDirections() {
        Map<String, Object> doc = document();
        capture(doc).put("kill-credit-share", 0.0);

        assertThatThrownBy(() -> bind(doc))
                .hasMessageContaining("Streiftreffer")
                .hasMessageContaining("griffe nie");
    }

    @ParameterizedTest(name = "kill-credit-share = {0} geht durch")
    @ValueSource(doubles = {0.001, 0.05, 0.5, 0.999})
    void ausableThresholdPasses(double share) {
        Map<String, Object> doc = document();
        capture(doc).put("kill-credit-share", share);

        assertThatCode(() -> bind(doc)).doesNotThrowAnyException();
    }

    // ------------------------------------------------------------------ Gerüst

    private static StatisticsConfig bind(Map<String, Object> document) {
        ConfigSchema<StatisticsConfig> schema = StatisticsConfigSchema.schema();
        return schema.bind(new MapView(document, StatisticsConfigSchema.SCHEMA_VERSION));
    }

    private static Map<String, Object> document() {
        Map<String, Object> doc = new LinkedHashMap<>();

        Map<String, Object> capture = new LinkedHashMap<>();
        capture.put("kill-credit-share", 0.05);
        capture.put("idle-after-seconds", 300);
        doc.put("capture", capture);

        Map<String, Object> leaderboards = new LinkedHashMap<>();
        leaderboards.put("refresh-interval-seconds", 300);
        leaderboards.put("places", 10);
        doc.put("leaderboards", leaderboards);

        List<Object> seasons = new ArrayList<>();
        Map<String, Object> season = new LinkedHashMap<>();
        season.put("key", "2026-q3");
        season.put("from", "2026-07-01");
        season.put("to", "2026-09-30");
        seasons.add(season);
        doc.put("seasons", seasons);

        Map<String, Object> weights = new LinkedHashMap<>();
        weights.put(Aggregation.MOB_KILLS.key(), 1);
        Map<String, Object> score = new LinkedHashMap<>();
        score.put("weights", weights);
        doc.put("score", score);

        return doc;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> capture(Map<String, Object> doc) {
        return (Map<String, Object>) doc.get("capture");
    }

    private record MapView(Map<String, Object> body, int schemaVersion) implements ConfigView {

        @Override
        public String getString(String path) {
            return String.valueOf(body.get(path));
        }

        @Override
        public boolean getBoolean(String path) {
            return Boolean.TRUE.equals(body.get(path));
        }

        @Override
        public int getInt(String path) {
            return ((Number) body.get(path)).intValue();
        }

        @Override
        public long getLong(String path) {
            return ((Number) body.get(path)).longValue();
        }

        @Override
        public double getDouble(String path) {
            return ((Number) body.get(path)).doubleValue();
        }

        @Override
        public List<?> getList(String path) {
            return (List<?>) body.get(path);
        }

        @Override
        public Map<?, ?> getMap(String path) {
            Object value = body.get(path);
            return value == null ? Map.of() : (Map<?, ?>) value;
        }
    }
}
