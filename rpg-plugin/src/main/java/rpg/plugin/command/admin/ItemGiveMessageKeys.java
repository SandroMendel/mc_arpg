package rpg.plugin.command.admin;

import java.util.List;

import rpg.core.message.MessageKey;

/** Player-facing texts owned by {@code /rpg item give}. */
public final class ItemGiveMessageKeys {

    private ItemGiveMessageKeys() {}

    /** Description of the item administration branch. */
    public static final MessageKey DESCRIPTION = MessageKey.of("command.item.description");

    /** Description of the item grant leaf. */
    public static final MessageKey GIVE_DESCRIPTION =
            MessageKey.of("command.item.give.description");

    /** Confirmation when every item fit into the target inventory. */
    public static final MessageKey GIVEN = MessageKey.of("command.item.give.done");

    /** Confirmation when the B11 full-inventory rule left an owned world drop. */
    public static final MessageKey DROPPED = MessageKey.of("command.item.give.dropped");

    /** The target cannot receive an item without an online active character. */
    public static final MessageKey TARGET_UNAVAILABLE =
            MessageKey.of("command.item.give.target-unavailable");

    /** All texts that must be present before the plugin starts. */
    public static List<MessageKey> all() {
        return List.of(DESCRIPTION, GIVE_DESCRIPTION, GIVEN, DROPPED, TARGET_UNAVAILABLE);
    }
}
