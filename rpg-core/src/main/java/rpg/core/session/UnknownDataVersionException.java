package rpg.core.session;

/**
 * Thrown when a stored record carries a version this build does not know (FR-027).
 *
 * <p>Almost always means the data was written by a newer build - a downgraded server. Interpreting
 * it with the older rules would silently corrupt it, so the login is refused instead.
 */
public class UnknownDataVersionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int foundVersion;
    private final int supportedVersion;

    public UnknownDataVersionException(int foundVersion, int supportedVersion) {
        super(
                "stored data is at version "
                        + foundVersion
                        + " but this build supports at most "
                        + supportedVersion
                        + " - refusing to interpret it rather than risk corrupting it");
        this.foundVersion = foundVersion;
        this.supportedVersion = supportedVersion;
    }

    public int foundVersion() {
        return foundVersion;
    }

    public int supportedVersion() {
        return supportedVersion;
    }
}
