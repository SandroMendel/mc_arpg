package rpg.core.statistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import rpg.core.config.ConfigSchema;
import rpg.core.config.ConfigView;

/**
 * Je ein Fall für jede Regel der Tabelle in {@code contracts/stats-config.md} (FR-066).
 *
 * <p><b>Geprüft wird nicht nur, <em>dass</em> es scheitert, sondern dass die Meldung den Schlüssel
 * nennt.</b> Ein Betreiber, der sieben YAML-Dateien pflegt, braucht Datei, Schlüssel und Grund;
 * „ungültige Konfiguration" schickt ihn auf die Suche, und beim übernächsten Mal schaltet er die
 * Prüfung ab. Dieselbe Begründung wie in {@code ItemConfigSchemaTest}.
 */
class StatisticsConfigSchemaTest {

    @Test
    @DisplayName("ein vollstaendiges Dokument bindet")
    void aCompleteDocumentBinds() {
        StatisticsConfig config = bind(document());

        assertThat(config.capture().killCreditShare()).isEqualTo(0.05);
        assertThat(config.capture().idleAfter().toMinutes()).isEqualTo(5);
        assertThat(config.leaderboards().places()).isEqualTo(10);
        assertThat(config.seasons().all()).hasSize(2);
        assertThat(config.score().weightOf(Aggregation.BOSS_KILLS)).isEqualTo(50.0);
        assertThat(config.rewards()).containsKey(1);
        assertThat(config.hologram()).isPresent();
    }

    @Test
    @DisplayName("ohne Hologramm-Abschnitt bindet das Dokument trotzdem")
    void withoutAHologramSectionItStillBinds() {
        Map<String, Object> doc = document();
        doc.remove("hologram");

        assertThat(bind(doc).hologram()).isEmpty();
    }

    @Nested
    @DisplayName("Erfassung")
    class Capture {

        @Test
        @DisplayName("eine Untaetigkeitsdauer von null wird abgewiesen")
        void aZeroIdleDurationIsRejected() {
            Map<String, Object> doc = document();
            capture(doc).put("idle-after-seconds", 0);

            assertThatThrownBy(() -> bind(doc))
                    .hasMessageContaining("statistics.yml")
                    .hasMessageContaining("capture.idle-after-seconds");
        }
    }

    @Nested
    @DisplayName("Ranglisten")
    class Leaderboards {

        @Test
        @DisplayName("null Plaetze werden abgewiesen")
        void zeroPlacesIsRejected() {
            Map<String, Object> doc = document();
            leaderboards(doc).put("places", 0);

            assertThatThrownBy(() -> bind(doc))
                    .hasMessageContaining("leaderboards.places")
                    .hasMessageContaining("0");
        }

        @Test
        @DisplayName("ein Auffrischungsintervall von null wird abgewiesen")
        void aZeroRefreshIntervalIsRejected() {
            Map<String, Object> doc = document();
            leaderboards(doc).put("refresh-interval-seconds", 0);

            assertThatThrownBy(() -> bind(doc))
                    .hasMessageContaining("leaderboards.refresh-interval-seconds");
        }
    }

    @Nested
    @DisplayName("Saisons")
    class Seasons {

        @Test
        @DisplayName("eine Luecke bricht den Start ab und nennt beide Saisons")
        void aGapAbortsTheStart() {
            Map<String, Object> doc = document();
            secondSeason(doc).put("from", "2026-10-02");

            assertThatThrownBy(() -> bind(doc))
                    .hasMessageContaining("2026-q3")
                    .hasMessageContaining("2026-q4")
                    .hasMessageContaining("Luecke");
        }

        @Test
        @DisplayName("ein unlesbares Datum nennt den Schluessel und die erwartete Form")
        void anUnreadableDateNamesTheKey() {
            Map<String, Object> doc = document();
            secondSeason(doc).put("from", "Oktober");

            assertThatThrownBy(() -> bind(doc))
                    .hasMessageContaining("seasons[2026-q4].from")
                    .hasMessageContaining("2026-07-01");
        }
    }

    @Nested
    @DisplayName("Gesamtwertung")
    class Score {

        @Test
        @DisplayName("eine leere Gewichtung wird abgewiesen, mit Begruendung")
        void anEmptyWeightingIsRejected() {
            Map<String, Object> doc = document();
            score(doc).put("weights", new LinkedHashMap<String, Object>());

            assertThatThrownBy(() -> bind(doc))
                    .hasMessageContaining("score.weights")
                    .hasMessageContaining("Erstbesten");
        }

        @Test
        @DisplayName("eine unbekannte Rangliste nennt die bekannten")
        void anUnknownBoardListsTheKnownOnes() {
            Map<String, Object> doc = document();
            weights(doc).put("fish_caught", 3);

            assertThatThrownBy(() -> bind(doc))
                    .hasMessageContaining("score.weights.fish_caught")
                    .hasMessageContaining(Aggregation.MOB_KILLS.key());
        }

        @Test
        @DisplayName("ADR-046 - eine private Rangliste darf keine Gewichtung tragen")
        void aPrivateBoardCarriesNoWeight() {
            Map<String, Object> doc = document();
            weights(doc).put(Aggregation.PLAYTIME_ONLINE.key(), 1);

            assertThatThrownBy(() -> bind(doc))
                    .hasMessageContaining(Aggregation.PLAYTIME_ONLINE.key())
                    .hasMessageContaining("oeffentlich");
        }

        @Test
        @DisplayName("ein negatives Gewicht wird abgewiesen")
        void aNegativeWeightIsRejected() {
            Map<String, Object> doc = document();
            weights(doc).put(Aggregation.MOB_KILLS.key(), -1);

            assertThatThrownBy(() -> bind(doc)).hasMessageContaining("negativ");
        }

        @Test
        @DisplayName("die Summe der Tode darf gewichtet werden - privat ist die Aufschluesselung")
        void theDeathTotalMayCarryAWeight() {
            // FR-037: die Gesamtzahl steht in jedem fremden Profil. Privat (ADR-043) ist
            // deaths.<verursacher>, nicht die Summe - und Aggregation traegt deshalb ihre eigene
            // Sichtbarkeit, statt sie von der Metrik zu erben.
            assertThat(bind(document()).score().weightOf(Aggregation.DEATHS)).isZero();
            assertThat(Aggregation.DEATHS.scoreable()).isTrue();
            assertThat(MetricRegistry.DEATHS.visibility()).isEqualTo(MetricVisibility.PRIVATE);
        }
    }

    @Nested
    @DisplayName("Belohnungen")
    class Rewards {

        @Test
        @DisplayName("ein Platz kleiner eins wird abgewiesen")
        void aPlaceBelowOneIsRejected() {
            Map<String, Object> doc = document();
            rewards(doc).put(0, Map.of("coins", 100));

            assertThatThrownBy(() -> bind(doc)).hasMessageContaining("rewards.0");
        }

        @Test
        @DisplayName("eine Stueckzahl von null wird abgewiesen")
        void aZeroAmountIsRejected() {
            Map<String, Object> doc = document();
            rewards(doc)
                    .put(
                            4,
                            Map.of(
                                    "items",
                                    List.of(Map.of("template", "cosmetic_trim_gold", "amount", 0))));

            assertThatThrownBy(() -> bind(doc)).hasMessageContaining("amount");
        }

        @Test
        @DisplayName("eine Belohnung ohne Inhalt wird abgewiesen")
        void anEmptyRewardIsRejected() {
            Map<String, Object> doc = document();
            rewards(doc).put(4, new LinkedHashMap<String, Object>());

            assertThatThrownBy(() -> bind(doc))
                    .hasMessageContaining("rewards.4")
                    .hasMessageContaining("weder Coins noch");
        }
    }

    @Nested
    @DisplayName("Hologramm")
    class Hologram {

        @Test
        @DisplayName("eine unbekannte Rangliste nennt die bekannten")
        void anUnknownBoardIsRejected() {
            Map<String, Object> doc = document();
            hologram(doc).put("board", "most_fish");

            assertThatThrownBy(() -> bind(doc))
                    .hasMessageContaining("hologram.board")
                    .hasMessageContaining("most_fish");
        }

        @Test
        @DisplayName("eine private Rangliste gehoert nicht in den Hub")
        void aPrivateBoardDoesNotBelongInTheHub() {
            Map<String, Object> doc = document();
            hologram(doc).put("board", Aggregation.PLAYTIME_ONLINE.key());

            assertThatThrownBy(() -> bind(doc)).hasMessageContaining("privat");
        }

        @Test
        @DisplayName("ein unbekannter Zeitraum nennt die vier")
        void anUnknownPeriodIsRejected() {
            Map<String, Object> doc = document();
            hologram(doc).put("period", "fortnight");

            assertThatThrownBy(() -> bind(doc))
                    .hasMessageContaining("hologram.period")
                    .hasMessageContaining("all_time");
        }
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
        seasons.add(season("2026-q3", "2026-07-01", "2026-09-30"));
        seasons.add(season("2026-q4", "2026-10-01", "2026-12-31"));
        doc.put("seasons", seasons);

        Map<String, Object> weights = new LinkedHashMap<>();
        weights.put(Aggregation.MOB_KILLS.key(), 1);
        weights.put(Aggregation.BOSS_KILLS.key(), 50);
        weights.put(Aggregation.PLAYTIME_ACTIVE.key(), 2);
        weights.put(Aggregation.DAMAGE_MAX.key(), 0);
        weights.put(Aggregation.DEATHS.key(), 0);
        Map<String, Object> score = new LinkedHashMap<>();
        score.put("weights", weights);
        doc.put("score", score);

        Map<Object, Object> rewards = new LinkedHashMap<>();
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("coins", 5000);
        first.put("items", List.of(Map.of("template", "cosmetic_trim_gold", "amount", 1)));
        rewards.put(1, first);
        rewards.put(2, new LinkedHashMap<>(Map.of("coins", 2500)));
        doc.put("rewards", rewards);

        Map<String, Object> hologram = new LinkedHashMap<>();
        hologram.put("world", "world");
        hologram.put("x", 0.5);
        hologram.put("y", 65.0);
        hologram.put("z", 0.5);
        hologram.put("board", Aggregation.MOB_KILLS.key());
        hologram.put("period", "season");
        hologram.put("places", 10);
        doc.put("hologram", hologram);

        return doc;
    }

    private static Map<String, Object> season(String key, String from, String to) {
        Map<String, Object> season = new LinkedHashMap<>();
        season.put("key", key);
        season.put("from", from);
        season.put("to", to);
        return season;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> capture(Map<String, Object> doc) {
        return (Map<String, Object>) doc.get("capture");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> leaderboards(Map<String, Object> doc) {
        return (Map<String, Object>) doc.get("leaderboards");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> secondSeason(Map<String, Object> doc) {
        return (Map<String, Object>) ((List<Object>) doc.get("seasons")).get(1);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> score(Map<String, Object> doc) {
        return (Map<String, Object>) doc.get("score");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> weights(Map<String, Object> doc) {
        return (Map<String, Object>) score(doc).get("weights");
    }

    @SuppressWarnings("unchecked")
    private static Map<Object, Object> rewards(Map<String, Object> doc) {
        return (Map<Object, Object>) doc.get("rewards");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> hologram(Map<String, Object> doc) {
        return (Map<String, Object>) doc.get("hologram");
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
