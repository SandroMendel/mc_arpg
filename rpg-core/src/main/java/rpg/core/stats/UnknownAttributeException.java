package rpg.core.stats;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * An attribute key that does not name one of the eight attributes (FR-004a, FR-009).
 *
 * <p>The message lists the permitted keys. A typo in a configuration file is the most likely way to
 * get here, and "unknown attribute 'phyiscalDamage'" without the list of correct spellings makes
 * the operator go looking for documentation that this message could have been.
 */
public class UnknownAttributeException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    @SuppressWarnings("serial") // String is serializable
    private final String key;

    public UnknownAttributeException(String key) {
        super(
                "unknown attribute '"
                        + key
                        + "' - the eight attributes are: "
                        + Arrays.stream(Attribute.values())
                                .map(Attribute::key)
                                .collect(Collectors.joining(", ")));
        this.key = key;
    }

    /** The key that could not be resolved. */
    public String key() {
        return key;
    }
}
