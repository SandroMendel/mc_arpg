package rpg.plugin.command.admin;

import java.util.List;

import rpg.core.message.MessageKey;

/** Player-facing texts owned by {@code /rpg mob spawn}. */
public final class MobSpawnMessageKeys {

    private MobSpawnMessageKeys() {}

    public static final MessageKey DESCRIPTION = MessageKey.of("command.mob.description");
    public static final MessageKey SPAWN_DESCRIPTION = MessageKey.of("command.mob.spawn.description");
    public static final MessageKey DONE = MessageKey.of("command.mob.spawn.done");
    public static final MessageKey NO_ZONE = MessageKey.of("command.mob.spawn.no-zone");
    public static final MessageKey LIMIT = MessageKey.of("command.mob.spawn.limit");
    public static final MessageKey FAILED = MessageKey.of("command.mob.spawn.failed");

    public static List<MessageKey> all() {
        return List.of(DESCRIPTION, SPAWN_DESCRIPTION, DONE, NO_ZONE, LIMIT, FAILED);
    }
}
