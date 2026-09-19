package rpg.core.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Creates a defensive snapshot of parser values without imposing schema semantics.
 *
 * <p>Parsed YAML collections may contain {@code null} values, so this utility deliberately uses
 * unmodifiable wrappers instead of {@link List#copyOf(List)} or {@link Map#copyOf(Map)}. Scalar
 * values are retained as-is. T009 intentionally defines no policy for unsupported mutable scalar
 * objects; rejecting, normalizing, or deep-copying such values belongs to later field validation.
 */
final class ConfigValueSnapshot {

    private ConfigValueSnapshot() {}

    static Object copy(Object value) {
        return copy(value, new IdentityHashMap<>());
    }

    private static Object copy(Object value, IdentityHashMap<Object, Boolean> visiting) {
        if (value instanceof List<?> list) {
            enter(value, visiting);
            try {
                List<Object> snapshot = new ArrayList<>(list.size());
                for (Object element : list) {
                    snapshot.add(copy(element, visiting));
                }
                return Collections.unmodifiableList(snapshot);
            } finally {
                visiting.remove(value);
            }
        }

        if (value instanceof Map<?, ?> map) {
            enter(value, visiting);
            try {
                Map<Object, Object> snapshot = new LinkedHashMap<>(map.size());
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    Object key = copy(entry.getKey(), visiting);
                    snapshot.put(key, copy(entry.getValue(), visiting));
                }
                return Collections.unmodifiableMap(snapshot);
            } finally {
                visiting.remove(value);
            }
        }

        // SafeConstructor can produce scalar types beyond the schema's eventual field type. Keep
        // them intact here; FieldType/SchemaValidator remains responsible for compatibility.
        return value;
    }

    private static void enter(Object value, IdentityHashMap<Object, Boolean> visiting) {
        if (visiting.put(value, Boolean.TRUE) != null) {
            throw new IllegalArgumentException("configuration value contains a cyclic collection");
        }
    }
}
