package rpg.core.progression;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.config.ConfigSchema;
import rpg.core.config.ConfigValidationException;
import rpg.core.config.FieldDefinition;
import rpg.core.config.SchemaValidator;
import rpg.core.stats.Attribute;

/**
 * The schema of {@code progression.yml} (FR-002, FR-003, FR-005, SC-003).
 *
 * <p>Every failing case asserts the <b>message</b>. An operator editing a sixty-line table has to be
 * told which line is wrong; "invalid configuration" would send them reading all sixty.
 *
 * <p>Each test goes through {@code SchemaValidator} and then the binder, which is exactly the route
 * the loader takes. Testing the binder alone would skip the "field missing" case, and that is the
 * one an operator hits most often.
 */
class ProgressionConfigSchemaTest {

    private static final Path SOURCE = Path.of("progression.yml");

    @Test
    @DisplayName("a complete configuration loads, and the maximum level comes from the curve")
    void completeConfigurationLoads() throws Exception {
        ProgressionConfig config = load(document());

        assertThat(config.maxLevel()).as("from the table, not a constant").isEqualTo(10);
        assertThat(config.mobXpDefault()).isEqualTo(10L);
        assertThat(config.mobXpFor("ZOMBIE")).hasValue(12L);
        assertThat(config.mobXpFor("SHEEP")).as("no entry of its own").isEmpty();
        assertThat(config.partyMaxSize()).isEqualTo(5);
        assertThat(config.growth().perLevel(Attribute.HEALTH)).isEqualTo(8.0);
        assertThat(config.growth().perLevel(Attribute.MOVEMENT_SPEED)).isZero();
    }

    @Test
    @DisplayName("a gap in the curve names the missing level")
    void curveGapNamesTheLevel() {
        Map<String, Object> document = document();
        curveOf(document).remove(7);

        assertThatThrownBy(() -> load(document)).hasMessageContaining("level 7 is missing");
    }

    @Test
    @DisplayName("a non-positive curve value names the level and the value")
    void curveZeroNamesTheLevel() {
        Map<String, Object> document = document();
        curveOf(document).put(5, 0);

        assertThatThrownBy(() -> load(document))
                .hasMessageContaining("level 5 must be positive")
                .hasMessageContaining("but was 0");
    }

    @Test
    @DisplayName("a curve that stops rising names both levels")
    void curveNotRisingNamesBothLevels() {
        Map<String, Object> document = document();
        curveOf(document).put(6, 10);

        assertThatThrownBy(() -> load(document))
                .hasMessageContaining("level 6 must be greater than level 5");
    }

    @Test
    @DisplayName("a curve without level 2 is rejected")
    void curveWithoutLevelTwo() {
        Map<String, Object> document = document();
        curveOf(document).remove(2);

        assertThatThrownBy(() -> load(document)).hasMessageContaining("must define at least level 2");
    }

    @Test
    @DisplayName("a curve key that is not a number names the key")
    void curveKeyNotANumber() {
        Map<String, Object> document = document();
        curveOf(document).put("zwei", 100);

        assertThatThrownBy(() -> load(document)).hasMessageContaining("'zwei' is not a level");
    }

    @Test
    @DisplayName("a bonus cap below the per-member step is rejected rather than silently useless")
    void bonusCapBelowPerMember() {
        Map<String, Object> document = document();
        partyOf(document).put("bonus-per-member", 0.20);
        partyOf(document).put("bonus-cap", 0.05);

        // A cap under the per-member step would cap the bonus at the second member and make
        // bonus-per-member meaningless. Better a startup error than a number that does nothing.
        assertThatThrownBy(() -> load(document))
                .hasMessageContaining("bonus-cap")
                .hasMessageContaining("bonus-per-member");
    }

    @Test
    @DisplayName("a missing growth field stops the start instead of quietly becoming zero")
    void missingGrowthFieldIsRejected() {
        Map<String, Object> document = document();
        growthOf(document).remove("movementSpeed");

        // Otherwise "movement speed does not grow" is indistinguishable from "somebody forgot the
        // line" - the same reason B05 makes every environment source a required field.
        assertThatThrownBy(() -> load(document))
                .isInstanceOf(ConfigValidationException.class)
                .hasMessageContaining("movementSpeed");
    }

    @Test
    @DisplayName("every one of the eight growth fields is required")
    void allEightGrowthFieldsAreRequired() {
        List<String> declared =
                ProgressionConfigSchema.schema().fields().stream()
                        .filter(FieldDefinition::required)
                        .map(FieldDefinition::path)
                        .filter(path -> path.startsWith("level-growth."))
                        .toList();

        assertThat(declared).hasSize(Attribute.count());
        for (Attribute attribute : Attribute.all()) {
            assertThat(declared).contains("level-growth." + attribute.key());
        }
    }

    @Test
    @DisplayName("a mob amount below 1 names the type")
    void mobAmountBelowOne() {
        Map<String, Object> document = document();
        byTypeOf(document).put("ZOMBIE", 0);

        assertThatThrownBy(() -> load(document))
                .hasMessageContaining("mob-xp.by-type.ZOMBIE")
                .hasMessageContaining("at least 1");
    }

    @Test
    @DisplayName("a party size below 1 is rejected")
    void partySizeBelowOne() {
        Map<String, Object> document = document();
        partyOf(document).put("max-size", 0);

        assertThatThrownBy(() -> load(document)).hasMessageContaining("party.max-size");
    }

    // --- helpers ---------------------------------------------------------

    /** The full route the loader takes: validate, then bind. */
    private static ProgressionConfig load(Map<String, Object> document) throws Exception {
        ConfigSchema<ProgressionConfig> schema = ProgressionConfigSchema.schema();
        return schema.bind(SchemaValidator.validate(SOURCE, document, schema));
    }

    /** A valid nested document: curve 2..10, all eight growth fields, party and window values. */
    private static Map<String, Object> document() {
        Map<Object, Object> curve = new LinkedHashMap<>();
        long value = 100L;
        for (int level = 2; level <= 10; level++) {
            curve.put(level, value);
            value += 20L;
        }

        Map<String, Object> growth = new LinkedHashMap<>();
        growth.put("health", 8.0);
        growth.put("defense", 2.0);
        growth.put("mana", 4.0);
        growth.put("physicalDamage", 1.5);
        growth.put("magicDamage", 1.5);
        growth.put("attackSpeed", 0.0);
        growth.put("movementSpeed", 0.0);
        growth.put("abilityCooldown", 0.0);

        Map<String, Object> byType = new LinkedHashMap<>();
        byType.put("ZOMBIE", 12);
        byType.put("CREEPER", 18);

        Map<String, Object> mobXp = new LinkedHashMap<>();
        mobXp.put("default", 10);
        mobXp.put("by-type", byType);

        Map<String, Object> party = new LinkedHashMap<>();
        party.put("max-size", 5);
        party.put("range-blocks", 50.0);
        party.put("bonus-per-member", 0.10);
        party.put("bonus-cap", 0.40);
        party.put("invite-timeout-seconds", 60);

        Map<String, Object> progressEvent = new LinkedHashMap<>();
        progressEvent.put("window-millis", 500);

        Map<String, Object> document = new LinkedHashMap<>();
        document.put("xp-curve", curve);
        document.put("level-growth", growth);
        document.put("mob-xp", mobXp);
        document.put("party", party);
        document.put("progress-event", progressEvent);
        return document;
    }

    @SuppressWarnings("unchecked")
    private static Map<Object, Object> curveOf(Map<String, Object> document) {
        return (Map<Object, Object>) document.get("xp-curve");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> growthOf(Map<String, Object> document) {
        return (Map<String, Object>) document.get("level-growth");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> partyOf(Map<String, Object> document) {
        return (Map<String, Object>) document.get("party");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> byTypeOf(Map<String, Object> document) {
        return (Map<String, Object>) ((Map<String, Object>) document.get("mob-xp")).get("by-type");
    }
}
