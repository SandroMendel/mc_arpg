package rpg.core.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ConfigFieldContractTest {

    @Test
    @SuppressWarnings("unchecked")
    void nestedCollectionDefaultsAreFrozenThroughFieldDefinition() {
        List<Object> nestedList = new ArrayList<>(List.of("original"));
        Map<String, Object> nestedMap = new LinkedHashMap<>();
        nestedMap.put("list", nestedList);
        Map<String, Object> mutableDefault = new LinkedHashMap<>();
        mutableDefault.put("nested", nestedMap);

        ConfigFieldContract contract =
                ConfigFieldContract.optional("values", FieldType.MAP, mutableDefault);
        Map<?, ?> exposed = (Map<?, ?>) contract.toFieldDefinition().defaultValue().orElseThrow();
        Map<?, ?> exposedNested = (Map<?, ?>) exposed.get("nested");
        List<?> exposedList = (List<?>) exposedNested.get("list");

        mutableDefault.put("added", Boolean.TRUE);
        nestedMap.put("changed", Boolean.TRUE);
        nestedList.add("changed");

        assertThat(exposed.containsKey("added")).isFalse();
        assertThat(exposedNested.containsKey("changed")).isFalse();
        assertThat(exposedList).hasSize(1);
        assertThat(exposedList.get(0)).isEqualTo("original");
        assertThatThrownBy(() -> ((Map<Object, Object>) exposed).put("new", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> ((Map<Object, Object>) exposedNested).put("new", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> ((List<Object>) exposedList).add("new"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void nullAndUnsupportedNestedDefaultValuesFailClearly() {
        List<Object> withNull = new ArrayList<>();
        withNull.add(null);
        assertThatThrownBy(
                        () ->
                                ConfigFieldContract.optional(
                                        "values", FieldType.LIST, withNull))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not contain null");

        assertThatThrownBy(
                        () ->
                                ConfigFieldContract.optional(
                                        "values", FieldType.LIST, List.of(new Object())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported value type");
    }
}
