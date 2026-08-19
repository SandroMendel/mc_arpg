package rpg.core.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * T016 / FR-002: an invalid configuration must be rejected fail-fast, and the error must name the
 * file, the path inside the document and the expected value.
 */
class ConfigLoaderValidationTest {

    private static final Path SOURCE = Path.of("config", "combat.yml");

    /** Typed configuration object produced from a validated document. */
    record CombatConfig(String damageFormula, int maxTargets, double critMultiplier, boolean pvp) {}

    private static ConfigSchema<CombatConfig> schema() {
        return ConfigSchema.<CombatConfig>builder(1)
                .required("combat.damage-formula", FieldType.STRING)
                .required("combat.max-targets", FieldType.INTEGER)
                .field(
                        FieldDefinition.optional("combat.crit-multiplier", FieldType.DOUBLE, 1.5d)
                                .withRange(1.0d, 5.0d))
                .optional("combat.pvp", FieldType.BOOLEAN, Boolean.FALSE)
                .boundTo(
                        view ->
                                new CombatConfig(
                                        view.getString("combat.damage-formula"),
                                        view.getInt("combat.max-targets"),
                                        view.getDouble("combat.crit-multiplier"),
                                        view.getBoolean("combat.pvp")))
                .build();
    }

    private static Map<String, Object> validDocument() {
        Map<String, Object> combat = new LinkedHashMap<>();
        combat.put("damage-formula", "atk * 1.2");
        combat.put("max-targets", 5);
        combat.put("crit-multiplier", 2.0d);
        combat.put("pvp", Boolean.TRUE);
        return new LinkedHashMap<>(Map.of("combat", combat));
    }

    @Test
    void aValidDocumentIsAccepted() throws Exception {
        InMemoryConfigLoader loader = new InMemoryConfigLoader();
        loader.put(SOURCE, validDocument());

        CombatConfig config = loader.loadAndValidate(SOURCE, schema());

        assertThat(config)
                .isEqualTo(new CombatConfig("atk * 1.2", 5, 2.0d, true));
    }

    @Test
    void aMissingRequiredFieldNamesFilePathAndExpectedValue() {
        Map<String, Object> document = validDocument();
        @SuppressWarnings("unchecked")
        Map<String, Object> combat = (Map<String, Object>) document.get("combat");
        combat.remove("max-targets");

        InMemoryConfigLoader loader = new InMemoryConfigLoader();
        loader.put(SOURCE, document);

        ConfigValidationException thrown =
                catchThrowableOfType(
                        ConfigValidationException.class,
                        () -> loader.loadAndValidate(SOURCE, schema()));

        assertThat(thrown).isNotNull();
        assertThat(thrown.sourceFile()).isEqualTo(SOURCE);
        assertThat(thrown.documentPath()).isEqualTo("combat.max-targets");
        assertThat(thrown.expected()).contains("integer").contains("required");
        assertThat(thrown.actual()).contains("missing");
        // the message alone must be enough for an operator to fix the file
        assertThat(thrown)
                .hasMessageContaining("combat.yml")
                .hasMessageContaining("combat.max-targets")
                .hasMessageContaining("integer");
    }

    @Test
    void aRequiredFieldIsNeverSilentlyDefaulted() {
        Map<String, Object> document = validDocument();
        @SuppressWarnings("unchecked")
        Map<String, Object> combat = (Map<String, Object>) document.get("combat");
        combat.remove("damage-formula");

        InMemoryConfigLoader loader = new InMemoryConfigLoader();
        loader.put(SOURCE, document);

        assertThat(
                        catchThrowableOfType(
                                ConfigValidationException.class,
                                () -> loader.loadAndValidate(SOURCE, schema())))
                .isNotNull();
    }

    @Test
    void aWronglyTypedValueIsRejected() {
        Map<String, Object> document = validDocument();
        @SuppressWarnings("unchecked")
        Map<String, Object> combat = (Map<String, Object>) document.get("combat");
        combat.put("max-targets", "five");

        InMemoryConfigLoader loader = new InMemoryConfigLoader();
        loader.put(SOURCE, document);

        ConfigValidationException thrown =
                catchThrowableOfType(
                        ConfigValidationException.class,
                        () -> loader.loadAndValidate(SOURCE, schema()));

        assertThat(thrown.documentPath()).isEqualTo("combat.max-targets");
        assertThat(thrown.expected()).contains("integer");
        assertThat(thrown.actual()).contains("five");
    }

    @Test
    void aValueOutsideItsDeclaredRangeIsRejected() {
        Map<String, Object> document = validDocument();
        @SuppressWarnings("unchecked")
        Map<String, Object> combat = (Map<String, Object>) document.get("combat");
        combat.put("crit-multiplier", 9.0d);

        InMemoryConfigLoader loader = new InMemoryConfigLoader();
        loader.put(SOURCE, document);

        ConfigValidationException thrown =
                catchThrowableOfType(
                        ConfigValidationException.class,
                        () -> loader.loadAndValidate(SOURCE, schema()));

        assertThat(thrown.documentPath()).isEqualTo("combat.crit-multiplier");
        assertThat(thrown.expected()).contains("1.0").contains("5.0");
        assertThat(thrown.actual()).contains("9.0");
    }

    @Test
    void anAbsentOptionalFieldFallsBackToItsDeclaredDefault() throws Exception {
        Map<String, Object> document = validDocument();
        @SuppressWarnings("unchecked")
        Map<String, Object> combat = (Map<String, Object>) document.get("combat");
        combat.remove("crit-multiplier");
        combat.remove("pvp");

        InMemoryConfigLoader loader = new InMemoryConfigLoader();
        loader.put(SOURCE, document);

        CombatConfig config = loader.loadAndValidate(SOURCE, schema());

        assertThat(config.critMultiplier()).isEqualTo(1.5d);
        assertThat(config.pvp()).isFalse();
    }

    @Test
    void anIntegerLiteralSatisfiesADoubleField() {
        Map<String, Object> document = validDocument();
        @SuppressWarnings("unchecked")
        Map<String, Object> combat = (Map<String, Object>) document.get("combat");
        // YAML writes `2` for a whole number even where the schema wants a decimal
        combat.put("crit-multiplier", 2);

        InMemoryConfigLoader loader = new InMemoryConfigLoader();
        loader.put(SOURCE, document);

        assertThatCode(() -> loader.loadAndValidate(SOURCE, schema())).doesNotThrowAnyException();
    }

    @Test
    void theViewCarriesTheSchemaVersion() throws Exception {
        InMemoryConfigLoader loader = new InMemoryConfigLoader();
        loader.put(SOURCE, validDocument());

        ConfigSchema<Integer> versionProbe =
                ConfigSchema.<Integer>builder(7)
                        .required("combat.max-targets", FieldType.INTEGER)
                        .boundTo(ConfigView::schemaVersion)
                        .build();

        assertThat(loader.loadAndValidate(SOURCE, versionProbe)).isEqualTo(7);
    }
}
