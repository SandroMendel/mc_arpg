package rpg.core.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link ConfigView} over an already validated, flattened document.
 *
 * <p>Constructed only by {@link SchemaValidator}. Required paths and optional paths with defaults
 * are present with their declared type; optional paths without defaults may be absent. The
 * accessors therefore never throw a checked exception.
 */
final class MapConfigView implements ValidatedConfigView {

    private final ConfigSchema<?> validatingSchema;
    private final Map<String, Object> values;

    MapConfigView(ConfigSchema<?> validatingSchema, Map<String, Object> values) {
        this.validatingSchema = validatingSchema;
        Map<String, Object> snapshot = new LinkedHashMap<>(values.size());
        values.forEach((path, value) -> snapshot.put(path, ConfigValueSnapshot.copy(value)));
        this.values = Collections.unmodifiableMap(snapshot);
    }

    @Override
    public ConfigSchema<?> validatingSchema() {
        return validatingSchema;
    }

    @Override
    public int schemaVersion() {
        return validatingSchema.schemaVersion();
    }

    @Override
    public String getString(String path) {
        return get(path, String.class);
    }

    @Override
    public boolean getBoolean(String path) {
        return get(path, Boolean.class);
    }

    @Override
    public int getInt(String path) {
        return get(path, Integer.class);
    }

    @Override
    public long getLong(String path) {
        return get(path, Long.class);
    }

    @Override
    public double getDouble(String path) {
        return get(path, Double.class);
    }

    @Override
    public List<?> getList(String path) {
        return get(path, List.class);
    }

    @Override
    public Map<?, ?> getMap(String path) {
        return get(path, Map.class);
    }

    private <T> T get(String path, Class<T> type) {
        Object value = values.get(path);
        if (value == null) {
            throw new IllegalArgumentException(
                    "'" + path + "' is not declared by this schema - add a FieldDefinition for it");
        }
        if (!type.isInstance(value)) {
            throw new IllegalStateException(
                    "'"
                            + path
                            + "' was validated as "
                            + value.getClass().getSimpleName()
                            + ", but read as "
                            + type.getSimpleName());
        }
        return type.cast(value);
    }
}
