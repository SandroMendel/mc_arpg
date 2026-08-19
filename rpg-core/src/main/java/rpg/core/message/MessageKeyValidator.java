package rpg.core.message;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Checks at startup that every message key a module declares actually has a text (FR-023a).
 *
 * <p>The point is timing. Without this, a typo in a key or a forgotten entry in {@code messages.yml}
 * surfaces only when the situation occurs - and the situations these texts cover are exactly the
 * rare ones: a rejected login during a database outage, a forced disconnect when the buffer fills.
 * Those are the worst possible moments to discover a missing text. Failing the bootstrap instead
 * follows the same fail-fast reasoning as B01's configuration validation.
 */
public final class MessageKeyValidator {

    private MessageKeyValidator() {}

    /**
     * Verifies that {@code messages} contains a text for every key in {@code declaredKeys}.
     *
     * @throws MissingMessageKeysException listing <em>all</em> missing keys, not just the first -
     *     an operator fixing a configuration file wants the whole list in one pass
     */
    public static void verifyAllPresent(Messages messages, Collection<MessageKey> declaredKeys) {
        Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(declaredKeys, "declaredKeys");

        List<MessageKey> missing = new ArrayList<>();
        for (MessageKey key : declaredKeys) {
            if (!messages.contains(key)) {
                missing.add(key);
            }
        }
        if (!missing.isEmpty()) {
            missing.sort(MessageKey::compareTo);
            throw new MissingMessageKeysException(missing);
        }
    }

    /** Thrown when one or more declared keys have no configured text. */
    public static class MissingMessageKeysException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        @SuppressWarnings("serial") // List.copyOf returns a serializable implementation
        private final List<MessageKey> missingKeys;

        public MissingMessageKeysException(List<MessageKey> missingKeys) {
            super(
                    "messages.yml is missing "
                            + missingKeys.size()
                            + " text(s) for declared message key(s): "
                            + missingKeys.stream().map(MessageKey::value).toList());
            this.missingKeys = List.copyOf(missingKeys);
        }

        /** Every key that had no text, sorted. */
        public List<MessageKey> missingKeys() {
            return missingKeys;
        }
    }
}
