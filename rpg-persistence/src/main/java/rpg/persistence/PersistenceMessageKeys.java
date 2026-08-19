package rpg.persistence;

import java.util.List;

import rpg.core.message.MessageKey;

/**
 * The message keys this module uses (FR-023).
 *
 * <p>Declared in one place so the bootstrap can prove every one of them has a text before a player
 * could ever see the situation. That matters more here than elsewhere: both of these appear only in
 * rare failure states - a rejected login during an outage, a forced disconnect when the buffer is
 * full - which are the worst possible moments to discover a missing text.
 */
public final class PersistenceMessageKeys {

    /** Storage unreachable, so no state can be loaded (FR-005a). */
    public static final MessageKey KICK_DATABASE_UNAVAILABLE =
            MessageKey.of("persistence.kick.database-unavailable");

    /** Write buffer at capacity; everyone must be disconnected to protect their progress (FR-009b). */
    public static final MessageKey KICK_BUFFER_EXHAUSTED =
            MessageKey.of("persistence.kick.buffer-exhausted");

    private PersistenceMessageKeys() {}

    /** Every key this module can ask for; consumed by the startup validation. */
    public static List<MessageKey> all() {
        return List.of(KICK_DATABASE_UNAVAILABLE, KICK_BUFFER_EXHAUSTED);
    }
}
