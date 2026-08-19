package rpg.core.config;

import java.util.List;
import java.util.Map;

/**
 * The value kinds a {@link FieldDefinition} can declare.
 *
 * <p>Kept deliberately small: B01 only needs enough type information to produce the fail-fast
 * message required by FR-002. Block-specific structures (item templates, ability definitions, ...)
 * are described by the schemas those blocks declare, not by new entries here.
 */
public enum FieldType {
    STRING(String.class, "a string"),
    BOOLEAN(Boolean.class, "a boolean"),
    INTEGER(Integer.class, "an integer"),
    LONG(Long.class, "a long"),
    DOUBLE(Double.class, "a decimal number"),
    LIST(List.class, "a list"),
    MAP(Map.class, "a mapping");

    private final Class<?> javaType;
    private final String description;

    FieldType(Class<?> javaType, String description) {
        this.javaType = javaType;
        this.description = description;
    }

    /** The Java type a validated value of this field type is guaranteed to have. */
    public Class<?> javaType() {
        return javaType;
    }

    /** Human-readable form used in {@link ConfigValidationException} messages. */
    public String description() {
        return description;
    }

    /**
     * Converts a raw parsed value to this field type, or returns {@code null} if the value cannot be
     * represented.
     *
     * <p>Numeric widening is accepted (a YAML {@code 5} is a valid {@code DOUBLE}) because YAML does
     * not distinguish the numeric types a schema wants to express. Everything else must match.
     */
    public Object coerce(Object raw) {
        if (raw == null) {
            return null;
        }
        switch (this) {
            case STRING:
                return raw instanceof String s ? s : null;
            case BOOLEAN:
                return raw instanceof Boolean b ? b : null;
            case INTEGER:
                if (raw instanceof Integer i) {
                    return i;
                }
                if (raw instanceof Long l && l.longValue() == l.intValue()) {
                    return Integer.valueOf(l.intValue());
                }
                return null;
            case LONG:
                if (raw instanceof Long l) {
                    return l;
                }
                if (raw instanceof Integer i) {
                    return Long.valueOf(i.longValue());
                }
                return null;
            case DOUBLE:
                if (raw instanceof Double d) {
                    return d;
                }
                if (raw instanceof Number n && !(raw instanceof Float)) {
                    return Double.valueOf(n.doubleValue());
                }
                if (raw instanceof Float f) {
                    return Double.valueOf(f.doubleValue());
                }
                return null;
            case LIST:
                return raw instanceof List<?> list ? list : null;
            case MAP:
                return raw instanceof Map<?, ?> map ? map : null;
            default:
                return null;
        }
    }
}
