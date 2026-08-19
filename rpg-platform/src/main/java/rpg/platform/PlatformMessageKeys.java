package rpg.platform;

import java.util.List;

import rpg.core.message.MessageKey;

/**
 * The message keys this module uses.
 *
 * <p>Declaring them in one place per module is what makes the startup validation possible: the
 * bootstrap collects every module's {@link #all()} and checks each key has a text before a player
 * can reach any of these situations (FR-023a). A key created ad hoc at a call site would escape
 * that check, which is why they live here rather than inline.
 */
public final class PlatformMessageKeys {

    /** Shown while the plugin is still starting up (FR-013). */
    public static final MessageKey KICK_STARTING_UP = MessageKey.of("server.kick.starting-up");

    /** Shown when the bootstrap failed and no session may be granted. */
    public static final MessageKey KICK_BOOTSTRAP_FAILED =
            MessageKey.of("server.kick.bootstrap-failed");

    /** Shown while the server is shutting down. */
    public static final MessageKey KICK_SHUTTING_DOWN = MessageKey.of("server.kick.shutting-down");

    private PlatformMessageKeys() {}

    /** Every key this module can ask for; consumed by the startup validation. */
    public static List<MessageKey> all() {
        return List.of(KICK_STARTING_UP, KICK_BOOTSTRAP_FAILED, KICK_SHUTTING_DOWN);
    }
}
