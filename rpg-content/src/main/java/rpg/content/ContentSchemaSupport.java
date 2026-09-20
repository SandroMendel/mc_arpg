package rpg.content;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import rpg.core.config.ConfigFieldContract;
import rpg.core.config.ConfigSchema;
import rpg.core.config.FieldDefinition;

/** Shared helpers for the nine T010 domain schema declarations. */
public final class ContentSchemaSupport {

    private ContentSchemaSupport() {}

    public static ContentDocumentContract document(String fileName) {
        return ContentKeyPolicy.defaultPolicy().requireDocument(fileName);
    }

    public static ConfigFieldContract field(FieldDefinition definition) {
        ConfigFieldContract.Builder builder =
                ConfigFieldContract.builder(definition.path(), definition.type());
        if (definition.required()) {
            builder.required();
        } else {
            builder.optional();
            definition.defaultValue().ifPresent(builder::defaultValue);
        }
        definition.minimum().ifPresent(builder::minimum);
        definition.maximum().ifPresent(builder::maximum);
        // Only stable units encoded in the canonical field name are inferred here. Scale and
        // semantic descriptions remain absent until Specify assigns them; no balancing meaning is
        // invented by T010.
        unitFor(definition.path()).ifPresent(builder::unit);
        return builder.build();
    }

    /** Wraps the exact core schema instance; use this for all T010 domain facades. */
    public static <T> ContentDocumentSchema<T> wrap(
            String fileName, ConfigSchema<T> coreSchema) {
        return ContentDocumentSchema.fromCore(document(fileName), coreSchema);
    }

    public static ContentRegistryContract registry(
            String fileName, String path) {
        return document(fileName)
                .registry(path)
                .orElseThrow(() -> new IllegalArgumentException(
                        "registry path is not declared for " + fileName + ": " + path));
    }

    public static <K, V> TypedContentRegistry<K, V> registry(
            String fileName, String path, Map<K, V> entries) {
        return new TypedContentRegistry<>(registry(fileName, path), entries);
    }

    public static Optional<String> unitFor(String path) {
        String normalized = path.toLowerCase(Locale.ROOT);
        if (normalized.endsWith("-ms")
                || normalized.contains("-ms.")
                || normalized.endsWith("-millis")
                || normalized.contains("-millis.")) {
            return Optional.of("milliseconds");
        }
        if (normalized.endsWith("-seconds") || normalized.contains("-seconds.")) {
            return Optional.of("seconds");
        }
        if (normalized.endsWith("-minutes") || normalized.contains("-minutes.")) {
            return Optional.of("minutes");
        }
        if (normalized.endsWith("-days") || normalized.contains("-days.")) {
            return Optional.of("days");
        }
        if (normalized.endsWith("-blocks") || normalized.contains("-blocks.")) {
            return Optional.of("blocks");
        }
        return Optional.empty();
    }
}
