package rpg.core.classes;

import rpg.core.message.MessageKey;

/**
 * Why a tier advance was refused.
 *
 * <p>A reason travels as a value and carries its message key; the sentence is chosen where players are
 * addressed (Constitution V). Never an exception - a refused advance is an expected outcome, and
 * Constitution VI forbids letting a gameplay path throw into the caller.
 *
 * <p>There is deliberately no {@code CANNOT_AFFORD}: B07 does not interpret the cost block (FR-021).
 * The caller has already collected whatever B11 decided the advance costs.
 */
public enum TierAdvanceRejection {

    /** The character has not reached the level the next tier requires (FR-018). */
    BELOW_REQUIRED_LEVEL(ClassMessageKeys.TIER_BELOW_REQUIRED_LEVEL),

    /** Already on the last tier of that ladder (FR-020). */
    ALREADY_AT_TOP(ClassMessageKeys.TIER_ALREADY_AT_TOP),

    /** No character, or no class for it. */
    UNKNOWN_CHARACTER(ClassMessageKeys.TIER_UNKNOWN_CHARACTER);

    private final MessageKey messageKey;

    TierAdvanceRejection(MessageKey messageKey) {
        this.messageKey = messageKey;
    }

    public MessageKey messageKey() {
        return messageKey;
    }
}
