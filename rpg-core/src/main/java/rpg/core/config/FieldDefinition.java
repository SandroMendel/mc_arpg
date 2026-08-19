package rpg.core.config;

import java.util.Objects;
import java.util.Optional;

/**
 * One declared field of a {@link ConfigSchema}.
 *
 * @param path dotted path inside the document, e.g. {@code "database.pool.size"}
 * @param type expected value kind
 * @param required whether the field must be present; a missing required field aborts the load
 *     (FR-002) and is never replaced by a default
 * @param defaultValue value used when an optional field is absent; empty for required fields
 * @param minimum inclusive lower bound for numeric fields, if any
 * @param maximum inclusive upper bound for numeric fields, if any
 */
public record FieldDefinition(
        String path,
        FieldType type,
        boolean required,
        Optional<Object> defaultValue,
        Optional<Double> minimum,
        Optional<Double> maximum) {

    public FieldDefinition {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(defaultValue, "defaultValue");
        Objects.requireNonNull(minimum, "minimum");
        Objects.requireNonNull(maximum, "maximum");
        if (path.isBlank()) {
            throw new IllegalArgumentException("field path must not be blank");
        }
        if (required && defaultValue.isPresent()) {
            throw new IllegalArgumentException(
                    "required field '" + path + "' must not declare a default value");
        }
    }

    /** A required field of the given type. */
    public static FieldDefinition required(String path, FieldType type) {
        return new FieldDefinition(
                path, type, true, Optional.empty(), Optional.empty(), Optional.empty());
    }

    /** An optional field falling back to {@code defaultValue} when absent. */
    public static FieldDefinition optional(String path, FieldType type, Object defaultValue) {
        return new FieldDefinition(
                path,
                type,
                false,
                Optional.of(defaultValue),
                Optional.empty(),
                Optional.empty());
    }

    /** Returns a copy of this definition constrained to the inclusive range {@code [min, max]}. */
    public FieldDefinition withRange(double min, double max) {
        if (min > max) {
            throw new IllegalArgumentException(
                    "invalid range for '" + path + "': min " + min + " > max " + max);
        }
        return new FieldDefinition(
                path, type, required, defaultValue, Optional.of(min), Optional.of(max));
    }

    /** Description of what this field expects, used in fail-fast messages. */
    public String expectedDescription() {
        StringBuilder sb = new StringBuilder(type.description());
        if (minimum.isPresent() && maximum.isPresent()) {
            sb.append(" in range [")
                    .append(minimum.get())
                    .append(", ")
                    .append(maximum.get())
                    .append("]");
        }
        if (required) {
            sb.append(" (required)");
        }
        return sb.toString();
    }
}
