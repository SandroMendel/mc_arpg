package rpg.core.config;

import java.nio.file.Path;

/**
 * Signals that a configuration source violates its declared schema.
 *
 * <p>Per FR-002 the message must name the file, the path inside the document and the expected
 * value/type, so an operator can fix the problem without reading the source code. A required field
 * is never silently replaced by a default.
 *
 * <p>Checked on purpose: loading configuration is an expected failure mode at startup and at reload,
 * and callers must decide between aborting the bootstrap (fail-fast) and keeping the previous
 * configuration (rollback, FR-004).
 */
public class ConfigValidationException extends Exception {

    private static final long serialVersionUID = 1L;

    @SuppressWarnings("serial") // Path implementations are not guaranteed serializable
    private final transient Path sourceFile;

    private final String documentPath;
    private final String expected;
    private final String actual;

    public ConfigValidationException(
            Path sourceFile, String documentPath, String expected, String actual) {
        this(sourceFile, documentPath, expected, actual, null);
    }

    public ConfigValidationException(
            Path sourceFile, String documentPath, String expected, String actual, Throwable cause) {
        super(message(sourceFile, documentPath, expected, actual), cause);
        this.sourceFile = sourceFile;
        this.documentPath = documentPath;
        this.expected = expected;
        this.actual = actual;
    }

    private static String message(
            Path sourceFile, String documentPath, String expected, String actual) {
        return "invalid configuration in "
                + sourceFile
                + " at '"
                + documentPath
                + "': expected "
                + expected
                + ", but was "
                + actual;
    }

    /** The configuration file the violation was found in. */
    public Path sourceFile() {
        return sourceFile;
    }

    /** Dotted path of the offending node inside the document, e.g. {@code "database.pool.size"}. */
    public String documentPath() {
        return documentPath;
    }

    /** Human-readable description of what the schema expects at {@link #documentPath()}. */
    public String expected() {
        return expected;
    }

    /** Human-readable description of what was actually found. */
    public String actual() {
        return actual;
    }
}
