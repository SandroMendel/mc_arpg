package rpg.content.migration;

import java.nio.file.Path;
import java.util.Objects;

/** A deterministic, operator-facing failure from the explicit B16 migration path. */
public final class B16MigrationException extends Exception {

    private static final long serialVersionUID = 1L;

    private final Path sourceFile;
    private final String documentPath;

    public B16MigrationException(Path sourceFile, String documentPath, String reason) {
        super(message(sourceFile, documentPath, reason));
        this.sourceFile = Objects.requireNonNull(sourceFile, "sourceFile");
        this.documentPath = Objects.requireNonNull(documentPath, "documentPath");
    }

    /** The source file that could not be migrated. */
    public Path sourceFile() {
        return sourceFile;
    }

    /** The legacy document path at which migration stopped. */
    public String documentPath() {
        return documentPath;
    }

    private static String message(Path sourceFile, String documentPath, String reason) {
        return "cannot migrate "
                + Objects.requireNonNull(sourceFile, "sourceFile")
                + " at '"
                + Objects.requireNonNull(documentPath, "documentPath")
                + "': "
                + Objects.requireNonNull(reason, "reason");
    }
}
