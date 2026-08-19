package rpg.core.message;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The identifier of one player-facing text.
 *
 * <p>Constitution V forbids hard-coded player texts: every text is reached through a key, and the
 * wording lives in configuration. The rationale there is explicit - this prepares multilingual
 * support structurally "without implementing it now". So the indirection is required from the
 * start; only the translation is not.
 *
 * <p>A typed key rather than a bare {@code String} on purpose: it makes the declaration sites
 * enumerable, which is what lets {@link MessageKeyValidator} prove at startup that every key a
 * module uses actually has a text behind it (FR-023a).
 *
 * @param value dotted identifier, e.g. {@code "persistence.login.database-unavailable"}
 */
public record MessageKey(String value) implements Comparable<MessageKey> {

    private static final Pattern VALID = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*(?:\\.[a-z0-9]+(?:-[a-z0-9]+)*)+");

    public MessageKey {
        Objects.requireNonNull(value, "value");
        if (!VALID.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "message key '"
                            + value
                            + "' must be lower-case dotted segments, e.g."
                            + " 'persistence.login.database-unavailable'");
        }
    }

    /** Convenience factory so call sites read as {@code MessageKey.of("a.b")}. */
    public static MessageKey of(String value) {
        return new MessageKey(value);
    }

    @Override
    public int compareTo(MessageKey other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
