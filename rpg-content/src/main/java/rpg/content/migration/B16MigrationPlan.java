package rpg.content.migration;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import rpg.content.ContentDocumentContract;
import rpg.content.ContentKeyPolicy;

/**
 * Registry and dispatcher for the explicit B16 migration path.
 *
 * <p>Only the nine known B16 filenames are registered. An unversioned document is the supported
 * legacy-v0 signal; an explicit version is never guessed or silently rewritten. A v1 document is
 * returned unchanged so a tool can safely report an idempotent no-op.
 */
public final class B16MigrationPlan {

    public static final int LEGACY_VERSION = 0;
    public static final int CURRENT_VERSION = 1;

    private static final String SCHEMA_VERSION = "schemaVersion";

    private final Map<String, MigrationStep> stepsByFile;

    public B16MigrationPlan(ContentKeyPolicy policy) {
        Objects.requireNonNull(policy, "policy");
        LinkedHashMap<String, MigrationStep> steps = new LinkedHashMap<>();
        for (ContentDocumentContract document : policy.documents()) {
            MigrationStep previous =
                    steps.put(document.fileName(), new LegacyV0ToV1Step(document));
            if (previous != null) {
                throw new IllegalArgumentException(
                        "duplicate migration filename: " + document.fileName());
            }
        }
        if (steps.isEmpty()) {
            throw new IllegalArgumentException("migration plan must contain a step");
        }
        this.stepsByFile = Collections.unmodifiableMap(steps);
    }

    /** The canonical migration plan for all current B16 documents. */
    public static B16MigrationPlan standard() {
        return new B16MigrationPlan(ContentKeyPolicy.defaultPolicy());
    }

    /** Registered steps in deterministic document order. */
    public List<MigrationStep> steps() {
        return List.copyOf(stepsByFile.values());
    }

    /** Returns the registered step for a filename, if it is a B16 document. */
    public java.util.Optional<MigrationStep> step(String fileName) {
        return java.util.Optional.ofNullable(stepsByFile.get(fileName));
    }

    /**
     * Applies the registered step or returns an explicit v1 no-op.
     *
     * @param source source path used to select the B16 file and report failures
     * @param document parsed YAML root
     * @return migrated document and whether a transformation occurred
     * @throws B16MigrationException when the file is unknown, unversioned but malformed, or has an
     *     unsupported explicit version
     */
    public MigrationResult migrate(Path source, Map<String, Object> document)
            throws B16MigrationException {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(document, "document");
        String fileName = fileNameOf(source);
        MigrationStep step = stepsByFile.get(fileName);
        if (step == null) {
            throw failure(source, "<document>", "one of the registered B16 filenames");
        }

        if (document.containsKey(SCHEMA_VERSION)) {
            Object version = document.get(SCHEMA_VERSION);
            if (version instanceof Integer integer && integer == CURRENT_VERSION) {
                return new MigrationResult(document, false);
            }
            throw failure(source, SCHEMA_VERSION, "the exact integer schemaVersion 1");
        }

        return new MigrationResult(step.apply(source, document), true);
    }

    /** Result of one explicit migration attempt. */
    public record MigrationResult(Map<String, Object> document, boolean changed) {

        public MigrationResult {
            document =
                    Collections.unmodifiableMap(
                            new LinkedHashMap<>(Objects.requireNonNull(document, "document")));
        }
    }

    private static String fileNameOf(Path source) {
        Path fileName = source.getFileName();
        return fileName == null ? "" : fileName.toString();
    }

    private static B16MigrationException failure(Path source, String path, String expected) {
        return new B16MigrationException(source, path, expected);
    }
}
