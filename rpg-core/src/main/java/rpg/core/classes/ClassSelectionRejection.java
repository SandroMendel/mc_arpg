package rpg.core.classes;

import rpg.core.message.MessageKey;

/**
 * Why a class selection was refused.
 *
 * <p>A reason travels as a value and carries its message key; the sentence is chosen where players
 * are actually addressed (Constitution V). Same shape as {@code PartyRejection} in B06.
 */
public enum ClassSelectionRejection {

    /** The account already has a character of that class - FR-036, decided by the unique index in B03. */
    CLASS_ALREADY_TAKEN(ClassMessageKeys.SELECTION_CLASS_TAKEN),

    /** Not one of the three known classes. Should be unreachable through the menu. */
    UNKNOWN_CLASS(ClassMessageKeys.SELECTION_UNKNOWN_CLASS),

    /** The session already has an active character; there is nothing to select. */
    ALREADY_HAS_CHARACTER(ClassMessageKeys.SELECTION_ALREADY_HAS_CHARACTER);

    private final MessageKey messageKey;

    ClassSelectionRejection(MessageKey messageKey) {
        this.messageKey = messageKey;
    }

    public MessageKey messageKey() {
        return messageKey;
    }
}
