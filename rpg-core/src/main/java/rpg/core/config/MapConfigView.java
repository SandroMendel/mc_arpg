package rpg.core.config;

import java.util.List;
import java.util.Map;

/**
 * {@link ConfigView} over an already validated, flattened document.
 *
 * <p>Constructed only by {@link SchemaValidator}, so every declared path is guaranteed to be present
 * with the declared type. The accessors therefore never throw a checked exception.
 */
final class MapConfigView implements ConfigView {

    private final int schemaVersion;
    private final Map<String, Object> values;

    MapConfigView(int schemaVersion, Map<String, Object> values) {
        this.schemaVersion = schemaVersion;
        this.values = Map.copyOf(values);
    }

    @Override
    public int schemaVersion() {
        return schemaVersion;
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
