package rpg.content.migration;

import java.nio.file.Path;
import java.util.Map;

/**
 * One explicitly registered, parser-free transformation between B16 document versions.
 *
 * <p>The step receives an already parsed document and returns a new value. It neither reads YAML
 * nor writes files; those responsibilities stay with the selected migration tool and the
 * platform parser. A step is intentionally not a {@code ConfigSchema}: migration is an explicit
 * source-protection workflow, not a runtime default or validation bypass.
 */
public interface MigrationStep {

    /** Version accepted by this step. */
    int fromVersion();

    /** Version produced by this step. */
    int toVersion();

    /**
     * Applies the step without mutating the supplied parsed document.
     *
     * @param source source path used in diagnostics and legacy-file selection
     * @param legacy parsed legacy document
     * @return a new parsed document in {@link #toVersion()}
     * @throws B16MigrationException when the structure is not the known legacy shape
     */
    Map<String, Object> apply(Path source, Map<String, Object> legacy)
            throws B16MigrationException;
}
