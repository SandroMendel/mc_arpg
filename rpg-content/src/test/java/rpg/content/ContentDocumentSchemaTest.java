package rpg.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import rpg.core.config.ConfigFieldContract;
import rpg.core.config.ConfigSchema;
import rpg.core.config.ConfigValidationException;
import rpg.core.config.FieldType;
import rpg.core.config.SchemaValidator;
import rpg.core.config.ValidatedConfigView;

class ContentDocumentSchemaTest {

    @Test
    void missingRequiredFieldReportsExactPathAndMetadata() {
        ConfigValidationException thrown =
                catchValidation(
                        Map.of("schemaVersion", 1, "settings", Map.of()));

        assertThat(thrown.getMessage())
                .contains("example.yml")
                .contains("settings.limit")
                .contains("integer")
                .contains("range [1.0, 10.0]")
                .contains("unit: seconds")
                .contains("missing");
        assertThat(thrown.sourceFile()).isEqualTo(Path.of("example.yml"));
        assertThat(thrown.documentPath()).isEqualTo("settings.limit");
        assertThat(thrown.expected()).contains("integer", "seconds");
        assertThat(thrown.actual()).isEqualTo("missing");
    }

    @Test
    void wrongTypeReportsExpectedTypeAndActualValue() {
        ConfigValidationException thrown =
                catchValidation(
                        Map.of(
                                "schemaVersion", 1,
                                "settings", Map.of("limit", "ten")));

        assertThat(thrown.documentPath()).isEqualTo("settings.limit");
        assertThat(thrown.expected()).contains("integer", "unit: seconds");
        assertThat(thrown.actual()).isEqualTo("'ten'");
        assertThat(thrown.getMessage()).contains("settings.limit").contains("'ten'");
    }

    @Test
    void rangeErrorReportsDeclaredRangeAndActualValue() {
        ConfigValidationException thrown =
                catchValidation(
                        Map.of("schemaVersion", 1, "settings", Map.of("limit", 11)));

        assertThat(thrown.documentPath()).isEqualTo("settings.limit");
        assertThat(thrown.expected()).contains("range [1.0, 10.0]").contains("seconds");
        assertThat(thrown.actual()).isEqualTo("11");
    }

    @Test
    void unknownFixedRootKeyReportsItsExactPath() {
        ConfigValidationException thrown =
                catchValidation(
                        Map.of(
                                "schemaVersion", 1,
                                "settings", Map.of("limit", 5),
                                "rogue", Map.of()));

        assertThat(thrown.documentPath()).isEqualTo("rogue");
        assertThat(thrown.expected()).contains("settings").contains("schemaVersion");
        assertThat(thrown.actual()).contains("unknown fixed root key");
    }

    @Test
    void unknownSchemaVersionRequiresTheExactIntegerVersion() {
        ConfigValidationException thrown =
                catchValidation(
                        Map.of("schemaVersion", 2, "settings", Map.of("limit", 5)));

        assertThat(thrown.documentPath()).isEqualTo("schemaVersion");
        assertThat(thrown.expected()).contains("exact integer schemaVersion 1");
        assertThat(thrown.actual()).contains("2");
    }

    @Test
    void unknownNestedFixedKeyReportsExactPathAndAllowedKeys() {
        ConfigValidationException thrown =
                catchValidation(
                        Map.of(
                                "schemaVersion", 1,
                                "settings", Map.of("limt", 5)));

        assertThat(thrown.documentPath()).isEqualTo("settings.limt");
        assertThat(thrown.expected()).contains("fixed child keys").contains("limit");
        assertThat(thrown.actual()).contains("limt");
        assertThat(thrown.sourceFile()).isEqualTo(Path.of("example.yml"));
    }

    @Test
    void fixedPathDeclarationsAlsoRejectUnknownChildrenWithoutScalarField() {
        ContentDocumentContract document =
                new ContentDocumentContract(
                        "structured.yml",
                        "structured",
                        List.of("schemaVersion"),
                        List.of("settings"),
                        List.of("settings.limit", "settings.mode"),
                        List.of());
        ContentDocumentSchema<Void> schema =
                ContentDocumentSchema.<Void>builder(document, 1, ignored -> null).build();

        ConfigValidationException thrown =
                catchThrowableOfType(
                        ConfigValidationException.class,
                        () ->
                                schema.validate(
                                        Path.of("structured.yml"),
                                        Map.of("schemaVersion", 1, "settings", Map.of("limt", 5))));

        assertThat(thrown.documentPath()).isEqualTo("settings.limt");
        assertThat(thrown.expected()).contains("limit").contains("mode");
        assertThat(thrown.actual()).contains("limt");
    }

    @Test
    void validateAndBindUsesTheSameContentSchemaSeam() throws ConfigValidationException {
        assertThat(
                        validationSchema()
                                .validateAndBind(
                                        Path.of("example.yml"),
                                        Map.of(
                                                "schemaVersion", 1,
                                                "settings", Map.of("limit", 5))))
                .isEqualTo(5);
    }

    @Test
    void bindRejectsAViewWithAnotherSchemaVersionBeforeDelegating()
            throws ConfigValidationException {
        ContentDocumentSchema<String> schema = schema();

        assertThatThrownBy(
                        () -> schema.bind(view(ConfigSchema.<Void>builder(1).boundTo(ignored -> null).build())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("config view schema version 1")
                .hasMessageContaining("content schema version 2");
    }

    @Test
    void bindDelegatesForMatchingVersion() throws ConfigValidationException {
        ContentDocumentSchema<String> schema = schema();
        assertThat(schema.bind(view(schema.configSchema()))).isEqualTo("bound");
    }

    @Test
    void bindRejectsSameVersionViewFromAnotherSchemaInstance() throws ConfigValidationException {
        ContentDocumentSchema<String> schema = schema();

        ConfigSchema<Void> foreignSchema =
                ConfigSchema.<Void>builder(2).boundTo(ignored -> null).build();

        assertThatThrownBy(() -> schema.bind(view(foreignSchema)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("different ConfigSchema instance");
    }

    private static ContentDocumentSchema<String> schema() {
        ContentDocumentContract document =
                new ContentDocumentContract(
                        "example.yml",
                        "example",
                        List.of("schemaVersion"),
                        List.of(),
                        List.of(),
                        List.of());
        return ContentDocumentSchema.builder(document, 2, ignored -> "bound").build();
    }

    private static ContentDocumentSchema<Integer> validationSchema() {
        ContentDocumentContract document =
                new ContentDocumentContract(
                        "example.yml",
                        "example",
                        List.of("schemaVersion"),
                        List.of("settings"),
                        List.of("settings.limit"),
                        List.of());
        return ContentDocumentSchema.<Integer>builder(document, 1, view -> view.getInt("settings.limit"))
                .field(
                        ConfigFieldContract.builder("settings.limit", FieldType.INTEGER)
                                .required()
                                .range(1, 10)
                                .unit("seconds")
                                .build())
                .build();
    }

    private static ConfigValidationException catchValidation(Map<String, Object> document) {
        return catchThrowableOfType(
                ConfigValidationException.class,
                () -> validationSchema().validate(Path.of("example.yml"), document));
    }

    private static ValidatedConfigView view(ConfigSchema<?> sourceSchema)
            throws ConfigValidationException {
        return SchemaValidator.validateTyped(Path.of("example.yml"), Map.of(), sourceSchema);
    }
}
