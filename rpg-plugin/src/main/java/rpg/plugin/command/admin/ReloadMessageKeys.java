package rpg.plugin.command.admin;

import java.util.List;

import rpg.core.message.MessageKey;

/** Player-facing texts owned by {@code /rpg reload}. */
public final class ReloadMessageKeys {

    private ReloadMessageKeys() {}

    /** Description shown by the server help. */
    public static final MessageKey DESCRIPTION = MessageKey.of("command.reload.description");

    /** Confirmation after all configuration sources and module hooks completed. */
    public static final MessageKey DONE = MessageKey.of("command.reload.done");

    /** Rejection with file, document path, expected value and actual reason. */
    public static final MessageKey REJECTED = MessageKey.of("command.reload.rejected");

    public static List<MessageKey> all() {
        return List.of(DESCRIPTION, DONE, REJECTED);
    }
}
