package rpg.core.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import rpg.core.config.ConfigSchema;
import rpg.core.config.ConfigView;

/**
 * Je ein Fall für <b>jede Zeile</b> der Regeltabelle aus {@code contracts/ui-config.md} §2.
 *
 * <p><b>Geprüft wird nicht nur, <em>dass</em> es scheitert, sondern dass die Meldung den Schlüssel
 * nennt</b> (Prinzip V). Ein Betreiber, der acht YAML-Dateien pflegt, braucht Datei, Schlüssel und
 * Grund; „ungültige Konfiguration" schickt ihn auf die Suche, und beim übernächsten Mal schaltet er
 * die Prüfung ab. Dieselbe Begründung wie in {@code ItemConfigSchemaTest} und
 * {@code StatisticsConfigSchemaTest}.
 */
class UiConfigSchemaTest {

    @Test
    @DisplayName("ein vollstaendiges Dokument bindet")
    void aCompleteDocumentBinds() {
        UiConfig config = bind(document());

        assertThat(config.language()).isEqualTo("en");
        assertThat(config.tick().toMillis()).isEqualTo(1000);
        assertThat(config.actionBar().enabled()).isTrue();
        assertThat(config.bossBar().enabled()).isTrue();
        assertThat(config.sidebar().enabled()).isTrue();
        assertThat(config.zoneNoticeDuration().toSeconds()).isEqualTo(4);
        assertThat(config.damageNumbers().enabled()).isTrue();
        assertThat(config.damageNumbers().lifetime().toMillis()).isEqualTo(1200);
        assertThat(config.damageNumbers().offset()).isEqualTo(1.4);
    }

    @Test
    @DisplayName("Sekunden und Millisekunden werden aus dem Schluesselnamen gelesen, nicht geraten")
    void unitsComeFromTheKeyName() {
        // Die eine Stelle, die beide Formen kennt: in der Datei stehen Zahlen mit der Einheit im
        // Namen, im Modell stehen Duration-Werte. Ein zweiter Ort dafuer waeren zwei Vorstellungen
        // von derselben Zahl.
        Map<String, Object> doc = document();
        hud(doc).put("tick-ms", 500);
        bossBar(doc).put("zone-notice-seconds", 9);
        damageNumbers(doc).put("lifetime-ms", 250);

        UiConfig config = bind(doc);

        assertThat(config.tick().toMillis()).isEqualTo(500);
        assertThat(config.zoneNoticeDuration().toSeconds()).isEqualTo(9);
        assertThat(config.damageNumbers().lifetime().toMillis()).isEqualTo(250);
    }

    @Nested
    @DisplayName("language")
    class Language {

        @Test
        @DisplayName("ein leeres Kuerzel wird abgewiesen und die Meldung nennt den Schluessel")
        void aBlankCodeIsRejected() {
            Map<String, Object> doc = document();
            doc.put("language", "  ");

            assertThatThrownBy(() -> bind(doc))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("ui.yml")
                    .hasMessageContaining("language");
        }
    }

    @Nested
    @DisplayName("hud.tick-ms")
    class Tick {

        @Test
        @DisplayName("null wird abgewiesen")
        void zeroIsRejected() {
            Map<String, Object> doc = document();
            hud(doc).put("tick-ms", 0);

            assertThatThrownBy(() -> bind(doc))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("ui.yml")
                    .hasMessageContaining("hud.tick-ms")
                    .hasMessageContaining("0");
        }

        @Test
        @DisplayName("ein negativer Takt wird abgewiesen")
        void aNegativeTickIsRejected() {
            Map<String, Object> doc = document();
            hud(doc).put("tick-ms", -1000);

            assertThatThrownBy(() -> bind(doc)).hasMessageContaining("hud.tick-ms");
        }

        @Test
        @DisplayName("ein fehlender Takt wird abgewiesen")
        void aMissingTickIsRejected() {
            Map<String, Object> doc = document();
            hud(doc).remove("tick-ms");

            assertThatThrownBy(() -> bind(doc)).hasMessageContaining("hud.tick-ms");
        }
    }

    @Nested
    @DisplayName("die drei Flaechen")
    class Surfaces {

        @Test
        @DisplayName("action-bar.enabled: false bindet - abgeschaltet ist ein gueltiger Zustand")
        void aDisabledActionBarBinds() {
            Map<String, Object> doc = document();
            actionBar(doc).put("enabled", false);

            assertThat(bind(doc).actionBar().enabled()).isFalse();
            assertThat(bind(doc).isEnabled(HudSurface.ACTION_BAR)).isFalse();
        }

        @Test
        @DisplayName("boss-bar.enabled: false bindet")
        void aDisabledBossBarBinds() {
            Map<String, Object> doc = document();
            bossBar(doc).put("enabled", false);

            assertThat(bind(doc).isEnabled(HudSurface.BOSS_BAR)).isFalse();
        }

        @Test
        @DisplayName("sidebar.enabled: false bindet")
        void aDisabledSidebarBinds() {
            Map<String, Object> doc = document();
            sidebar(doc).put("enabled", false);

            assertThat(bind(doc).isEnabled(HudSurface.SIDEBAR)).isFalse();
        }

        @Test
        @DisplayName("ein Text statt eines Wahrheitswerts wird abgewiesen und nennt den Schluessel")
        void aStringInsteadOfABooleanIsRejected() {
            Map<String, Object> doc = document();
            sidebar(doc).put("enabled", "vielleicht");

            assertThatThrownBy(() -> bind(doc))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("ui.yml")
                    .hasMessageContaining("hud.sidebar.enabled")
                    .hasMessageContaining("vielleicht");
        }

        @Test
        @DisplayName("ein fehlender Abschnitt wird abgewiesen und nennt ihn")
        void aMissingSectionIsRejected() {
            Map<String, Object> doc = document();
            hud(doc).remove("sidebar");

            assertThatThrownBy(() -> bind(doc))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("hud.sidebar");
        }
    }

    @Nested
    @DisplayName("hud.boss-bar.zone-notice-seconds")
    class ZoneNotice {

        @Test
        @DisplayName("null wird abgewiesen")
        void zeroIsRejected() {
            Map<String, Object> doc = document();
            bossBar(doc).put("zone-notice-seconds", 0);

            assertThatThrownBy(() -> bind(doc))
                    .hasMessageContaining("ui.yml")
                    .hasMessageContaining("hud.boss-bar.zone-notice-seconds")
                    .hasMessageContaining("0");
        }
    }

    @Nested
    @DisplayName("damage-numbers")
    class DamageNumbers {

        @Test
        @DisplayName("eine Lebensdauer von null wird abgewiesen")
        void aZeroLifetimeIsRejected() {
            Map<String, Object> doc = document();
            damageNumbers(doc).put("lifetime-ms", 0);

            assertThatThrownBy(() -> bind(doc))
                    .hasMessageContaining("ui.yml")
                    .hasMessageContaining("damage-numbers.lifetime-ms");
        }

        @Test
        @DisplayName("ein unendlicher Versatz wird abgewiesen")
        void anInfiniteOffsetIsRejected() {
            Map<String, Object> doc = document();
            damageNumbers(doc).put("offset", Double.POSITIVE_INFINITY);

            assertThatThrownBy(() -> bind(doc))
                    .hasMessageContaining("damage-numbers.offset")
                    .hasMessageContaining("endlich");
        }

        @Test
        @DisplayName("NaN als Versatz wird abgewiesen")
        void aNanOffsetIsRejected() {
            Map<String, Object> doc = document();
            damageNumbers(doc).put("offset", Double.NaN);

            assertThatThrownBy(() -> bind(doc)).hasMessageContaining("damage-numbers.offset");
        }

        @Test
        @DisplayName("ein negativer Versatz bindet - unter dem Trefferpunkt ist erlaubt")
        void aNegativeOffsetBinds() {
            // Endlich ist die Regel, nicht positiv: eine Zahl unter dem Trefferpunkt ist eine
            // Gestaltungsentscheidung des Betreibers und kein Fehler.
            Map<String, Object> doc = document();
            damageNumbers(doc).put("offset", -0.5);

            assertThat(bind(doc).damageNumbers().offset()).isEqualTo(-0.5);
        }

        @Test
        @DisplayName("enabled: false bindet - abgeschaltet ist ein gueltiger Zustand")
        void disabledBinds() {
            Map<String, Object> doc = document();
            damageNumbers(doc).put("enabled", false);

            assertThat(bind(doc).damageNumbers().enabled()).isFalse();
        }
    }

    // --- Aufbau -------------------------------------------------------------

    private static UiConfig bind(Map<String, Object> document) {
        ConfigSchema<UiConfig> schema = UiConfigSchema.schema();
        return schema.bind(new MapView(document, UiConfigSchema.SCHEMA_VERSION));
    }

    private static Map<String, Object> document() {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("language", "en");

        Map<String, Object> hud = new LinkedHashMap<>();
        hud.put("tick-ms", 1000);
        hud.put("action-bar", new LinkedHashMap<>(Map.of("enabled", true)));
        Map<String, Object> bossBar = new LinkedHashMap<>();
        bossBar.put("enabled", true);
        bossBar.put("zone-notice-seconds", 4);
        hud.put("boss-bar", bossBar);
        hud.put("sidebar", new LinkedHashMap<>(Map.of("enabled", true)));
        doc.put("hud", hud);

        Map<String, Object> damageNumbers = new LinkedHashMap<>();
        damageNumbers.put("enabled", true);
        damageNumbers.put("lifetime-ms", 1200);
        damageNumbers.put("offset", 1.4);
        doc.put("damage-numbers", damageNumbers);

        return doc;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> hud(Map<String, Object> doc) {
        return (Map<String, Object>) doc.get("hud");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> actionBar(Map<String, Object> doc) {
        return (Map<String, Object>) hud(doc).get("action-bar");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> bossBar(Map<String, Object> doc) {
        return (Map<String, Object>) hud(doc).get("boss-bar");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> sidebar(Map<String, Object> doc) {
        return (Map<String, Object>) hud(doc).get("sidebar");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> damageNumbers(Map<String, Object> doc) {
        return (Map<String, Object>) doc.get("damage-numbers");
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
