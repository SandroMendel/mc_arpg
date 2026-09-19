package rpg.core.config;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Validates a parsed document against a {@link ConfigSchema}.
 *
 * <p>Lives in {@code rpg-core} on purpose: this is the part FR-002 is actually about, and it must be
 * unit-testable without a file system, a YAML parser or a running server (Constitution VII.1). The
 * platform only contributes the parsing step.
 */
public final class SchemaValidator {

    private SchemaValidator() {}

    /**
     * Checks {@code document} against {@code schema} and returns a view over the validated values.
     *
     * @param source only used to build the error message (FR-002)
     * @throws ConfigValidationException on the first violation, naming file, document path and
     *     expected value
     */
    public static ConfigView validate(
            Path source, Map<String, Object> document, ConfigSchema<?> schema)
            throws ConfigValidationException {
        return validateTyped(source, document, schema);
    }

    /**
     * Checks {@code document} and retains the exact validating schema for typed content binding.
     *
     * @param source only used to build the error message (FR-002)
     * @throws ConfigValidationException on the first violation, naming file, document path and
     *     expected value
     */
    public static ValidatedConfigView validateTyped(
            Path source, Map<String, Object> document, ConfigSchema<?> schema)
            throws ConfigValidationException {
        return validateTyped(source, document, schema, Map.of());
    }

    /**
     * Validates with richer B16 field metadata while retaining the existing core schema and
     * coercion rules. The metadata map is keyed by the exact dotted field path.
     */
    public static ValidatedConfigView validateTyped(
            Path source,
            Map<String, Object> document,
            ConfigSchema<?> schema,
            Map<String, ConfigFieldContract> fieldContracts)
            throws ConfigValidationException {

        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(schema, "schema");
        Objects.requireNonNull(fieldContracts, "fieldContracts");
        if (document == null) {
            throw new ConfigValidationException(
                    source, "<document>", "a mapping at the document root", "null");
        }

        Map<String, Object> validated = new LinkedHashMap<>();
        for (FieldDefinition field : schema.fields()) {
            LookupResult lookup = lookup(document, field.path());
            String expected = expectedDescription(field, fieldContracts.get(field.path()));

            if (lookup.invalidPath() != null) {
                throw new ConfigValidationException(
                        source,
                        lookup.invalidPath(),
                        "a mapping",
                        describe(lookup.value()));
            }

            if (!lookup.present()) {
                if (field.required()) {
                    throw new ConfigValidationException(
                            source, field.path(), expected, "missing");
                }
                field.defaultValue().ifPresent(value -> validated.put(field.path(), value));
                continue;
            }

            Object raw = lookup.value();
            Object coerced = field.type().coerce(raw);
            if (coerced == null) {
                throw new ConfigValidationException(
                        source, field.path(), expected, describe(raw));
            }

            if (coerced instanceof Number number) {
                double asDouble = number.doubleValue();
                if (!Double.isFinite(asDouble)) {
                    throw new ConfigValidationException(
                            source, field.path(), expected, describe(raw));
                }
                boolean belowMinimum = field.minimum().filter(min -> asDouble < min).isPresent();
                boolean aboveMaximum = field.maximum().filter(max -> asDouble > max).isPresent();
                if (belowMinimum || aboveMaximum) {
                    throw new ConfigValidationException(
                            source, field.path(), expected, describe(coerced));
                }
            }

            validated.put(field.path(), coerced);
        }
        return new MapConfigView(schema, validated);
    }

    /** Resolves a dotted path such as {@code "combat.max-targets"} inside a nested document. */
    private static LookupResult lookup(Map<String, Object> document, String dottedPath) {
        Object current = document;
        StringBuilder path = new StringBuilder();
        for (String segment : dottedPath.split("\\.", -1)) {
            if (!(current instanceof Map<?, ?> map)) {
                return LookupResult.invalid(path.toString(), current);
            }
            if (!map.containsKey(segment)) {
                return LookupResult.missing();
            }
            if (path.length() > 0) {
                path.append('.');
            }
            path.append(segment);
            current = map.get(segment);
        }
        return LookupResult.present(current);
    }

    private static String expectedDescription(
            FieldDefinition field, ConfigFieldContract contract) {
        return contract == null ? field.expectedDescription() : contract.expectedDescription();
    }

    private static String describe(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String s) {
            return "'" + s + "'";
        }
        return String.valueOf(value);
    }

    private record LookupResult(boolean present, Object value, String invalidPath) {
        private static LookupResult missing() {
            return new LookupResult(false, null, null);
        }

        private static LookupResult present(Object value) {
            return new LookupResult(true, value, null);
        }

        private static LookupResult invalid(String path, Object value) {
            return new LookupResult(false, value, path);
        }
    }
}
