package rpg.core.zone;

import rpg.core.message.MessageKey;

/**
 * What came of a journey (contracts/zone-api.md).
 *
 * <p><b>A return value, not an exception</b> - the same shape {@code BookingResult} has in B08b, and
 * for the same reason: a locked destination and an empty purse are ordinary outcomes of play. An
 * exception would have made the common case the expensive one and would have left the caller free to
 * ignore it.
 *
 * <p><b>{@link #TELEPORT_FAILED} is its own value rather than a variant of anything else.</b> It is
 * the outcome research.md R1 forced into the open: the fare was taken, the move did not happen, and
 * the fare came back. Folding it into {@link #NO_SUCH_CRYSTAL} would have hidden the one case an
 * operator needs to be able to count - a server that cannot move players is a server with a problem,
 * and the refund is the symptom.
 */
public enum TravelResult {

    /** Travelled. The fare was taken and the player is there. */
    OK(null),

    /** Visible in the window but not discovered yet (FR-049). Nothing was booked. */
    NOT_UNLOCKED(ZoneMessageKeys.WAYPOINT_LOCKED),

    /** The character counts as in combat (FR-051f). Nothing was booked. */
    IN_COMBAT(ZoneMessageKeys.IN_COMBAT),

    /**
     * The fare could not be taken, so nothing happened (FR-050a).
     *
     * <p>Covers every refusal the booking can give, not only an empty purse: each of them leaves the
     * balance untouched and the player where they stood, which is the only thing this outcome
     * promises. The distinction between "broke" and "not loaded" belongs to the ledger, not to a
     * player-facing message.
     */
    NOT_ENOUGH_COINS(ZoneMessageKeys.NOT_ENOUGH_COINS),

    /**
     * The crystal was gone by the time the click arrived.
     *
     * <p>Checked when clicked and not when the window opened: a reload can drop a region between the
     * two, and a stale window must not be able to send anybody nowhere.
     */
    NO_SUCH_CRYSTAL(ZoneMessageKeys.WAYPOINT_GONE),

    /** Taken, not travelled, given back (FR-050f). The balance is where it started. */
    TELEPORT_FAILED(ZoneMessageKeys.REFUNDED);

    private final MessageKey messageKey;

    TravelResult(MessageKey messageKey) {
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
