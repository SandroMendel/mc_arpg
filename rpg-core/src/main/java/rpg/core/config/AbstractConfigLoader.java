package rpg.core.config;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Everything a {@link ConfigLoader} does apart from turning bytes into a document: schema
 * validation, registration of sources and the atomic global reload with rollback.
 *
 * <p>Sits in {@code rpg-core} so FR-002/FR-003/FR-004 are covered by server-free unit tests
 * (Constitution VII.1). A concrete loader only supplies {@link #parse(Path)} - the YAML
 * implementation in {@code rpg-platform} adds SnakeYAML and nothing else.
 */
public abstract class AbstractConfigLoader implements ConfigLoader {

    private final List<RegisteredSource<?>> registered = new CopyOnWriteArrayList<>();

    /**
     * Reads {@code source} and returns it as a nested map.
     *
     * @throws ConfigValidationException if the source cannot be read or parsed at all
     */
    protected abstract Map<String, Object> parse(Path source) throws ConfigValidationException;

    @Override
    public final <T> T loadAndValidate(Path source, ConfigSchema<T> schema)
            throws ConfigValidationException {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(schema, "schema");
        return schema.bind(SchemaValidator.validate(source, parse(source), schema));
    }

    @Override
    public final <T> ConfigHandle<T> register(Path source, ConfigSchema<T> schema)
            throws ConfigValidationException {
        T initial = loadAndValidate(source, schema);
        RegisteredSource<T> entry = new RegisteredSource<>(source, schema, initial);
        registered.add(entry);
        return entry;
    }

    @Override
    public final void reloadAll() throws ConfigValidationException {
        // Two phases on purpose: validate everything first, publish only when all of it passed.
        // A half-applied reload would leave modules with a mixed old/new view, which FR-004 forbids.
        Map<RegisteredSource<?>, Object> staged = new LinkedHashMap<>();
        for (RegisteredSource<?> entry : registered) {
            staged.put(entry, entry.reloadValue(this));
        }
        staged.forEach((entry, value) -> entry.publish(value));
    }

    /** The sources currently taking part in {@link #reloadAll()}. */
    public final List<Path> registeredSources() {
        List<Path> paths = new ArrayList<>(registered.size());
        for (RegisteredSource<?> entry : registered) {
            paths.add(entry.source());
        }
        return List.copyOf(paths);
    }

    /** One registered source plus the currently valid value loaded from it. */
    private static final class RegisteredSource<T> implements ConfigHandle<T> {

        private final Path source;
        private final ConfigSchema<T> schema;
        private final AtomicReference<T> current;

        RegisteredSource(Path source, ConfigSchema<T> schema, T initial) {
            this.source = source;
            this.schema = schema;
            this.current = new AtomicReference<>(initial);
        }

        @Override
        public T get() {
            return current.get();
        }

        @Override
        public Path source() {
            return source;
        }

        /** Loads and validates the new value without publishing it yet. */
        T reloadValue(AbstractConfigLoader loader) throws ConfigValidationException {
            return loader.loadAndValidate(source, schema);
        }

        @SuppressWarnings("unchecked") // the staged value came from this entry's own schema
        void publish(Object value) {
            current.set((T) value);
        }
    }
}
