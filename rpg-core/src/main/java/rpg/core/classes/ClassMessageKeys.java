package rpg.core.classes;

import java.util.List;

import rpg.core.message.MessageKey;

/**
 * Every string a player might see because of this block (Constitution V).
 *
 * <p>Keys only. B07 never formats a message and never decides wording. The display name of a class
 * is a key too - "Berserker" is a value in the message file, not in the class file, and the enum
 * constant stays {@code WARRIOR} (ADR-019).
 */
public final class ClassMessageKeys {

    /** Display names. The class file references these; it does not carry the text (ADR-019). */
    public static final MessageKey WARRIOR_NAME = MessageKey.of("class.warrior.name");
    public static final MessageKey MAGE_NAME = MessageKey.of("class.mage.name");
    public static final MessageKey ROGUE_NAME = MessageKey.of("class.rogue.name");

    /** Selection flow. */
    public static final MessageKey SELECTION_TITLE = MessageKey.of("class.selection.title");
    public static final MessageKey SELECTION_CHOSEN = MessageKey.of("class.selection.chosen");
    public static final MessageKey SELECTION_LOCKED = MessageKey.of("class.selection.locked");

    /** Selection rejections. A reason travels as a value; the sentence is chosen where players are addressed. */
    public static final MessageKey SELECTION_CLASS_TAKEN =
            MessageKey.of("class.selection.error.class-taken");
    public static final MessageKey SELECTION_UNKNOWN_CLASS =
            MessageKey.of("class.selection.error.unknown-class");
    public static final MessageKey SELECTION_ALREADY_HAS_CHARACTER =
            MessageKey.of("class.selection.error.already-has-character");

    /** Tier advance. */
    public static final MessageKey TIER_ADVANCED = MessageKey.of("class.tier.advanced");
    public static final MessageKey TIER_BELOW_REQUIRED_LEVEL =
            MessageKey.of("class.tier.error.below-required-level");
    public static final MessageKey TIER_ALREADY_AT_TOP =
            MessageKey.of("class.tier.error.already-at-top");
    public static final MessageKey TIER_UNKNOWN_CHARACTER =
            MessageKey.of("class.tier.error.unknown-character");

    /** Equipment lock. */
    public static final MessageKey EQUIPMENT_BOUND = MessageKey.of("class.equipment.bound");
    public static final MessageKey EQUIPMENT_DROP_DISABLED =
            MessageKey.of("class.equipment.drop-disabled");

    /**
     * Inventory is full and loot is arriving. There is no automatic cleanup, no background bank and
     * no silent discard - the player makes room (ADR-018). The drawing is B13's; until it exists this
     * key travels through the ordinary message path (ADR-005).
     */
    public static final MessageKey INVENTORY_FULL = MessageKey.of("class.inventory.full");

    private ClassMessageKeys() {}

    /** Every key this block can emit, for the resolution test in the plugin module. */
    public static List<MessageKey> all() {
        return List.of(
                WARRIOR_NAME,
                MAGE_NAME,
                ROGUE_NAME,
                SELECTION_TITLE,
                SELECTION_CHOSEN,
                SELECTION_LOCKED,
                SELECTION_CLASS_TAKEN,
                SELECTION_UNKNOWN_CLASS,
                SELECTION_ALREADY_HAS_CHARACTER,
                TIER_ADVANCED,
                TIER_BELOW_REQUIRED_LEVEL,
                TIER_ALREADY_AT_TOP,
                TIER_UNKNOWN_CHARACTER,
                EQUIPMENT_BOUND,
                EQUIPMENT_DROP_DISABLED,
                INVENTORY_FULL);
    }
}
