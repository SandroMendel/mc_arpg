package rpg.content.migration;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import rpg.content.ContentDocumentContract;

/**
 * The first B16 migration: a known unversioned document becomes version 1.
 *
 * <p>The current v0 files already have the v1 grouping and field names. The only required envelope
 * change is the root {@code schemaVersion: 1}; no value, unit, identifier, or nested structure is
 * invented here. A later structural change must be a separate registered step with its own
 * fixtures.
 */
public final class LegacyV0ToV1Step implements MigrationStep {

    public static final int FROM_VERSION = 0;
    public static final int TO_VERSION = 1;

    private static final String SCHEMA_VERSION = "schemaVersion";

    private final ContentDocumentContract contract;

    public LegacyV0ToV1Step(ContentDocumentContract contract) {
        this.contract = Objects.requireNonNull(contract, "contract");
    }

    @Override
    public int fromVersion() {
        return FROM_VERSION;
    }

    @Override
    public int toVersion() {
        return TO_VERSION;
    }

    /** The exact B16 file this registered step accepts. */
    public String fileName() {
        return contract.fileName();
    }

    @Override
    public Map<String, Object> apply(Path source, Map<String, Object> legacy)
            throws B16MigrationException {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(legacy, "legacy");
        if (!contract.fileName().equals(fileNameOf(source))) {
            throw failure(source, "<document>", "the registered file " + contract.fileName());
        }
        if (legacy.containsKey(SCHEMA_VERSION)) {
            throw failure(
                    source,
                    SCHEMA_VERSION,
                    "an unversioned legacy document (schemaVersion must be absent)");
        }

        LinkedHashMap<String, Object> copied = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : legacy.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw failure(source, "<document>", "string root keys");
            }
            copied.put(key, entry.getValue());
        }

        if (copied.isEmpty()) {
            throw failure(source, "<document>", "a non-empty known legacy structure");
        }

        List<String> allowed = contract.fixedRootKeys();
        for (String key : copied.keySet()) {
            if (!allowed.contains(key)) {
                throw failure(
                        source,
                        key,
                        "one of the known legacy root sections " + allowed);
            }
        }

        LinkedHashMap<String, Object> migrated = new LinkedHashMap<>();
        migrated.put(SCHEMA_VERSION, TO_VERSION);
        migrated.putAll(copied);
        return Collections.unmodifiableMap(migrated);
    }

    private static String fileNameOf(Path source) {
        Path fileName = source.getFileName();
        return fileName == null ? "" : fileName.toString();
    }

    private static B16MigrationException failure(Path source, String path, String expected) {
        return new B16MigrationException(source, path, expected);
    }
}
