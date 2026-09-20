package rpg.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import rpg.content.ability.AbilityContentSchema;
import rpg.core.config.ConfigFieldContract;
import rpg.core.config.ConfigValidationException;
import rpg.core.config.FieldType;
import rpg.platform.config.YamlConfigLoader;

/**
 * T028: file-backed, server-free fixtures for the B16 document-envelope contract.
 *
 * <p>The generic in-memory contract cases remain in {@link ContentDocumentSchemaTest}. This class
 * proves the same operator-facing error metadata against YAML fixtures and the canonical
 * {@code abilities.yml} paths. Duplicate keys are a parser error, so the expected path is the
 * parser boundary {@code <document>} rather than an invented dotted path.
 */
class B16SchemaFixtureTest {

    private static final Path FIXTURE_ROOT = Path.of("src/test/resources/b16-schema-fixtures");

    @Test
    void validDocumentFixtureBindsThroughTheCanonicalAbilitySchema() throws Exception {
        Path source = source("abilities-valid.yml");

        assertThat(AbilityContentSchema.schema().validate(source, read("abilities-valid.yml")))
                .isNotNull();
    }

    @Test
    void missingRequiredFixtureChecksFilePathYamlPathExpectationAndActualValue() {
        Path source = source("abilities-missing-required.yml");
        ConfigValidationException thrown =
                catchThrowableOfType(
                        ConfigValidationException.class,
                        () ->
                                AbilityContentSchema.schema()
                                        .validate(source, read("abilities-missing-required.yml")));

        assertThat(thrown.sourceFile()).isEqualTo(source);
        assertThat(thrown.documentPath()).isEqualTo("runtime.global-cooldown-ms");
        assertThat(thrown.expected())
                .contains("a long")
                .contains("milliseconds")
                .contains("required");
        assertThat(thrown.actual()).isEqualTo("missing");
    }

    @Test
    void wrongTypeFixtureChecksFilePathYamlPathExpectationAndActualValue() {
        Path source = source("abilities-wrong-type.yml");
        ConfigValidationException thrown =
                catchThrowableOfType(
                        ConfigValidationException.class,
                        () ->
                                AbilityContentSchema.schema()
                                        .validate(source, read("abilities-wrong-type.yml")));

        assertThat(thrown.sourceFile()).isEqualTo(source);
        assertThat(thrown.documentPath()).isEqualTo("runtime.global-cooldown-ms");
        assertThat(thrown.expected()).contains("a long").contains("milliseconds");
        assertThat(thrown.actual()).isEqualTo("'slow'");
    }

    @Test
    void rangeFixtureChecksFilePathYamlPathExpectationAndActualValue() {
        Path source = source("abilities-range-error.yml");
        ConfigValidationException thrown =
                catchThrowableOfType(
                        ConfigValidationException.class,
                        () -> rangeAwareSchema().validate(source, read("abilities-range-error.yml")));

        assertThat(thrown.sourceFile()).isEqualTo(source);
        assertThat(thrown.documentPath()).isEqualTo("runtime.global-cooldown-ms");
        assertThat(thrown.expected())
                .contains("range [1.0, 10000.0]")
                .contains("milliseconds");
        assertThat(thrown.actual()).isEqualTo("0");
    }

    @Test
    void unknownFixedKeyFixtureChecksFilePathYamlPathExpectationAndActualValue() {
        Path source = source("abilities-unknown-fixed-key.yml");
        ConfigValidationException thrown =
                catchThrowableOfType(
                        ConfigValidationException.class,
                        () ->
                                AbilityContentSchema.schema()
                                        .validate(source, read("abilities-unknown-fixed-key.yml")));

        assertThat(thrown.sourceFile()).isEqualTo(source);
        assertThat(thrown.documentPath()).isEqualTo("rogue");
        assertThat(thrown.expected()).contains("schemaVersion").contains("runtime").contains("abilities");
        assertThat(thrown.actual()).isEqualTo("unknown fixed root key 'rogue'");
    }

    @Test
    void duplicateKeyFixtureChecksTheCanonicalYamlLoaderErrorMetadata() {
        Path source = source("abilities-duplicate-key.yml");
        ConfigValidationException thrown =
                catchThrowableOfType(
                        ConfigValidationException.class,
                        () -> new YamlConfigLoader(Path.of(".")).readDocument(source));

        assertThat(thrown).isNotNull();
        assertThat(thrown.sourceFile()).isEqualTo(source);
        assertThat(thrown.documentPath()).isEqualTo("<document>");
        assertThat(thrown.expected()).isEqualTo("well-formed YAML");
        assertThat(thrown.actual()).contains("global-cooldown-ms");
    }

    @Test
    void unknownSchemaVersionFixtureChecksFilePathYamlPathExpectationAndActualValue() {
        Path source = source("abilities-unknown-schema-version.yml");
        ConfigValidationException thrown =
                catchThrowableOfType(
                        ConfigValidationException.class,
                        () ->
                                AbilityContentSchema.schema()
                                        .validate(source, read("abilities-unknown-schema-version.yml")));

        assertThat(thrown.sourceFile()).isEqualTo(source);
        assertThat(thrown.documentPath()).isEqualTo("schemaVersion");
        assertThat(thrown.expected()).contains("exact integer schemaVersion 1");
        assertThat(thrown.actual()).contains("2");
    }

    private static ContentDocumentSchema<Integer> rangeAwareSchema() {
        ContentDocumentContract document =
                new ContentDocumentContract(
                        "abilities.yml",
                        "abilities",
                        List.of("schemaVersion"),
                        List.of("runtime", "abilities"),
                        List.of("runtime.global-cooldown-ms", "abilities"),
                        List.of());
        return ContentDocumentSchema.<Integer>builder(
                        document, 1, view -> (int) view.getLong("runtime.global-cooldown-ms"))
                .field(
                        ConfigFieldContract.builder(
                                        "runtime.global-cooldown-ms", FieldType.LONG)
                                .required()
                                .range(1, 10_000)
                                .unit("milliseconds")
                                .build())
                .build();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> read(String filename) {
        try (InputStream input = fixture(filename)) {
            Object parsed = new Yaml(new SafeConstructor(new LoaderOptions())).load(input);
            return (Map<String, Object>) Objects.requireNonNull(parsed, "fixture root");
        } catch (IOException exception) {
            throw new AssertionError("could not read fixture " + filename, exception);
        }
    }

    private static InputStream fixture(String filename) {
        return Objects.requireNonNull(
                B16SchemaFixtureTest.class.getResourceAsStream("/b16-schema-fixtures/" + filename),
                () -> "missing fixture " + filename);
    }

    private static Path source(String filename) {
        return FIXTURE_ROOT.resolve(filename);
    }
}
