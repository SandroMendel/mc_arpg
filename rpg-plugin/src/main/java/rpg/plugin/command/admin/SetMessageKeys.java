package rpg.plugin.command.admin;

import java.util.List;

import rpg.core.message.MessageKey;

/** Player-facing texts owned by {@code /rpg set}. */
public final class SetMessageKeys {

    private SetMessageKeys() {}

    public static final MessageKey DESCRIPTION = MessageKey.of("command.set.description");
    public static final MessageKey LEVEL_DESCRIPTION =
            MessageKey.of("command.set.level.description");
    public static final MessageKey XP_DESCRIPTION = MessageKey.of("command.set.xp.description");
    public static final MessageKey CLASS_DESCRIPTION =
            MessageKey.of("command.set.class.description");
    public static final MessageKey DONE = MessageKey.of("command.set.done");
    public static final MessageKey NOTICE = MessageKey.of("command.set.notice");
    public static final MessageKey TARGET_UNAVAILABLE =
            MessageKey.of("command.set.target-unavailable");
    public static final MessageKey REJECTED = MessageKey.of("command.set.rejected");
    public static final MessageKey CLASS_DONE = MessageKey.of("command.set.class.done");
    public static final MessageKey CLASS_NOTICE = MessageKey.of("command.set.class.notice");
    public static final MessageKey CLASS_FAILED = MessageKey.of("command.set.class.failed");

    public static List<MessageKey> all() {
        return List.of(
                DESCRIPTION,
                LEVEL_DESCRIPTION,
                XP_DESCRIPTION,
                CLASS_DESCRIPTION,
                DONE,
                NOTICE,
                TARGET_UNAVAILABLE,
                REJECTED,
                CLASS_DONE,
                CLASS_NOTICE,
                CLASS_FAILED);
    }
}
