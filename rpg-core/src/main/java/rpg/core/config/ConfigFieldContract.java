package rpg.core.config;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Server-free metadata for one configuration field.
 *
 * <p>This contract is deliberately independent of a parser. It records the exact document path,
 * the existing {@link FieldType}, presence/default semantics, numeric bounds and the descriptive
 * unit metadata needed by content schemas. {@link #toFieldDefinition()} is the compatibility seam
 * to the existing generic validation API.
 *
 * @param path exact dotted YAML path
 * @param type expected value kind
 * @param required whether the field must be present
 * @param defaultValue fallback for an absent optional field, if one exists
 * @param minimum inclusive numeric lower bound, if one exists
 * @param maximum inclusive numeric upper bound, if one exists
 * @param unit human-readable unit, when applicable
 * @param scale human-readable scale, when applicable
 * @param semantics human-readable semantic description, when applicable
 */
public record ConfigFieldContract(
        String path,
        FieldType type,
        boolean required,
        Optional<Object> defaultValue,
        Optional<Double> minimum,
        Optional<Double> maximum,
        Optional<String> unit,
        Optional<String> scale,
        Optional<String> semantics) {

    public ConfigFieldContract {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(defaultValue, "defaultValue");
        Objects.requireNonNull(minimum, "minimum");
        Objects.requireNonNull(maximum, "maximum");
        Objects.requireNonNull(unit, "unit");
        Objects.requireNonNull(scale, "scale");
        Objects.requireNonNull(semantics, "semantics");
        if (path.isBlank()) {
            throw new IllegalArgumentException("field path must not be blank");
        }
        if (required && defaultValue.isPresent()) {
            throw new IllegalArgumentException(
                    "required field '" + path + "' must not declare a default value");
        }
        validateBounds(path, type, minimum, maximum);
        validateText("unit", path, unit);
        validateText("scale", path, scale);
        validateText("semantics", path, semantics);

        if (defaultValue.isPresent()) {
            Object normalized = normalizeDefault(path, type, defaultValue.orElseThrow());
            if (normalized == null) {
                throw new IllegalArgumentException(
                        "default for '" + path + "' is not compatible with " + type);
            }
            validateRange(path, normalized, minimum, maximum);
            defaultValue = Optional.of(normalized);
        }
    }

    /** Starts a field contract with required presence semantics. */
    public static Builder builder(String path, FieldType type) {
        return new Builder(path, type);
    }

    /** Creates a required field contract without descriptive metadata. */
    public static ConfigFieldContract required(String path, FieldType type) {
        return builder(path, type).required().build();
    }

    /** Creates an optional field contract with a fallback default. */
    public static ConfigFieldContract optional(String path, FieldType type, Object defaultValue) {
        return builder(path, type).optional().defaultValue(defaultValue).build();
    }

    /**
     * Converts this richer metadata contract to the existing generic field definition. Unit and
     * descriptive metadata intentionally stay at the B16 contract layer because the existing
     * validator has no parser-facing representation for them yet.
     */
    public FieldDefinition toFieldDefinition() {
        return new FieldDefinition(path, type, required, defaultValue, minimum, maximum);
    }

    /**
     * Human-readable expectation used by the B16 validation boundary.
     *
     * <p>The description deliberately reports only metadata declared by this contract. It never
     * turns a missing unit, scale or semantic description into an inferred balancing decision.
     */
    public String expectedDescription() {
        StringBuilder description = new StringBuilder(type.description());
        if (minimum.isPresent() && maximum.isPresent()) {
            description.append(" in range [").append(minimum.get()).append(", ").append(maximum.get()).append("]");
        } else if (minimum.isPresent()) {
            description.append(" with minimum ").append(minimum.get());
        } else if (maximum.isPresent()) {
            description.append(" with maximum ").append(maximum.get());
        }
        unit.ifPresent(value -> description.append(" (unit: ").append(value).append(")"));
        scale.ifPresent(value -> description.append(" (scale: ").append(value).append(")"));
        semantics.ifPresent(value -> description.append(" (semantics: ").append(value).append(")"));
        if (required) {
            description.append(" (required)");
        } else if (defaultValue.isPresent()) {
            description.append(" (optional; default: ").append(defaultValue.get()).append(")");
        } else {
            description.append(" (optional)");
        }
        return description.toString();
    }

    private static void validateBounds(
            String path,
            FieldType type,
            Optional<Double> minimum,
            Optional<Double> maximum) {
        if ((minimum.isPresent() || maximum.isPresent()) && !isNumeric(type)) {
            throw new IllegalArgumentException(
                    "range metadata for '" + path + "' requires a numeric field type");
        }
        minimum.ifPresent(value -> validateFinite("minimum", path, value));
        maximum.ifPresent(value -> validateFinite("maximum", path, value));
        if (minimum.isPresent() && maximum.isPresent() && minimum.orElseThrow() > maximum.orElseThrow()) {
            throw new IllegalArgumentException(
                    "invalid range for '"
                            + path
                            + "': min "
                            + minimum.orElseThrow()
                            + " > max "
                            + maximum.orElseThrow());
        }
    }

    private static void validateFinite(String name, String path, double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(
                    name + " for '" + path + "' must be a finite number");
        }
    }

    private static void validateText(String name, String path, Optional<String> value) {
        value.ifPresent(
                text -> {
                    if (text.isBlank()) {
                        throw new IllegalArgumentException(
                                name + " for '" + path + "' must not be blank when present");
                    }
                });
    }

    private static void validateRange(
            String path, Object value, Optional<Double> minimum, Optional<Double> maximum) {
        if (!(value instanceof Number number)) {
            return;
        }
        double numericValue = number.doubleValue();
        if (minimum.filter(min -> numericValue < min).isPresent()
                || maximum.filter(max -> numericValue > max).isPresent()) {
            throw new IllegalArgumentException(
                    "default for '" + path + "' is outside its declared range");
        }
    }

    /**
     * Coerces a top-level default and recursively freezes collection values before they cross the
     * compatibility seam into {@link FieldDefinition}. The existing {@link MapConfigView} only
     * copies its outer map, so nested values must already be immutable here.
     */
    private static Object normalizeDefault(String path, FieldType type, Object raw) {
        Object normalized = type.coerce(raw);
        if (normalized == null) {
            throw new IllegalArgumentException(
                    "default for '" + path + "' is not compatible with " + type);
        }
        if (type == FieldType.LIST || type == FieldType.MAP) {
            return freezeValue(path, normalized, new IdentityHashMap<>());
        }
        return normalized;
    }

    private static Object freezeValue(
            String path, Object value, IdentityHashMap<Object, Boolean> visiting) {
        if (value == null) {
            throw new IllegalArgumentException("default at '" + path + "' must not contain null");
        }
        if (value instanceof List<?> list) {
            enterCollection(path, value, visiting);
            try {
                List<Object> frozen = new ArrayList<>(list.size());
                for (int index = 0; index < list.size(); index++) {
                    frozen.add(freezeValue(path + "[" + index + "]", list.get(index), visiting));
                }
                return List.copyOf(frozen);
            } finally {
                visiting.remove(value);
            }
        }
        if (value instanceof Map<?, ?> map) {
            enterCollection(path, value, visiting);
            try {
                Map<Object, Object> frozen = new LinkedHashMap<>(map.size());
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    Object key = freezeMapKey(path, entry.getKey());
                    if (frozen.containsKey(key)) {
                        throw new IllegalArgumentException(
                                "default at '" + path + "' contains duplicate map key: " + key);
                    }
                    frozen.put(
                            key,
                            freezeValue(path + "[" + key + "]", entry.getValue(), visiting));
                }
                return Map.copyOf(frozen);
            } finally {
                visiting.remove(value);
            }
        }
        if (isImmutableScalar(value)) {
            return value;
        }
        throw new IllegalArgumentException(
                "default at '" + path + "' contains unsupported value type: "
                        + value.getClass().getName());
    }

    private static Object freezeMapKey(String path, Object key) {
        if (key == null) {
            throw new IllegalArgumentException("default at '" + path + "' must not contain null map keys");
        }
        if (!isImmutableScalar(key)) {
            throw new IllegalArgumentException(
                    "default at '" + path + "' contains unsupported map key type: "
                            + key.getClass().getName());
        }
        return key;
    }

    private static void enterCollection(
            String path, Object value, IdentityHashMap<Object, Boolean> visiting) {
        if (visiting.put(value, Boolean.TRUE) != null) {
            throw new IllegalArgumentException("default at '" + path + "' contains a cyclic collection");
        }
    }

    private static boolean isImmutableScalar(Object value) {
        return value instanceof String
                || value instanceof Boolean
                || value instanceof Character
                || value instanceof Byte
                || value instanceof Short
                || value instanceof Integer
                || value instanceof Long
                || value instanceof Float
                || value instanceof Double
                || value instanceof BigInteger
                || value instanceof BigDecimal;
    }

    private static boolean isNumeric(FieldType type) {
        return switch (type) {
            case INTEGER, LONG, DOUBLE -> true;
            default -> false;
        };
    }

    /** Fluent builder for a field contract. */
    public static final class Builder {

        private final String path;
        private final FieldType type;
        private boolean required = true;
        private Optional<Object> defaultValue = Optional.empty();
        private Optional<Double> minimum = Optional.empty();
        private Optional<Double> maximum = Optional.empty();
        private Optional<String> unit = Optional.empty();
        private Optional<String> scale = Optional.empty();
        private Optional<String> semantics = Optional.empty();

        private Builder(String path, FieldType type) {
            this.path = Objects.requireNonNull(path, "path");
            this.type = Objects.requireNonNull(type, "type");
        }

        public Builder required() {
            required = true;
            defaultValue = Optional.empty();
            return this;
        }

        public Builder optional() {
            required = false;
            return this;
        }

        public Builder defaultValue(Object value) {
            if (value == null) {
                throw new IllegalArgumentException("defaultValue must not be null");
            }
            required = false;
            defaultValue = Optional.of(value);
            return this;
        }

        public Builder minimum(double value) {
            minimum = Optional.of(value);
            return this;
        }

        public Builder maximum(double value) {
            maximum = Optional.of(value);
            return this;
        }

        public Builder range(double minimum, double maximum) {
            this.minimum = Optional.of(minimum);
            this.maximum = Optional.of(maximum);
            return this;
        }

        public Builder unit(String value) {
            unit = Optional.of(Objects.requireNonNull(value, "unit"));
            return this;
        }

        public Builder scale(String value) {
            scale = Optional.of(Objects.requireNonNull(value, "scale"));
            return this;
        }

        public Builder semantics(String value) {
            semantics = Optional.of(Objects.requireNonNull(value, "semantics"));
            return this;
        }

        public ConfigFieldContract build() {
            return new ConfigFieldContract(
                    path, type, required, defaultValue, minimum, maximum, unit, scale, semantics);
        }
    }
}
