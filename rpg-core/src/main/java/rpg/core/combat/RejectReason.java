package rpg.core.combat;

/**
 * Why a damage event did not apply.
 *
 * <p>A reason rather than a silent {@code false}: someone who swings and sees nothing happen has to
 * be able to tell "clicked too early" from "PvP is off here" from "the session is still loading".
 * Without that distinction, all three arrive later as the same bug report.
 */
public enum RejectReason {

    /** The event applied. */
    NONE,

    /** Player against player, or mob against mob (FR-041, FR-042a). */
    NOT_PERMITTED,

    /** Inside the attacker's attack window; the swing is discarded, not weakened (FR-021). */
    ATTACK_TOO_SOON,

    /** The player's session is not released yet (FR-046). */
    SESSION_NOT_READY,

    /** Attacker or target has no stat holder - not part of this combat system (FR-018). */
    NO_HOLDER,

    /** The target is already dead; a second lethal hit does nothing (FR-026). */
    ALREADY_DEAD,

    /** An interception point cancelled the event (FR-009). */
    CANCELLED,

    /** Raw damage was negative or not finite (FR-006). */
    INVALID_DAMAGE
}
