package rpg.core.ability;

import rpg.core.message.MessageKey;

/**
 * What came of raising an ability's rank (FR-062, FR-065).
 *
 * <p><b>Nobody pays for the advance, and that is on purpose.</b> The block brief speaks of buying
 * ranks with coins; there are no coins anywhere in this project - no currency, no balance, no
 * transaction. Building one here would be inventing an economy inside the ability block, and an
 * economy is not something a later block could then take over: it would already exist, in the wrong
 * place, with the wrong owner (Workflow rule 5).
 *
 * <p>So this enum has no {@code NOT_ENOUGH_COINS}. When a currency block arrives it adds the cost
 * check in front of {@link AbilityRuntime#advanceRank}, and the two outcomes below stay what they
 * are. A price that is missing is easier to add than a price that was guessed.
 */
public enum RankResult {

    /** Raised by one. The new rank is readable from the registry. */
    ADVANCED(null),

    /** Already at the ceiling the definition names; nothing changed. */
    AT_MAXIMUM(AbilityMessageKeys.RANK_AT_MAXIMUM),

    /** The character has not unlocked this ability, so there is no rank to raise. */
    NOT_UNLOCKED(AbilityMessageKeys.NOT_UNLOCKED);

    private final MessageKey messageKey;

    RankResult(MessageKey messageKey) {
        this.messageKey = messageKey;
    }

    public boolean isSuccess() {
        return this == ADVANCED;
    }

    /** What to tell the player, or {@code null} when there is nothing to say. */
    public MessageKey messageKey() {
        return messageKey;
    }
}
