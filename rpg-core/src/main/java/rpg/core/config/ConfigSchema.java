package rpg.core.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Declares the permitted structure and values of a configuration source, and how a validated
 * document is bound to a typed configuration object.
 *
 * <p>The schema is versioned (Constitution IV.1) so future migration paths stay open. Validation is
 * fail-fast: a document that does not satisfy every required field is rejected at start (FR-002) and
 * discarded at reload while the previously valid configuration stays active (FR-004).
 *
 * <p>Note on {@code sourceFile}: data-model.md lists the source path as part of the schema. It is
 * intentionally not duplicated here because the path is already passed to
 * {@link ConfigLoader#loadAndValidate(java.nio.file.Path, ConfigSchema)} and is carried into every
 * {@link ConfigValidationException}. Keeping it out lets one schema validate several sources.
 *
 * @param <T> the typed configuration object produced from a validated document
 */
public final class ConfigSchema<T> {

    private final int schemaVersion;
    private final List<FieldDefinition> fields;
    private final Function<ConfigView, T> binder;

    private ConfigSchema(int schemaVersion, List<FieldDefinition> fields, Function<ConfigView, T> binder) {
        this.schemaVersion = schemaVersion;
        this.fields = List.copyOf(fields);
        this.binder = binder;
    }

    public static <T> Builder<T> builder(int schemaVersion) {
        return new Builder<>(schemaVersion);
    }

    /** Version of this schema; carried into the {@link ConfigView} of every validated document. */
    public int schemaVersion() {
        return schemaVersion;
    }

    /** The declared fields, in declaration order. */
    public List<FieldDefinition> fields() {
        return fields;
    }

    /** Builds the typed configuration object from an already validated document. */
    public T bind(ConfigView view) {
        return binder.apply(view);
    }

    /** Fluent builder for a schema. */
    public static final class Builder<T> {

        private final int schemaVersion;
        private final Map<String, FieldDefinition> fields = new LinkedHashMap<>();
        private Function<ConfigView, T> binder;

        private Builder(int schemaVersion) {
            if (schemaVersion < 1) {
                throw new IllegalArgumentException("schemaVersion must be >= 1");
            }
            this.schemaVersion = schemaVersion;
        }

        public Builder<T> field(FieldDefinition definition) {
            Objects.requireNonNull(definition, "definition");
            FieldDefinition previous = fields.putIfAbsent(definition.path(), definition);
            if (previous != null) {
                throw new IllegalArgumentException(
                        "duplicate field path in schema: " + definition.path());
            }
            return this;
        }

        public Builder<T> required(String path, FieldType type) {
            return field(FieldDefinition.required(path, type));
        }

        public Builder<T> optional(String path, FieldType type, Object defaultValue) {
            return field(FieldDefinition.optional(path, type, defaultValue));
        }

        /** Sets the function that turns a validated document into the typed configuration object. */
        public Builder<T> boundTo(Function<ConfigView, T> binder) {
            this.binder = Objects.requireNonNull(binder, "binder");
            return this;
        }

        public ConfigSchema<T> build() {
            if (binder == null) {
                throw new IllegalStateException("schema requires a binder (call boundTo(...))");
            }
            return new ConfigSchema<>(schemaVersion, new ArrayList<>(fields.values()), binder);
        }
    }
}
