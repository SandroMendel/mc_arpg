package rpg.core.currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import rpg.core.config.ConfigSchema;
import rpg.core.config.SchemaValidator;

/**
 * T015 - das Schema von {@code currency.yml} (FR-058, Prinzip V).
 *
 * <p>Jeder Fehlerfall prueft die <b>Meldung</b>. Ein Betreiber, der eine Zahl falsch setzt, muss
 * erfahren welche - „ungueltige Konfiguration" schickt ihn durch die ganze Datei.
 *
 * <p>Jeder Lauf geht durch {@code SchemaValidator} und dann den Binder, also genau den Weg des
 * Laders. Nur den Binder zu pruefen liesse den Fall „Feld fehlt" aus, und das ist der, den ein
 * Betreiber am haeufigsten trifft.
 */
class CurrencyConfigValidationTest {

    private static final Path SOURCE = Path.of("currency.yml");

    @Test
    @DisplayName("die ausgelieferte Konfiguration laedt")
    void shippedConfigurationLoads() throws Exception {
        CurrencyConfig config = load(document());

        assertThat(config.startingBalance()).as("null im Auslieferungszustand").isZero();
        assertThat(config.defaultDrop()).isEqualTo(4L);
        assertThat(config.pileDespawn().toSeconds()).isEqualTo(120L);
        assertThat(config.mergeRadius()).isEqualTo(3.0d);
        assertThat(config.maxPiles()).isEqualTo(400);
        assertThat(config.ledgerRetention().toDays()).isEqualTo(30L);
        assertThat(config.historyPageSize()).isEqualTo(45);
    }

    @Nested
    @DisplayName("Kreatur-Ertraege")
    class Drops {

        @Test
        @DisplayName("ein fehlender Eintrag ergibt den Standardbetrag, nicht null (FR-023)")
        void missingEntryFallsBackToDefault() throws Exception {
            CurrencyConfig config = load(document());

            assertThat(config.dropFor("ZOMBIE")).as("eigener Eintrag").isEqualTo(5L);
            assertThat(config.dropFor("WAS_MOJANG_LETZTE_WOCHE_ERGAENZT_HAT"))
                    .as("kein eigener Eintrag - der Standardbetrag, nicht null")
                    .isEqualTo(4L);
        }

        @Test
        @DisplayName("eine ausdrueckliche Null bedeutet null - sie ist gewaehlt, nicht vergessen")
        void explicitZeroMeansZero() throws Exception {
            Map<String, Object> document = document();
            byType(document).put("SLIME", 0);

            assertThat(load(document).dropFor("SLIME")).isZero();
        }

        @Test
        @DisplayName("Schluessel werden gross geschrieben, damit ein klein geschriebener trifft")
        void keysAreUpperCased() throws Exception {
            Map<String, Object> document = document();
            byType(document).put("piglin", 7);

            assertThat(load(document).dropFor("PIGLIN"))
                    .as("sonst waere der Eintrag eine Balancing-Entscheidung, die nie wirkt")
                    .isEqualTo(7L);
        }

        @Test
        @DisplayName("ein negativer Ertrag wird abgelehnt und nennt den Typ")
        void negativeDropIsRejected() {
            Map<String, Object> document = document();
            byType(document).put("CREEPER", -1);

            assertThatThrownBy(() -> load(document))
                    .hasMessageContaining("currency.drops.by-type.CREEPER")
                    .hasMessageContaining("negativ");
        }

        @Test
        @DisplayName("ein nicht-numerischer Ertrag wird abgelehnt und nennt den Typ")
        void nonNumericDropIsRejected() {
            Map<String, Object> document = document();
            byType(document).put("CREEPER", "viele");

            assertThatThrownBy(() -> load(document))
                    .hasMessageContaining("currency.drops.by-type.CREEPER");
        }

        @Test
        @DisplayName("ein leerer Typ-Schluessel wird abgelehnt")
        void blankTypeIsRejected() {
            Map<String, Object> document = document();
            byType(document).put("   ", 5);

            assertThatThrownBy(() -> load(document)).hasMessageContaining("empty creature type");
        }
    }

    @Nested
    @DisplayName("Grenzwerte")
    class Bounds {

        @Test
        @DisplayName("ein negatives Startguthaben wird abgelehnt")
        void negativeStartingBalanceIsRejected() {
            Map<String, Object> document = document();
            put(document, "account", "starting-balance", -1L);

            assertThatThrownBy(() -> load(document))
                    .hasMessageContaining("account.starting-balance");
        }

        @Test
        @DisplayName("ein Startguthaben ueber null ist zulaessig - es wird spaeter gebucht")
        void positiveStartingBalanceIsAllowed() throws Exception {
            Map<String, Object> document = document();
            put(document, "account", "starting-balance", 500L);

            assertThat(load(document).startingBalance()).isEqualTo(500L);
        }

        @Test
        @DisplayName("eine Verfallszeit von null wird abgelehnt")
        void zeroDespawnIsRejected() {
            Map<String, Object> document = document();
            put(document, "drops", "despawn-seconds", 0);

            assertThatThrownBy(() -> load(document))
                    .hasMessageContaining("drops.despawn-seconds");
        }

        @Test
        @DisplayName("eine Verfallszeit ueber 300s wird abgelehnt - sie koennte gar nicht wirken")
        void despawnBeyondVanillaLifetimeIsRejected() {
            Map<String, Object> document = document();
            put(document, "drops", "despawn-seconds", 600);

            assertThatThrownBy(() -> load(document))
                    .as("eine Zahl, die nicht wirkt, ist schlimmer als keine (research.md R1c)")
                    .hasMessageContaining("drops.despawn-seconds")
                    .hasMessageContaining("pre-aged");
        }

        @Test
        @DisplayName("genau die Vanilla-Lebensdauer geht noch, und der Haufen lebt einen Tick")
        void exactlyVanillaLifetimeIsAllowed() throws Exception {
            Map<String, Object> document = document();
            put(document, "drops", "despawn-seconds", 300);

            CurrencyConfig config = load(document);

            assertThat(config.pileDespawn().toSeconds()).isEqualTo(300L);
            assertThat(config.spawnTicksLived())
                    .as("nicht null - ein Haufen, der schon abgelaufen ankommt, waere nie zu sehen")
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("die Vorablterung rechnet die konfigurierte Frist korrekt um")
        void preAgingMatchesTheConfiguredLifetime() throws Exception {
            assertThat(load(document()).spawnTicksLived())
                    .as("6000 - 120*20 = 3600")
                    .isEqualTo(3600);
        }

        @Test
        @DisplayName("ein merge-radius ueber 16 wird abgelehnt - das waere keine Umkreisabfrage mehr")
        void oversizedMergeRadiusIsRejected() {
            Map<String, Object> document = document();
            put(document, "drops", "merge-radius", 32.0d);

            assertThatThrownBy(() -> load(document))
                    .hasMessageContaining("drops.merge-radius")
                    .hasMessageContaining("nearby lookup");
        }

        @Test
        @DisplayName("ein merge-radius von null wird abgelehnt")
        void zeroMergeRadiusIsRejected() {
            Map<String, Object> document = document();
            put(document, "drops", "merge-radius", 0.0d);

            assertThatThrownBy(() -> load(document)).hasMessageContaining("drops.merge-radius");
        }

        @Test
        @DisplayName("max-piles gleich null wird abgelehnt")
        void zeroMaxPilesIsRejected() {
            Map<String, Object> document = document();
            put(document, "drops", "max-piles", 0);

            assertThatThrownBy(() -> load(document)).hasMessageContaining("drops.max-piles");
        }

        @Test
        @DisplayName("eine Aufbewahrungsdauer von null wird abgelehnt")
        void zeroRetentionIsRejected() {
            Map<String, Object> document = document();
            put(document, "ledger", "retention-days", 0);

            assertThatThrownBy(() -> load(document)).hasMessageContaining("ledger.retention-days");
        }

        @Test
        @DisplayName("eine page-size ueber 45 wird abgelehnt - die unterste Reihe traegt die Knoepfe")
        void oversizedPageSizeIsRejected() {
            Map<String, Object> document = document();
            put(document, "history", "page-size", 54);

            assertThatThrownBy(() -> load(document))
                    .hasMessageContaining("history.page-size")
                    .hasMessageContaining("paging buttons");
        }
    }

    @Nested
    @DisplayName("fehlende Felder halten den Start an")
    class MissingFields {

        @Test
        @DisplayName("ein fehlendes Startguthaben wird NICHT stillschweigend null")
        void missingStartingBalanceStopsTheStart() {
            Map<String, Object> document = document();
            section(document, "account").remove("starting-balance");

            assertThatThrownBy(() -> load(document))
                    .as("sonst waere 'startet mit nichts' nicht von 'Zeile geloescht' zu trennen")
                    .hasMessageContaining("account.starting-balance");
        }

        @Test
        @DisplayName("ein fehlender Standardertrag haelt den Start an")
        void missingDefaultDropStopsTheStart() {
            Map<String, Object> document = document();
            section(document, "drops").remove("default");

            assertThatThrownBy(() -> load(document)).hasMessageContaining("drops.default");
        }

        @Test
        @DisplayName("eine fehlende page-size haelt den Start an")
        void missingPageSizeStopsTheStart() {
            Map<String, Object> document = document();
            section(document, "history").remove("page-size");

            assertThatThrownBy(() -> load(document)).hasMessageContaining("history.page-size");
        }
    }

    // --- Hilfsmittel -------------------------------------------------------------------------

    private static CurrencyConfig load(Map<String, Object> document) throws Exception {
        ConfigSchema<CurrencyConfig> schema = CurrencyConfigSchema.schema();
        return schema.bind(SchemaValidator.validate(SOURCE, document, schema));
    }

    /** Die ausgelieferte Konfiguration, als veraenderbare Karte. */
    private static Map<String, Object> document() {
        Map<String, Object> account = new LinkedHashMap<>();
        account.put("starting-balance", 0L);

        Map<String, Object> byType = new LinkedHashMap<>();
        byType.put("ZOMBIE", 5);
        byType.put("SKELETON", 5);
        byType.put("CREEPER", 8);
        byType.put("ENDERMAN", 18);

        Map<String, Object> drops = new LinkedHashMap<>();
        drops.put("default", 4L);
        drops.put("by-type", byType);
        drops.put("despawn-seconds", 120);
        drops.put("merge-radius", 3.0d);
        drops.put("max-piles", 400);

        Map<String, Object> ledger = new LinkedHashMap<>();
        ledger.put("retention-days", 30);

        Map<String, Object> history = new LinkedHashMap<>();
        history.put("page-size", 45);

        Map<String, Object> document = new LinkedHashMap<>();
        document.put("account", account);
        document.put("drops", drops);
        document.put("ledger", ledger);
        document.put("history", history);
        return document;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> section(Map<String, Object> document, String name) {
        return (Map<String, Object>) document.get(name);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> byType(Map<String, Object> document) {
        return (Map<String, Object>) section(document, "drops").get("by-type");
    }

    private static void put(
            Map<String, Object> document, String section, String key, Object value) {
        section(document, section).put(key, value);
    }
}
