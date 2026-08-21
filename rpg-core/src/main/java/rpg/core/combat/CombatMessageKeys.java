package rpg.core.combat;

import java.util.List;

import rpg.core.message.MessageKey;

/**
 * The player-facing text of the combat block.
 *
 * <p>B05 had none until now: it computed damage and let the vanilla animation say so. These two lines
 * put numbers on it - one about the player, one about what they are hitting.
 *
 * <p>Text, colours and layout live in {@code messages.yml} like everything else (Constitution V), so a
 * server can reword or translate them without a build. The placeholders are the contract.
 */
public final class CombatMessageKeys {

    /**
     * The player's own health and defence, on the action bar.
     *
     * <p>Placeholders: {@code health}, {@code max}, {@code percent}, {@code defense}.
     */
    public static final MessageKey STATUS_ACTION_BAR = MessageKey.of("combat.status.action-bar");

    /**
     * What the player just hit, in chat.
     *
     * <p>Placeholders: {@code target}, {@code health}, {@code max}, {@code percent},
     * {@code defense}, {@code damage}, {@code hits}.
     */
    public static final MessageKey TARGET_REPORT = MessageKey.of("combat.target.report");

    /** The same, for a target that did not survive the window. */
    public static final MessageKey TARGET_SLAIN = MessageKey.of("combat.target.slain");

    private CombatMessageKeys() {}

    /** Every key this block can emit, for the resolution test in the plugin module. */
    public static List<MessageKey> all() {
        return List.of(STATUS_ACTION_BAR, TARGET_REPORT, TARGET_SLAIN);
    }
}
