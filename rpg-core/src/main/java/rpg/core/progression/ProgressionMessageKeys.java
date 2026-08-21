package rpg.core.progression;

import java.util.List;

import rpg.core.message.MessageKey;

/**
 * Every string a player might see because of this block (FR-038).
 *
 * <p>Keys only. B06 never formats a message and never decides wording - B13 draws and B14 answers.
 * The party rejection reasons are an enum for the same reason: a reason travels as a value, and the
 * sentence is chosen where players are actually addressed.
 */
public final class ProgressionMessageKeys {

    public static final MessageKey LEVEL_UP = MessageKey.of("progression.level-up");
    public static final MessageKey MAX_LEVEL_REACHED = MessageKey.of("progression.max-level");

    public static final MessageKey PARTY_CREATED = MessageKey.of("progression.party.created");
    public static final MessageKey PARTY_INVITED = MessageKey.of("progression.party.invited");
    public static final MessageKey PARTY_JOINED = MessageKey.of("progression.party.joined");
    public static final MessageKey PARTY_LEFT = MessageKey.of("progression.party.left");
    public static final MessageKey PARTY_REMOVED = MessageKey.of("progression.party.removed");
    public static final MessageKey PARTY_DISBANDED = MessageKey.of("progression.party.disbanded");
    public static final MessageKey PARTY_LEADER_CHANGED =
            MessageKey.of("progression.party.leader-changed");

    public static final MessageKey PARTY_ALREADY_IN_PARTY =
            MessageKey.of("progression.party.error.already-in-party");
    public static final MessageKey PARTY_FULL = MessageKey.of("progression.party.error.full");
    public static final MessageKey PARTY_NOT_LEADER =
            MessageKey.of("progression.party.error.not-leader");
    public static final MessageKey PARTY_INVITE_EXPIRED =
            MessageKey.of("progression.party.error.invite-expired");
    public static final MessageKey PARTY_INVITE_UNKNOWN =
            MessageKey.of("progression.party.error.invite-unknown");
    public static final MessageKey PARTY_TARGET_NOT_READY =
            MessageKey.of("progression.party.error.target-not-ready");
    public static final MessageKey PARTY_SELF_INVITE =
            MessageKey.of("progression.party.error.self-invite");
    public static final MessageKey PARTY_NOT_A_MEMBER =
            MessageKey.of("progression.party.error.not-a-member");

    private ProgressionMessageKeys() {}

    public static List<MessageKey> all() {
        return List.of(
                LEVEL_UP,
                MAX_LEVEL_REACHED,
                PARTY_CREATED,
                PARTY_INVITED,
                PARTY_JOINED,
                PARTY_LEFT,
                PARTY_REMOVED,
                PARTY_DISBANDED,
                PARTY_LEADER_CHANGED,
                PARTY_ALREADY_IN_PARTY,
                PARTY_FULL,
                PARTY_NOT_LEADER,
                PARTY_INVITE_EXPIRED,
                PARTY_INVITE_UNKNOWN,
                PARTY_TARGET_NOT_READY,
                PARTY_SELF_INVITE,
                PARTY_NOT_A_MEMBER);
    }
}
