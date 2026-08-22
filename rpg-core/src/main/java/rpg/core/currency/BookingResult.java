package rpg.core.currency;

import rpg.core.message.MessageKey;

/**
 * What came of a booking (FR-008).
 *
 * <p><b>A return value, not an exception.</b> Not having enough coins is an ordinary outcome of
 * play, and an exception would have made the common case the expensive one. An invalid amount is a
 * caller error and still comes back as a value: Constitution VI forbids leaving a player in an
 * inconsistent state, and a throw halfway through a gameplay path is exactly how that happens.
 *
 * <p>Every outcome that is not {@link #OK} carries the key of the text the player sees. The block
 * never decides wording (Constitution V).
 *
 * <p>Same shape as {@code RankResult} and {@code AbilityResult}, deliberately: three enums that
 * answer "did it work, and what do I tell them" should not each answer it differently.
 */
public enum BookingResult {

    /** Booked. The balance changed and the ledger has the entry. */
    OK(null),

    /**
     * Not enough coins. The balance is unchanged.
     *
     * <p>It is <b>not</b> capped to zero - a silent cap is a gift nobody notices, and the player
     * would be left believing they spent something they still have (FR-004).
     */
    NOT_ENOUGH(CurrencyMessageKeys.NOT_ENOUGH),

    /**
     * Zero or a negative amount. A caller error, not a game outcome.
     *
     * <p>Direction comes from the method - credit or debit - never from the sign of the amount
     * (FR-009). A negative credit and a positive debit would be two ways of writing the same thing,
     * and one of them would eventually be written by mistake.
     */
    INVALID_AMOUNT(CurrencyMessageKeys.INVALID_AMOUNT),

    /** The credit would leave the representable range. Refused rather than wrapped (FR-010). */
    WOULD_OVERFLOW(CurrencyMessageKeys.WOULD_OVERFLOW),

    /** No such character. Nothing is created on the way past (FR-044). */
    NO_SUCH_CHARACTER(CurrencyMessageKeys.NO_SUCH_CHARACTER);

    private final MessageKey messageKey;

    BookingResult(MessageKey messageKey) {
        this.messageKey = messageKey;
    }

    public boolean isSuccess() {
        return this == OK;
    }

    /** What to tell the player, or {@code null} when there is nothing to say. */
    public MessageKey messageKey() {
        return messageKey;
    }
}
