package rpg.content;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import rpg.core.config.ConfigFieldContract;
import rpg.core.config.ConfigSchema;
import rpg.core.config.ConfigValidationException;
import rpg.core.config.ConfigView;
import rpg.core.config.FieldDefinition;
import rpg.core.config.SchemaValidator;
import rpg.core.config.ValidatedConfigView;

/**
 * Immutable, parser-free schema metadata for one versioned B16 content document.
 *
 * <p>The container binds a {@link ContentDocumentContract} to ordered field metadata and the
 * existing typed {@link ConfigSchema}. It checks declaration invariants while being built and
 * implements the parser-free root envelope plus fixed-child gate. Dynamic registry entries and
 * their nested policies remain deferred to later B16 validation tasks; YAML parsing and platform
 * integration are outside this container.
 *
 * @param <T> typed configuration value produced by the schema binder
 */
public final class ContentDocumentSchema<T> {

    private final ContentDocumentContract document;
    private final int schemaVersion;
    private final List<ConfigFieldContract> fields;
    private final Map<String, ConfigFieldContract> fieldsByPath;
    private final Map<String, List<String>> fixedChildKeysByPath;
    private final ConfigSchema<T> configSchema;

    private ContentDocumentSchema(
            ContentDocumentContract document,
            int schemaVersion,
            List<ConfigFieldContract> fields,
            ConfigSchema<T> configSchema) {
        this.document = Objects.requireNonNull(document, "document");
        if (schemaVersion < 1) {
            throw new IllegalArgumentException("schemaVersion must be >= 1");
        }
        this.schemaVersion = schemaVersion;
        this.fields = List.copyOf(fields);
        this.fieldsByPath = indexFields(this.fields);
        this.fixedChildKeysByPath = fixedChildKeys(this.fields, document);
        this.configSchema = Objects.requireNonNull(configSchema, "configSchema");
        if (configSchema.schemaVersion() != schemaVersion) {
            throw new IllegalArgumentException(
                    "content schema version must match ConfigSchema.schemaVersion()");
        }
    }

    /** Starts a typed content schema for a document and schema version. */
    public static <T> Builder<T> builder(
            ContentDocumentContract document,
            int schemaVersion,
            Function<ConfigView, T> binder) {
        return new Builder<>(document, schemaVersion, binder);
    }

    /**
     * Wraps the exact existing core schema instance without rebuilding its fields or binder.
     *
     * <p>This is the preferred T010 entry point. The returned facade therefore preserves the
     * core schema identity used by validation and typed binding.
     */
    public static <T> ContentDocumentSchema<T> fromCore(
            ContentDocumentContract document, ConfigSchema<T> configSchema) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(configSchema, "configSchema");
        List<ConfigFieldContract> fields =
                configSchema.fields().stream().map(ContentSchemaSupport::field).toList();
        return new ContentDocumentSchema<>(
                document, configSchema.schemaVersion(), fields, configSchema);
    }

    public ContentDocumentContract document() {
        return document;
    }

    public int schemaVersion() {
        return schemaVersion;
    }

    /** Field contracts in declaration order. */
    public List<ConfigFieldContract> fields() {
        return fields;
    }

    /** Finds a declared field by its exact dotted YAML path. */
    public Optional<ConfigFieldContract> field(String path) {
        return Optional.ofNullable(fieldsByPath.get(path));
    }

    /** Finds a field or fails with a useful declaration error. */
    public ConfigFieldContract requireField(String path) {
        return field(path)
                .orElseThrow(
                        () -> new IllegalArgumentException("field is not declared: " + path));
    }

    /** Typed generic schema used by the existing core loader/validator. */
    public ConfigSchema<T> configSchema() {
        return configSchema;
    }

    /**
     * Binds a view produced by this document schema's exact validating schema instance.
     *
     * <p>This provenance and version gate does not parse YAML or validate root or unknown keys.
     */
    public T bind(ValidatedConfigView view) {
        Objects.requireNonNull(view, "view");
        if (view.schemaVersion() != schemaVersion) {
            throw new IllegalArgumentException(
                    "config view schema version "
                            + view.schemaVersion()
                            + " does not match content schema version "
                            + schemaVersion);
        }
        if (view.validatingSchema() != configSchema) {
            throw new IllegalArgumentException(
                    "config view was validated by a different ConfigSchema instance");
        }
        return configSchema.bind(view);
    }

    /**
     * Validates a parsed content document, preserving the exact source path in any failure.
     *
     * <p>The envelope permits only the reserved keys and fixed root sections declared by the
     * document contract. Dynamic registry identifiers below those sections are intentionally not
     * expanded here; nested-key and cross-domain rules belong to T012/T014.
     *
     * @param source operator-facing source filename
     * @param root parsed document root, normally supplied by {@code YamlConfigLoader}
     * @return a view validated by this schema's exact core schema instance
     * @throws ConfigValidationException if the envelope or a declared field is invalid
     */
    public ValidatedConfigView validate(Path source, Object root)
            throws ConfigValidationException {
        Map<String, Object> document = validateRoot(source, root);
        validateFixedChildKeys(source, document);
        return SchemaValidator.validateTyped(source, document, configSchema, fieldsByPath);
    }

    /** Validates and binds a parsed document through this schema's single validation seam. */
    public T validateAndBind(Path source, Object root) throws ConfigValidationException {
        return bind(validate(source, root));
    }

    /**
     * Performs the parser-free document-envelope gate for this schema.
     *
     * <p>Field-level and dynamic-entry validation remain the responsibility of T011+; this
     * T010-compatible method intentionally keeps its existing IllegalArgumentException API and
     * checks the root envelope only.
     */
    public void assertRootStructure(Object root) {
        if (!(root instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("content document root must be a map");
        }
        for (Object key : map.keySet()) {
            if (!(key instanceof String)) {
                throw new IllegalArgumentException("content document root keys must be strings");
            }
        }
        Object version = map.get("schemaVersion");
        if (!(version instanceof Integer) || ((Integer) version) != schemaVersion) {
            throw new IllegalArgumentException(
                    "schemaVersion must be the integer " + schemaVersion);
        }
        List<String> allowed = new ArrayList<>(document.reservedRootKeys());
        allowed.addAll(document.fixedRootKeys());
        for (String key : map.keySet().stream().map(String.class::cast).toList()) {
            if (!allowed.contains(key)) {
                throw new IllegalArgumentException("unknown content root key: " + key);
            }
        }
    }

    /**
     * Checks fixed child maps that can be expressed unambiguously by declared field prefixes.
     * Dynamic registry maps and all descendants below them are deliberately opaque here, so
     * numeric level IDs and open mappings remain valid until their later entry-specific policy.
     */
    private void validateFixedChildKeys(Path source, Map<String, Object> root)
            throws ConfigValidationException {
        for (Map.Entry<String, List<String>> entry : fixedChildKeysByPath.entrySet()) {
            Object value = lookup(root, entry.getKey());
            if (!(value instanceof Map<?, ?> map)) {
                continue;
            }
            List<String> allowed = entry.getValue();
            for (Map.Entry<?, ?> child : map.entrySet()) {
                if (!(child.getKey() instanceof String key) || !allowed.contains(key)) {
                    String key = String.valueOf(child.getKey());
                    throw new ConfigValidationException(
                            source,
                            entry.getKey() + "." + key,
                            "one of the fixed child keys " + allowed,
                            "unknown fixed child key '" + key + "'");
                }
            }
        }
    }

    private static Object lookup(Map<String, Object> root, String dottedPath) {
        Object current = root;
        for (String segment : dottedPath.split("\\.", -1)) {
            if (!(current instanceof Map<?, ?> map) || !map.containsKey(segment)) {
                return null;
            }
            current = map.get(segment);
        }
        return current;
    }

    private static Map<String, List<String>> fixedChildKeys(
            List<ConfigFieldContract> fields, ContentDocumentContract document) {
        Map<String, Set<String>> mutable = new LinkedHashMap<>();
        for (ConfigFieldContract field : fields) {
            addFixedPathChildren(mutable, field.path(), document);
        }
        // A document contract can describe fixed children that are deliberately not represented as
        // scalar core fields (for example, a future structured section). Include those declarations
        // without attempting to enumerate any dynamic registry entry.
        for (String fixedPath : document.fixedPaths()) {
            if (fixedPath.indexOf('<') >= 0 || fixedPath.indexOf('[') >= 0) {
                continue;
            }
            addFixedPathChildren(mutable, fixedPath, document);
        }
        Map<String, List<String>> result = new LinkedHashMap<>();
        mutable.forEach((path, keys) -> result.put(path, List.copyOf(keys)));
        return Collections.unmodifiableMap(result);
    }

    private static void addFixedPathChildren(
            Map<String, Set<String>> target, String path, ContentDocumentContract document) {
        String[] segments = path.split("\\.", -1);
        for (int childIndex = 1; childIndex < segments.length; childIndex++) {
            String mapPath = String.join(".", java.util.Arrays.copyOf(segments, childIndex));
            if (isUnderDynamicRegistry(mapPath, document)) {
                continue;
            }
            target.computeIfAbsent(mapPath, ignored -> new java.util.LinkedHashSet<>())
                    .add(segments[childIndex]);
        }
    }

    private static boolean isUnderDynamicRegistry(
            String mapPath, ContentDocumentContract document) {
        for (ContentRegistryContract registry : document.dynamicRegistries()) {
            String[] segments = registry.path().split("\\.", -1);
            for (int index = 0; index < segments.length; index++) {
                if (segments[index].contains("<") || segments[index].contains("[]")) {
                    String dynamicRoot = String.join(
                            ".", java.util.Arrays.copyOf(segments, index));
                    if (mapPath.equals(dynamicRoot) || mapPath.startsWith(dynamicRoot + ".")) {
                        return true;
                    }
                    break;
                }
            }
        }
        return false;
    }

    private Map<String, Object> validateRoot(Path source, Object root)
            throws ConfigValidationException {
        Objects.requireNonNull(source, "source");
        if (!(root instanceof Map<?, ?> map)) {
            throw new ConfigValidationException(
                    source, "<document>", "a mapping at the document root", describe(root));
        }

        Map<String, Object> document = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw new ConfigValidationException(
                        source,
                        "<document>",
                        "string keys at the document root",
                        describeKey(entry.getKey()));
            }
            document.put(key, entry.getValue());
        }

        Object version = document.get("schemaVersion");
        if (!(version instanceof Integer) || ((Integer) version) != schemaVersion) {
            throw new ConfigValidationException(
                    source,
                    "schemaVersion",
                    "the exact integer schemaVersion " + schemaVersion,
                    describe(version));
        }

        List<String> allowed = documentContractRootKeys();
        for (String key : document.keySet()) {
            if (!allowed.contains(key)) {
                throw new ConfigValidationException(
                        source,
                        key,
                        "one of the fixed root keys " + allowed,
                        "unknown fixed root key '" + key + "'");
            }
        }
        return document;
    }

    private List<String> documentContractRootKeys() {
        List<String> allowed = new ArrayList<>(document.reservedRootKeys());
        for (String key : document.fixedRootKeys()) {
            if (!allowed.contains(key)) {
                allowed.add(key);
            }
        }
        return List.copyOf(allowed);
    }

    private static String describe(Object value) {
        if (value == null) {
            return "null";
        }
        return value.getClass().getSimpleName() + " (" + value + ")";
    }

    private static String describeKey(Object key) {
        if (key == null) {
            return "null key";
        }
        return "key " + key + " (" + key.getClass().getSimpleName() + ")";
    }

    private static Map<String, ConfigFieldContract> indexFields(
            List<ConfigFieldContract> fields) {
        Map<String, ConfigFieldContract> indexed = new LinkedHashMap<>();
        for (ConfigFieldContract field : fields) {
            ConfigFieldContract previous = indexed.putIfAbsent(field.path(), field);
            if (previous != null) {
                throw new IllegalArgumentException(
                        "duplicate field path in content schema: " + field.path());
            }
        }
        return Map.copyOf(indexed);
    }

    /** Fluent builder preserving declaration order. */
    public static final class Builder<T> {

        private final ContentDocumentContract document;
        private final int schemaVersion;
        private final Function<ConfigView, T> binder;
        private final Map<String, ConfigFieldContract> fields = new LinkedHashMap<>();

        private Builder(
                ContentDocumentContract document,
                int schemaVersion,
                Function<ConfigView, T> binder) {
            this.document = Objects.requireNonNull(document, "document");
            if (schemaVersion < 1) {
                throw new IllegalArgumentException("schemaVersion must be >= 1");
            }
            this.schemaVersion = schemaVersion;
            this.binder = Objects.requireNonNull(binder, "binder");
        }

        public Builder<T> field(ConfigFieldContract field) {
            Objects.requireNonNull(field, "field");
            ConfigFieldContract previous = fields.putIfAbsent(field.path(), field);
            if (previous != null) {
                throw new IllegalArgumentException(
                        "duplicate field path in content schema: " + field.path());
            }
            return this;
        }

        public ContentDocumentSchema<T> build() {
            List<ConfigFieldContract> orderedFields = new ArrayList<>(fields.values());
            ConfigSchema.Builder<T> typedBuilder = ConfigSchema.builder(schemaVersion);
            for (ConfigFieldContract field : orderedFields) {
                FieldDefinition definition = field.toFieldDefinition();
                typedBuilder.field(definition);
            }
            ConfigSchema<T> typedSchema = typedBuilder.boundTo(binder).build();
            return new ContentDocumentSchema<>(document, schemaVersion, orderedFields, typedSchema);
        }
    }
}
