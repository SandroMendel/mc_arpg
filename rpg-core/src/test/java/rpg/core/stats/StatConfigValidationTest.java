package rpg.core.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.config.ConfigSchema;
import rpg.core.config.ConfigValidationException;

/**
 * T083, T084: every rule of {@code stats.yml}, checked through the message rather than the
 * exception type (FR-003, FR-014a, SC-009).
 *
 * <p>The message is the deliverable here. An operator who edits a balancing file and gets
 * "IllegalArgumentException" learns that something is wrong; one who gets "attribute 'health': min
 * (0.0) must be at least 1.0" learns what to change.
 */
class StatConfigValidationTest {

    private static final ConfigSchema<StatConfig> SCHEMA = StatConfig.schema();
    private static final Path SOURCE = Path.of("stats.yml");

    /** A complete, valid document; individual tests break exactly one thing in it. */
    private static Map<String, Object> validDocument() {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("health", attribute(100.0, 1.0, 2000.0, null));
        attributes.put("defense", attribute(0.0, 0.0, 300.0, null));
        attributes.put("mana", attribute(50.0, 0.0, 500.0, null));
        attributes.put("physicalDamage", attribute(5.0, 0.0, 150.0, null));
        attributes.put("magicDamage", attribute(5.0, 0.0, 150.0, null));
        attributes.put("attackSpeed", attribute(4.0, 0.0, 1024.0, 0.50));
        attributes.put("movementSpeed", attribute(0.1, 0.0, 1.0, 0.30));
        attributes.put("abilityCooldown", attribute(0.0, 0.0, 0.40, null));

        Map<String, Object> document = new LinkedHashMap<>();
        document.put("attributes", attributes);
        return document;
    }

    private static Map<String, Object> attribute(double base, double min, double max, Double band) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("base", base);
        values.put("min", min);
        values.put("max", max);
        if (band != null) {
            values.put("modifier-band", band);
        }
        return values;
    }

    /** The nested map for one attribute, so a test can break exactly one field. */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> attributeOf(Map<String, Object> document, String key) {
        return (Map<String, Object>) ((Map<String, Object>) document.get("attributes")).get(key);
    }

    private static StatConfig load(Map<String, Object> document) throws ConfigValidationException {
        MapConfigLoader loader = new MapConfigLoader();
        loader.put(SOURCE, document);
        return loader.loadAndValidate(SOURCE, SCHEMA);
    }

    @Test
    @DisplayName("the shipped document is accepted and produces the shipped values")
    void validDocumentLoads() throws Exception {
        StatConfig config = load(validDocument());

        assertThat(config.definitions()).hasSize(Attribute.count());
        assertThat(config.definition(Attribute.HEALTH).base()).isEqualTo(100.0);
        assertThat(config.definition(Attribute.ABILITY_COOLDOWN).max()).isEqualTo(0.40);
        assertThat(config.definition(Attribute.ATTACK_SPEED).modifierBand()).isEqualTo(0.50);
    }

    @Test
    @DisplayName("rule 1: a missing attribute is named")
    void missingAttribute() {
        Map<String, Object> values = validDocument();
        @SuppressWarnings("unchecked")
        Map<String, Object> attributes = (Map<String, Object>) values.get("attributes");
        attributes.remove("magicDamage");

        assertThatThrownBy(() -> load(values))
                .isInstanceOf(ConfigValidationException.class)
                .hasMessageContaining("magicDamage");
    }

    @Test
    @DisplayName("rule 2: a missing field is named together with its attribute")
    void missingField() {
        Map<String, Object> values = validDocument();
        attributeOf(values, "mana").remove("min");

        assertThatThrownBy(() -> load(values))
                .isInstanceOf(ConfigValidationException.class)
                .hasMessageContaining("mana")
                .hasMessageContaining("min");
    }

    @Test
    @DisplayName("rule 3: min must lie below max")
    void minAboveMax() {
        Map<String, Object> values = validDocument();
        attributeOf(values, "defense").put("min", 400.0);

        assertThatThrownBy(() -> load(values))
                .hasMessageContaining("defense")
                .hasMessageContaining("min");
    }

    @Test
    @DisplayName("rule 4: base must lie inside the bounds")
    void baseOutsideBounds() {
        Map<String, Object> values = validDocument();
        attributeOf(values, "health").put("max", 5.0);

        assertThatThrownBy(() -> load(values))
                .hasMessageContaining("health")
                .hasMessageContaining("base");
    }

    @Test
    @DisplayName("rule 5: health may not have a maximum of zero")
    void healthFloor() {
        Map<String, Object> values = validDocument();
        attributeOf(values, "health").put("min", 0.0);

        assertThatThrownBy(() -> load(values))
                .hasMessageContaining("health")
                .hasMessageContaining("min");
    }

    @Test
    @DisplayName("rule 6: a percent attribute stays inside [-1, 1]")
    void percentRange() {
        Map<String, Object> values = validDocument();
        attributeOf(values, "abilityCooldown").put("max", 40.0); // 4000% rather than 40%

        assertThatThrownBy(() -> load(values))
                .hasMessageContaining("abilityCooldown")
                .hasMessageContaining("percent");
    }

    @Test
    @DisplayName("rule 7: the speeds require a band")
    void bandRequired() {
        Map<String, Object> values = validDocument();
        attributeOf(values, "movementSpeed").remove("modifier-band");

        assertThatThrownBy(() -> load(values))
                .isInstanceOf(ConfigValidationException.class)
                .hasMessageContaining("movementSpeed")
                .hasMessageContaining("modifier-band");
    }

    @Test
    @DisplayName("rule 8: a band of zero on a speed is refused")
    void bandMustBePositive() {
        Map<String, Object> values = validDocument();
        attributeOf(values, "attackSpeed").put("modifier-band", 0.0);

        assertThatThrownBy(() -> load(values))
                .hasMessageContaining("attackSpeed")
                .hasMessageContaining("modifier-band");
    }

    @Test
    @DisplayName("rule 9: a band elsewhere is refused rather than silently ignored")
    void strayBandRefused() {
        // Not pedantry: a band on health would look like a working setting to an operator and do
        // nothing at all.
        assertThatThrownBy(
                        () -> new AttributeDefinition(Attribute.HEALTH, 100.0, 1.0, 2000.0, 0.25))
                .hasMessageContaining("health")
                .hasMessageContaining("no effect");
    }

    @Test
    @DisplayName("an unknown attribute key names the eight that exist")
    void unknownAttributeKey() {
        assertThatThrownBy(() -> Attribute.byKey("attackspeed"))
                .isInstanceOf(UnknownAttributeException.class)
                .hasMessageContaining("attackSpeed")
                .hasMessageContaining("movementSpeed");
    }

    @Test
    @DisplayName("the shipped defaults and the shipped document agree")
    void defaultsMatchTheShippedDocument() throws Exception {
        StatConfig fromFile = load(validDocument());
        StatConfig fromCode = StatConfig.defaults();

        for (Attribute attribute : Attribute.all()) {
            assertThat(fromFile.definition(attribute))
                    .as(attribute.key())
                    .isEqualTo(fromCode.definition(attribute));
        }
    }

    @Test
    @DisplayName("a document that only changes numbers is accepted - balancing needs no code change")
    void rebalancingNeedsNoCode() {
        Map<String, Object> values = validDocument();
        attributeOf(values, "health").put("max", 5000.0);
        attributeOf(values, "abilityCooldown").put("max", 0.25);

        assertThatCode(
                        () -> {
                            StatConfig config = load(values);
                            assertThat(config.definition(Attribute.HEALTH).max()).isEqualTo(5000.0);
                            assertThat(config.definition(Attribute.ABILITY_COOLDOWN).max())
                                    .isEqualTo(0.25);
                        })
                .doesNotThrowAnyException();
    }
}
