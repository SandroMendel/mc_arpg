package rpg.core.session;

import java.util.List;

import rpg.core.message.MessageKey;

/**
 * The message keys this block uses (FR-023 from B02's constitution work).
 *
 * <p>Declared in one place so the bootstrap can prove each has a text before a player could reach
 * the situation. That matters here more than elsewhere: every one of these appears only when
 * something already went wrong, which is the worst moment to discover a blank message.
 */
public final class SessionMessageKeys {

    /** The stored state could not be read; the login is refused (FR-011). */
    public static final MessageKey KICK_LOAD_FAILED = MessageKey.of("session.kick.load-failed");

    /** Loading exceeded its 5 second budget (FR-006). */
    public static final MessageKey KICK_LOAD_TIMEOUT = MessageKey.of("session.kick.load-timeout");

    /** The stored state is in a version this build does not know (FR-027). */
    public static final MessageKey KICK_UNKNOWN_VERSION =
            MessageKey.of("session.kick.unknown-data-version");

    /** A second character of the same class was rejected (FR-020). */
    public static final MessageKey CHARACTER_CLASS_TAKEN =
            MessageKey.of("session.character.class-taken");

    private SessionMessageKeys() {}

    /** Every key this block can ask for; consumed by the startup validation. */
    public static List<MessageKey> all() {
        return List.of(
                KICK_LOAD_FAILED, KICK_LOAD_TIMEOUT, KICK_UNKNOWN_VERSION, CHARACTER_CLASS_TAKEN);
    }
}
