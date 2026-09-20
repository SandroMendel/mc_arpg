package rpg.core.config;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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

    private final List<RegisteredEntry> registered = new CopyOnWriteArrayList<>();

    /** One immutable generation pointer makes every handle observe the same committed reload. */
    private final AtomicReference<Map<RegisteredEntry, Object>> active =
            new AtomicReference<>(Map.of());

    private final Object reloadLock = new Object();

    /** The hook thread sees staged values without exposing them to other readers prematurely. */
    private final ThreadLocal<Map<RegisteredEntry, Object>> stagedForHook = new ThreadLocal<>();

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
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(schema, "schema");
        synchronized (reloadLock) {
            T initial = requireLoaded(loadAndValidate(source, schema));
            RegisteredSource<T> entry = new RegisteredSource<>(source, schema);
            registered.add(entry);
            installInitial(entry, initial);
            return entry;
        }
    }

    @Override
    public final <T> ConfigHandle<T> registerBatch(
            List<Path> sources, ConfigLoader.BatchLoader<T> loader)
            throws ConfigValidationException {
        List<Path> sourceCopy = copySources(sources);
        Objects.requireNonNull(loader, "loader");
        synchronized (reloadLock) {
            T initial = requireLoaded(loader.load());
            RegisteredBatch<T> entry = new RegisteredBatch<>(sourceCopy, loader);
            registered.add(entry);
            installInitial(entry, initial);
            return entry;
        }
    }

    @Override
    public final void reloadAll() throws ConfigValidationException {
        reloadAll(() -> {}, () -> {});
    }

    /**
     * Stages one generation, lets the caller apply derived state against that generation, and only
     * publishes after the hook returns successfully.
     */
    @Override
    public final void reloadAll(Runnable afterStaging, Runnable onRollback)
            throws ConfigValidationException {
        Objects.requireNonNull(afterStaging, "afterStaging");
        Objects.requireNonNull(onRollback, "onRollback");
        synchronized (reloadLock) {
            // Two phases on purpose: validate everything first, then let hooks observe the staged
            // generation, and only then swap one immutable generation pointer. A hook failure thus
            // leaves the old handles and snapshots published while the compensating hook can restore
            // derived state against those old values.
            Map<RegisteredEntry, Object> staged = stageValues();
            stagedForHook.set(staged);
            try {
                afterStaging.run();
                active.set(staged);
            } catch (RuntimeException | Error failure) {
                stagedForHook.remove();
                try {
                    onRollback.run();
                } catch (RuntimeException | Error rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
                throw failure;
            } finally {
                stagedForHook.remove();
            }
        }
    }

    private Map<RegisteredEntry, Object> stageValues() throws ConfigValidationException {
        Map<RegisteredEntry, Object> staged = new LinkedHashMap<>();
        for (RegisteredEntry entry : registered) {
            staged.put(entry, requireLoaded(entry.reloadValue(this)));
        }
        return Map.copyOf(staged);
    }

    /** The sources currently taking part in {@link #reloadAll()}. */
    public final List<Path> registeredSources() {
        List<Path> paths = new ArrayList<>();
        for (RegisteredEntry entry : registered) {
            paths.addAll(entry.sources());
        }
        return List.copyOf(paths);
    }

    private void installInitial(RegisteredEntry entry, Object value) {
        Map<RegisteredEntry, Object> next = new LinkedHashMap<>(active.get());
        next.put(entry, value);
        active.set(Map.copyOf(next));
    }

    @SuppressWarnings("unchecked")
    private <T> T current(RegisteredEntry entry) {
        Map<RegisteredEntry, Object> generation = stagedForHook.get();
        Object value = (generation == null ? active.get() : generation).get(entry);
        if (value == null) {
            throw new IllegalStateException("configuration handle has no active value");
        }
        return (T) value;
    }

    private static <T> T requireLoaded(T value) {
        return Objects.requireNonNull(value, "loaded configuration");
    }

    private static List<Path> copySources(List<Path> sources) {
        Objects.requireNonNull(sources, "sources");
        if (sources.isEmpty()) {
            throw new IllegalArgumentException("batch sources must not be empty");
        }
        List<Path> copy = new ArrayList<>(sources.size());
        Set<Path> distinct = new LinkedHashSet<>();
        for (Path source : sources) {
            Objects.requireNonNull(source, "source");
            if (!distinct.add(source)) {
                throw new IllegalArgumentException("duplicate batch source: " + source);
            }
            copy.add(source);
        }
        return List.copyOf(copy);
    }

    private interface RegisteredEntry {

        List<Path> sources();

        Object reloadValue(AbstractConfigLoader loader) throws ConfigValidationException;
    }

    /** One registered source plus the currently valid value loaded from it. */
    private final class RegisteredSource<T> implements ConfigHandle<T>, RegisteredEntry {

        private final Path source;
        private final ConfigSchema<T> schema;

        RegisteredSource(Path source, ConfigSchema<T> schema) {
            this.source = source;
            this.schema = schema;
        }

        @Override
        public T get() {
            return current(this);
        }

        @Override
        public Path source() {
            return source;
        }

        @Override
        public List<Path> sources() {
            return List.of(source);
        }

        /** Loads and validates the new value without publishing it yet. */
        @Override
        public Object reloadValue(AbstractConfigLoader loader) throws ConfigValidationException {
            return loader.loadAndValidate(source, schema);
        }
    }

    /** One multi-source registration plus the currently valid complete value. */
    private final class RegisteredBatch<T> implements ConfigHandle<T>, RegisteredEntry {

        private final List<Path> sources;
        private final ConfigLoader.BatchLoader<T> loader;

        RegisteredBatch(List<Path> sources, ConfigLoader.BatchLoader<T> loader) {
            this.sources = sources;
            this.loader = loader;
        }

        @Override
        public T get() {
            return current(this);
        }

        @Override
        public Path source() {
            return sources.get(0);
        }

        @Override
        public List<Path> sources() {
            return sources;
        }

        @Override
        public Object reloadValue(AbstractConfigLoader loader) throws ConfigValidationException {
            return this.loader.load();
        }
    }
}
