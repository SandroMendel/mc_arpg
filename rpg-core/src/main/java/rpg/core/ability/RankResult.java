package rpg.core.ability;

import rpg.core.message.MessageKey;

/**
 * What came of raising an ability's rank (FR-062, FR-065).
 *
 * <p><b>A rank costs coins</b> - since B08b exists (ADR-027, FR-051). Until then this enum carried a
 * paragraph explaining that nobody paid for an advance because there was no currency anywhere in the
 * project, and that inventing one inside the ability block would have put an economy in the wrong
 * place with the wrong owner (Workflow rule 5).
 *
 * <p>That reasoning was right and it is worth keeping the outcome of it: the price was never guessed
 * here. It came from the block that owns currency, and this enum grew by exactly one value when it
 * arrived. A price that is missing is easier to add than a price that was invented.
 *
 * <p><b>Where the check sits.</b> In front of {@link AbilityRuntime#advanceRank}, and <em>last</em>:
 * unlock and maximum rank are settled before a single coin moves, so an advance refused for any
 * other reason leaves the balance untouched (FR-052). What a rank costs lives in the ability
 * configuration and is read by B08b - this block still knows nothing about coins beyond the value
 * below.
 */
public enum RankResult {

    /** Raised by one. The new rank is readable from the registry. */
    ADVANCED(null),

    /** Already at the ceiling the definition names; nothing changed, and nothing was charged. */
    AT_MAXIMUM(AbilityMessageKeys.RANK_AT_MAXIMUM),

    /** The character has not unlocked this ability, so there is no rank to raise. */
    NOT_UNLOCKED(AbilityMessageKeys.NOT_UNLOCKED),

    /**
     * Everything else was in order; the coins were not there (FR-051).
     *
     * <p>The rank is unchanged and nothing was taken - a player charged for something that did not
     * happen is the worst outcome available here.
     */
    NOT_ENOUGH_COINS(AbilityMessageKeys.RANK_NOT_ENOUGH_COINS);

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
