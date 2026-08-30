package rpg.core.statistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.config.ConfigSchema;
import rpg.core.config.ConfigView;

/**
 * FR-050d, SC-022 — <b>eine Gewichtung ohne bekannte Metrik bricht den Start ab.</b>
 *
 * <p>Ohne einen einzigen Eintrag hätte jedes Konto null Punkte. Die Saison endete dann nicht ohne
 * Sieger, sondern mit einem <em>willkürlichen</em> Sieger — der Gleichstandsentscheid ist die
 * Kontokennung, also gewönne, wessen UUID zufällig vorne steht. Das ist schlimmer als kein
 * Ergebnis: es sieht aus wie eines.
 *
 * <p><b>Und die Meldung nennt, was fehlt.</b> Ein Abbruch mit „ungültige Konfiguration" schickt den
 * Betreiber auf die Suche — an dem Tag, an dem sein Server nicht startet.
 */
class EmptyWeightsFailStartupTest {

    @Test
    @DisplayName("FR-050d - eine leere Gewichtung bricht den Start ab")
    void anemptyWeightingAbortsTheStart() {
        Map<String, Object> doc = document();
        score(doc).put("weights", new LinkedHashMap<String, Object>());

        assertThatThrownBy(() -> bind(doc))
                .hasMessageContaining("statistics.yml")
                .hasMessageContaining("score.weights");
    }

    @Test
    @DisplayName("SC-022 - und die Meldung erklaert, was ohne Gewichtung passieren wuerde")
    void andTheMessageExplainsWhatWouldHappen() {
        Map<String, Object> doc = document();
        score(doc).remove("weights");

        assertThatThrownBy(() -> bind(doc)).hasMessageContaining("Erstbesten");
    }

    @Test
    @DisplayName("SC-022 - eine unbekannte Metrik nennt sich selbst und die bekannten")
    void anunknownMetricNamesItselfAndTheKnownOnes() {
        Map<String, Object> doc = document();
        weights(doc).put("fish_caught", 3);

        assertThatThrownBy(() -> bind(doc))
                .hasMessageContaining("fish_caught")
                .hasMessageContaining(Aggregation.MOB_KILLS.key());
    }

    @Test
    @DisplayName("eine Gewichtung mit genau einem Eintrag genuegt")
    void aweightingWithExactlyOneEntryIsEnough() {
        Map<String, Object> doc = document();
        Map<String, Object> single = new LinkedHashMap<>();
        single.put(Aggregation.MOB_KILLS.key(), 1);
        score(doc).put("weights", single);

        assertThat(bind(doc).score().weights()).hasSize(1);
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

        Map<String, Object> byBoard = new LinkedHashMap<>();
        byBoard.put(Aggregation.MOB_KILLS.key(), 1);
        Map<String, Object> score = new LinkedHashMap<>();
        score.put("weights", byBoard);
        doc.put("score", score);

        return doc;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> score(Map<String, Object> doc) {
        return (Map<String, Object>) doc.get("score");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> weights(Map<String, Object> doc) {
        return (Map<String, Object>) score(doc).get("weights");
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
