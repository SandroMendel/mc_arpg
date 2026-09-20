package rpg.core.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class SchemaValidatorSnapshotTest {

    private static final Path SOURCE = Path.of("content.yml");

    @Test
    void validatedCollectionsAreRecursiveSnapshotsAndKeepNestedNulls() throws Exception {
        List<Object> parsedList = new ArrayList<>();
        parsedList.add(null);
        parsedList.add("before");
        Map<String, Object> parsedMap = new LinkedHashMap<>();
        parsedMap.put("list", parsedList);
        parsedMap.put("decimal", new BigDecimal("1.25"));
        parsedMap.put("nullable", null);
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("values", parsedMap);

        ValidatedConfigView view =
                SchemaValidator.validateTyped(SOURCE, document, schema(FieldType.MAP));
        Map<?, ?> exposedMap = view.getMap("values");
        List<?> exposedList = (List<?>) exposedMap.get("list");

        parsedMap.put("added", Boolean.TRUE);
        parsedList.set(1, "after");
        parsedList.add("outside");

        assertThat(exposedMap.get("decimal")).isEqualTo(new BigDecimal("1.25"));
        assertThat(exposedMap.containsKey("nullable")).isTrue();
        assertThat(exposedMap.get("nullable")).isNull();
        assertThat(exposedMap.containsKey("added")).isFalse();
        assertThat(exposedList).hasSize(2);
        assertThat(exposedList.get(0)).isNull();
        assertThat(exposedList.get(1)).isEqualTo("before");
        assertThatThrownBy(() -> ((Map<Object, Object>) exposedMap).put("new", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> ((List<Object>) exposedList).add("new"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void cyclicParsedCollectionsFailInsteadOfRecursingForever() {
        List<Object> cyclic = new ArrayList<>();
        cyclic.add(cyclic);
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("values", cyclic);

        assertThatThrownBy(
                        () -> SchemaValidator.validateTyped(SOURCE, document, schema(FieldType.LIST)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cyclic collection");
    }

    @Test
    void nonFiniteDoubleValuesAreRejectedWithPathAndActualValue() {
        for (double value :
                new double[] {
                    Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY
                }) {
            assertThatThrownBy(
                            () ->
                                    SchemaValidator.validateTyped(
                                            SOURCE,
                                            Map.of("value", value),
                                            ConfigSchema.<Void>builder(1)
                                                    .required("value", FieldType.DOUBLE)
                                                    .boundTo(ignored -> null)
                                                    .build()))
                    .isInstanceOf(ConfigValidationException.class)
                    .satisfies(
                            thrown -> {
                                ConfigValidationException failure =
                                        (ConfigValidationException) thrown;
                                assertThat(failure.documentPath()).isEqualTo("value");
                                assertThat(failure.actual()).isEqualTo(String.valueOf(value));
                            });
        }
    }

    @Test
    void presentButMalformedIntermediateMapIsNotTreatedAsMissingOptionalField() {
        ConfigSchema<Void> schema =
                ConfigSchema.<Void>builder(1)
                        .field(FieldDefinition.optional("settings.limit", FieldType.INTEGER))
                        .boundTo(ignored -> null)
                        .build();

        assertThatThrownBy(
                        () ->
                                SchemaValidator.validateTyped(
                                        SOURCE, Map.of("settings", "falsch"), schema))
                .isInstanceOf(ConfigValidationException.class)
                .satisfies(
                        thrown -> {
                            ConfigValidationException failure = (ConfigValidationException) thrown;
                            assertThat(failure.documentPath()).isEqualTo("settings");
                            assertThat(failure.expected()).contains("mapping");
                            assertThat(failure.actual()).isEqualTo("'falsch'");
                        });
    }

    @Test
    void validatedViewBoundaryIsSealedToTheCoreImplementation() {
        assertThat(ValidatedConfigView.class.isSealed()).isTrue();
        assertThat(ValidatedConfigView.class.getPermittedSubclasses())
                .containsExactly(MapConfigView.class);
    }

    private static ConfigSchema<Void> schema(FieldType type) {
        return ConfigSchema.<Void>builder(1)
                .required("values", type)
                .boundTo(ignored -> null)
                .build();
    }
}
