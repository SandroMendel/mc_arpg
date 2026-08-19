package rpg.core.config;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

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

        Map<String, Object> validated = new LinkedHashMap<>();
        for (FieldDefinition field : schema.fields()) {
            Object raw = lookup(document, field.path());

            if (raw == null) {
                if (field.required()) {
                    throw new ConfigValidationException(
                            source, field.path(), field.expectedDescription(), "missing");
                }
                validated.put(field.path(), field.defaultValue().orElseThrow());
                continue;
            }

            Object coerced = field.type().coerce(raw);
            if (coerced == null) {
                throw new ConfigValidationException(
                        source,
                        field.path(),
                        field.expectedDescription(),
                        describe(raw));
            }

            if (coerced instanceof Number number) {
                double asDouble = number.doubleValue();
                boolean belowMinimum = field.minimum().filter(min -> asDouble < min).isPresent();
                boolean aboveMaximum = field.maximum().filter(max -> asDouble > max).isPresent();
                if (belowMinimum || aboveMaximum) {
                    throw new ConfigValidationException(
                            source, field.path(), field.expectedDescription(), describe(coerced));
                }
            }

            validated.put(field.path(), coerced);
        }
        return new MapConfigView(schema.schemaVersion(), validated);
    }

    /** Resolves a dotted path such as {@code "combat.max-targets"} inside a nested document. */
    private static Object lookup(Map<String, Object> document, String dottedPath) {
        Object current = document;
        for (String segment : dottedPath.split("\\.", -1)) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(segment);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    private static String describe(Object value) {
        if (value instanceof String s) {
            return "'" + s + "'";
        }
        return String.valueOf(value);
    }
}
