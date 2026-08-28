package rpg.core.currency;

import java.util.List;

import rpg.core.message.MessageKey;

/**
 * Every string a player or an operator might see because of this block (Constitution V).
 *
 * <p>Keys only. This block never formats a message and never decides wording - the text behind a
 * key lives in the message file, which is what keeps translation a configuration change rather than
 * a code change (ADR-005).
 *
 * <p>{@link #all()} exists so the startup validator can prove that every key declared here has a
 * text behind it. A key without a text is a blank message at the worst possible moment; the
 * bootstrap refuses to start instead.
 */
public final class CurrencyMessageKeys {

    private CurrencyMessageKeys() {}

    /** A booking was refused. One key per {@link BookingResult} that is not a success. */
    public static final MessageKey NOT_ENOUGH = MessageKey.of("currency.rejected.not-enough");

    public static final MessageKey INVALID_AMOUNT =
            MessageKey.of("currency.rejected.invalid-amount");

    public static final MessageKey WOULD_OVERFLOW = MessageKey.of("currency.rejected.overflow");

    public static final MessageKey NO_SUCH_CHARACTER =
            MessageKey.of("currency.rejected.no-character");

    /** Coins arriving. */
    public static final MessageKey PILE_PICKED_UP = MessageKey.of("currency.pile.picked-up");

    /**
     * The pile cap bit and an older pile was credited instead of dropped (FR-030a).
     *
     * <p>Its own key rather than reusing {@link #PILE_PICKED_UP}: the player did not pick anything
     * up, and telling them they did would be a small lie that makes the ledger look wrong.
     */
    public static final MessageKey PILE_CASHED_IN = MessageKey.of("currency.pile.cashed-in");

    public static final MessageKey STARTING_BALANCE = MessageKey.of("currency.starting-balance");

    /** Reading a balance. */
    public static final MessageKey BALANCE_CURRENT = MessageKey.of("currency.balance.current");

    /** The operator's intervention. */
    public static final MessageKey ADMIN_APPLIED = MessageKey.of("currency.admin.applied");

    public static final MessageKey ADMIN_DENIED = MessageKey.of("currency.admin.denied");

    public static final MessageKey ADMIN_UNKNOWN_CHARACTER =
            MessageKey.of("currency.admin.unknown-character");

    /** The window (ADR-028). Provisional, like the window itself - B13 will own this wording. */
    public static final MessageKey MENU_TITLE_CHARACTERS =
            MessageKey.of("currency.menu.title-characters");

    public static final MessageKey MENU_TITLE_HISTORY =
            MessageKey.of("currency.menu.title-history");

    public static final MessageKey MENU_CHARACTER_ENTRY =
            MessageKey.of("currency.menu.character-entry");

    public static final MessageKey MENU_HISTORY_ENTRY =
            MessageKey.of("currency.menu.history-entry");

    public static final MessageKey MENU_PAGE_NEXT = MessageKey.of("currency.menu.page-next");

    public static final MessageKey MENU_PAGE_PREVIOUS =
            MessageKey.of("currency.menu.page-previous");

    public static final MessageKey MENU_EMPTY = MessageKey.of("currency.menu.empty");

    /** Every key this block declares, for the startup validation of the message file. */
    public static List<MessageKey> all() {
        return List.of(
                NOT_ENOUGH,
                INVALID_AMOUNT,
                WOULD_OVERFLOW,
                NO_SUCH_CHARACTER,
                PILE_PICKED_UP,
                PILE_CASHED_IN,
                STARTING_BALANCE,
                BALANCE_CURRENT,
                ADMIN_APPLIED,
                ADMIN_DENIED,
                ADMIN_UNKNOWN_CHARACTER,
                MENU_TITLE_CHARACTERS,
                MENU_TITLE_HISTORY,
                MENU_CHARACTER_ENTRY,
                MENU_HISTORY_ENTRY,
                MENU_PAGE_NEXT,
                MENU_PAGE_PREVIOUS,
                MENU_EMPTY);
    }
}
