package rpg.core.currency;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.config.ConfigSchema;
import rpg.core.config.SchemaValidator;

/**
 * T119a - eine geaenderte Zahl wirkt, ohne dass Code geaendert wird (SC-009, Prinzip V).
 *
 * <p>Das Erfolgskriterium des Projekts lautet, dass neue Inhalte und neues Balancing ohne
 * Codeaenderung ergaenzbar sind. Ohne diesen Test waere das eine Behauptung: jede einzelne Zahl
 * <em>koennte</em> aus der Konfiguration kommen und trotzdem irgendwo hartcodiert danebenstehen.
 */
class CurrencyConfigEffectTest {

    private static final Logger QUIET = Logger.getLogger("currency-config-effect-test");
    private static final Path SOURCE = Path.of("currency.yml");

    @Test
    @DisplayName("ein geaenderter Kreatur-Ertrag wirkt nach dem Neuladen")
    void achangedDropTakesEffect() throws Exception {
        QUIET.setLevel(Level.OFF);

        CurrencyConfig before = load(document(5));
        assertThat(new ConfigMobCoinProvider(before, QUIET).coinsFor("ZOMBIE")).hasValue(5L);

        // Nur die Zahl in der Datei geaendert - keine Zeile Code.
        CurrencyConfig after = load(document(42));
        assertThat(new ConfigMobCoinProvider(after, QUIET).coinsFor("ZOMBIE")).hasValue(42L);
    }

    @Test
    @DisplayName("ein geaenderter Standardertrag wirkt fuer alles ohne eigenen Eintrag")
    void achangedDefaultTakesEffect() throws Exception {
        Map<String, Object> document = document(5);
        section(document, "drops").put("default", 77L);

        assertThat(load(document).dropFor("WAS_NIEMAND_KENNT")).isEqualTo(77L);
    }

    @Test
    @DisplayName("ein geaendertes Startguthaben wirkt fuer neue Charaktere")
    void achangedStartingBalanceTakesEffect() throws Exception {
        Map<String, Object> document = document(5);
        section(document, "account").put("starting-balance", 750L);

        assertThat(load(document).startingBalance()).isEqualTo(750L);
    }

    @Test
    @DisplayName("eine geaenderte Rangkosten-Zahl wirkt, ohne dass Code sie kennt")
    void achangedRankCostTakesEffect() {
        // Der Preis reist als undurchsichtige Map durch B08 und wird hier ausgelegt. Ihn zu aendern
        // heisst, eine Zahl in abilities.yml zu aendern - mehr nicht (FR-053).
        assertThat(CostSpec.parse(Map.of("coins", 250), "abilities.probe").coins()).isEqualTo(250L);
        assertThat(CostSpec.parse(Map.of("coins", 900), "abilities.probe").coins()).isEqualTo(900L);
    }

    @Test
    @DisplayName("eine geaenderte Stufenkosten-Zahl ebenso")
    void achangedTierCostTakesEffect() {
        assertThat(CostSpec.parse(Map.of("coins", 500), "classes.warrior.armor.tier 2").coins())
                .isEqualTo(500L);
        assertThat(CostSpec.parse(Map.of("coins", 2500), "classes.warrior.armor.tier 3").coins())
                .isEqualTo(2500L);
    }

    @Test
    @DisplayName("keine dieser Zahlen steht im Code")
    void noneOfTheseNumbersLivesInCode() throws Exception {
        // Die Gegenprobe: waeren sie hartcodiert, waere der Auslieferungswert von einer geaenderten
        // Konfiguration nicht zu unterscheiden.
        CurrencyConfig shipped = load(document(5));
        CurrencyConfig retuned = load(document(5000));

        assertThat(shipped.dropFor("ZOMBIE")).isNotEqualTo(retuned.dropFor("ZOMBIE"));
    }

    // --- Hilfsmittel -----------------------------------------------------

    private static CurrencyConfig load(Map<String, Object> document) throws Exception {
        ConfigSchema<CurrencyConfig> schema = CurrencyConfigSchema.schema();
        return schema.bind(SchemaValidator.validate(SOURCE, document, schema));
    }

    private static Map<String, Object> document(long zombieDrop) {
        Map<String, Object> account = new LinkedHashMap<>();
        account.put("starting-balance", 0L);

        Map<String, Object> byType = new LinkedHashMap<>();
        byType.put("ZOMBIE", zombieDrop);

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
}
