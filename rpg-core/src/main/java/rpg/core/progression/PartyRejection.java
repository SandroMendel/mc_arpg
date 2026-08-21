package rpg.core.progression;

/**
 * Why a party action did nothing.
 *
 * <p>An enum rather than a message, because B06 contains no player-facing text (FR-038). A reason
 * travels as a value and B14 decides the wording - which is also what makes it translatable later.
 */
public enum PartyRejection {

    /** Nothing was rejected. */
    NONE,

    /** The invited player is already in a party; one at a time (FR-032). */
    ALREADY_IN_PARTY,

    /** The party is at the configured maximum size (FR-033). */
    PARTY_FULL,

    /** Only the leader may invite or remove (FR-029b). */
    NOT_LEADER,

    /** The invitation is past its configured lifetime (FR-031). */
    INVITE_EXPIRED,

    /** There is no invitation for this player. */
    INVITE_UNKNOWN,

    /** The invited player has no ready session (FR-030). */
    TARGET_NOT_READY,

    /** A player cannot invite themselves. */
    SELF_INVITE,

    /** The player is not in the party the action refers to. */
    NOT_A_MEMBER
}
