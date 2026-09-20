package rpg.plugin.command.admin;

import java.util.List;

import rpg.core.message.MessageKey;

/** Message keys for the read-only inspection views (US7). */
public final class InspectMessageKeys {

    private InspectMessageKeys() {}

    public static final MessageKey DESCRIPTION =
            MessageKey.of("command.inspect.description");
    public static final MessageKey SHEET_DESCRIPTION =
            MessageKey.of("command.inspect.sheet.description");
    public static final MessageKey STATISTICS_DESCRIPTION =
            MessageKey.of("command.inspect.statistics.description");
    public static final MessageKey INVENTORY_DESCRIPTION =
            MessageKey.of("command.inspect.inventory.description");
    public static final MessageKey SESSION_DESCRIPTION =
            MessageKey.of("command.inspect.session.description");

    public static final MessageKey SHEET_TITLE = MessageKey.of("command.inspect.sheet.title");
    public static final MessageKey STATISTICS_TITLE =
            MessageKey.of("command.inspect.statistics.title");
    public static final MessageKey INVENTORY_TITLE =
            MessageKey.of("command.inspect.inventory.title");
    public static final MessageKey SESSION_TITLE =
            MessageKey.of("command.inspect.session.title");
    public static final MessageKey LINE = MessageKey.of("command.inspect.line");
    public static final MessageKey ANONYMOUS = MessageKey.of("command.inspect.anonymous");
    public static final MessageKey NO_DATA = MessageKey.of("command.inspect.no-data");
    public static final MessageKey FAILED = MessageKey.of("command.inspect.failed");

    public static final MessageKey SHEET_CLASS =
            MessageKey.of("command.inspect.sheet.class");
    public static final MessageKey SHEET_LEVEL =
            MessageKey.of("command.inspect.sheet.level");
    public static final MessageKey SHEET_XP = MessageKey.of("command.inspect.sheet.xp");
    public static final MessageKey SHEET_COINS =
            MessageKey.of("command.inspect.sheet.coins");
    public static final MessageKey SHEET_REVISION =
            MessageKey.of("command.inspect.sheet.revision");
    public static final MessageKey SHEET_EQUIPMENT =
            MessageKey.of("command.inspect.sheet.equipment");

    public static final MessageKey INVENTORY_BACKPACK =
            MessageKey.of("command.inspect.inventory.backpack");
    public static final MessageKey INVENTORY_ENDER_CHEST =
            MessageKey.of("command.inspect.inventory.ender-chest");
    public static final MessageKey INVENTORY_COUNT =
            MessageKey.of("command.inspect.inventory.count");
    public static final MessageKey INVENTORY_UNREADABLE =
            MessageKey.of("command.inspect.inventory.unreadable");

    public static final MessageKey SESSION_STATE =
            MessageKey.of("command.inspect.session.state");
    public static final MessageKey SESSION_ONLINE =
            MessageKey.of("command.inspect.session.online");
    public static final MessageKey SESSION_CHARACTER =
            MessageKey.of("command.inspect.session.character");
    public static final MessageKey SESSION_LAST_SEEN =
            MessageKey.of("command.inspect.session.last-seen");

    /** Every fixed key must be present before the server starts. */
    public static List<MessageKey> all() {
        return List.of(
                DESCRIPTION,
                SHEET_DESCRIPTION,
                STATISTICS_DESCRIPTION,
                INVENTORY_DESCRIPTION,
                SESSION_DESCRIPTION,
                SHEET_TITLE,
                STATISTICS_TITLE,
                INVENTORY_TITLE,
                SESSION_TITLE,
                LINE,
                ANONYMOUS,
                NO_DATA,
                FAILED,
                SHEET_CLASS,
                SHEET_LEVEL,
                SHEET_XP,
                SHEET_COINS,
                SHEET_REVISION,
                SHEET_EQUIPMENT,
                INVENTORY_BACKPACK,
                INVENTORY_ENDER_CHEST,
                INVENTORY_COUNT,
                INVENTORY_UNREADABLE,
                SESSION_STATE,
                SESSION_ONLINE,
                SESSION_CHARACTER,
                SESSION_LAST_SEEN);
    }
}
