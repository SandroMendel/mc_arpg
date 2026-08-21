package rpg.core.progression;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import rpg.core.event.EventBus;
import rpg.core.session.SessionRegistry;

/**
 * Party membership, invitations and the leadership handover (FR-029 to FR-036).
 *
 * <p>Everything here is runtime state. There is no repository, no table and no migration - a restart
 * erases every party, and that is the promise.
 *
 * <p><b>No scheduled work.</b> An invitation expires because the clock says so when somebody asks,
 * not because a task woke up (FR-031, FR-061). An invitation nobody ever answers simply stops
 * mattering; it is evicted the next time that player is invited again or leaves.
 *
 * <p><b>No commands and no display.</b> B14 builds {@code /party invite} on this, B13 shows the
 * result (FR-037).
 */
public final class PartyRegistry {

    private record Invite(UUID from, UUID partyId, long sentAt) {}

    private final SessionRegistry sessions;
    private final EventBus events;
    private final Clock clock;
    private final int maxSize;
    private final long inviteTimeoutMillis;

    private final Map<UUID, Party> parties = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> partyOfPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, Invite> invites = new ConcurrentHashMap<>();

    public PartyRegistry(
            SessionRegistry sessions,
            EventBus events,
            Clock clock,
            int maxSize,
            Duration inviteTimeout) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.events = Objects.requireNonNull(events, "events");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.maxSize = maxSize;
        this.inviteTimeoutMillis = Objects.requireNonNull(inviteTimeout, "inviteTimeout").toMillis();
    }

    // --- founding, inviting, joining ------------------------------------------------------------

    /** Founds a party with the caller as its leader (FR-029a). */
    public PartyResult create(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        if (partyOfPlayer.containsKey(playerId)) {
            return PartyResult.rejected(PartyRejection.ALREADY_IN_PARTY);
        }
        Party party = new Party(UUID.randomUUID(), playerId, clock.millis(), maxSize);
        parties.put(party.partyId(), party);
        partyOfPlayer.put(playerId, party.partyId());
        publish(party, PartyChangedEvent.PartyChange.CREATED, playerId);
        return PartyResult.success(party.partyId());
    }

    /**
     * The leader invites another player (FR-029b, FR-030).
     *
     * <p>A player who is in no party founds one implicitly. Otherwise every flow in B14 would be two
     * commands, and somebody typing {@code /party invite} plainly wants a party.
     */
    public PartyResult invite(UUID leaderId, UUID targetId) {
        Objects.requireNonNull(leaderId, "leaderId");
        Objects.requireNonNull(targetId, "targetId");
        if (leaderId.equals(targetId)) {
            return PartyResult.rejected(PartyRejection.SELF_INVITE);
        }
        if (!sessions.isReady(targetId)) {
            return PartyResult.rejected(PartyRejection.TARGET_NOT_READY);
        }
        if (partyOfPlayer.containsKey(targetId)) {
            return PartyResult.rejected(PartyRejection.ALREADY_IN_PARTY);
        }

        Party party = partyOf0(leaderId);
        if (party == null) {
            PartyResult founded = create(leaderId);
            if (!founded.success()) {
                return founded;
            }
            party = parties.get(founded.partyId());
        } else if (!party.isLeader(leaderId)) {
            return PartyResult.rejected(PartyRejection.NOT_LEADER);
        }
        if (party.isFull()) {
            return PartyResult.rejected(PartyRejection.PARTY_FULL);
        }

        invites.put(targetId, new Invite(leaderId, party.partyId(), clock.millis()));
        return PartyResult.success(party.partyId());
    }

    /** The invited player accepts; membership starts here, not at the invitation (FR-030). */
    public PartyResult accept(UUID targetId) {
        Objects.requireNonNull(targetId, "targetId");
        Invite invite = invites.get(targetId);
        if (invite == null) {
            return PartyResult.rejected(PartyRejection.INVITE_UNKNOWN);
        }
        if (expired(invite)) {
            // Evicted here rather than by a task: the check is where the answer is needed.
            invites.remove(targetId);
            return PartyResult.rejected(PartyRejection.INVITE_EXPIRED);
        }
        if (partyOfPlayer.containsKey(targetId)) {
            return PartyResult.rejected(PartyRejection.ALREADY_IN_PARTY);
        }
        Party party = parties.get(invite.partyId());
        if (party == null) {
            // The party dissolved while the invitation sat unanswered.
            invites.remove(targetId);
            return PartyResult.rejected(PartyRejection.INVITE_UNKNOWN);
        }
        if (party.isFull()) {
            return PartyResult.rejected(PartyRejection.PARTY_FULL);
        }

        invites.remove(targetId);
        party.add(targetId, clock.millis());
        partyOfPlayer.put(targetId, party.partyId());
        publish(party, PartyChangedEvent.PartyChange.JOINED, targetId);
        return PartyResult.success(party.partyId());
    }

    /** The invited player declines. */
    public PartyResult decline(UUID targetId) {
        Objects.requireNonNull(targetId, "targetId");
        Invite invite = invites.remove(targetId);
        return invite == null
                ? PartyResult.rejected(PartyRejection.INVITE_UNKNOWN)
                : PartyResult.success(invite.partyId());
    }

    // --- leaving and removing ------------------------------------------------------------------

    /** Any member may leave at any time (FR-029b). */
    public PartyResult leave(UUID playerId) {
        return depart(playerId, PartyChangedEvent.PartyChange.LEFT);
    }

    /** Only the leader may remove somebody (FR-029b). */
    public PartyResult remove(UUID leaderId, UUID memberId) {
        Objects.requireNonNull(leaderId, "leaderId");
        Objects.requireNonNull(memberId, "memberId");
        Party party = partyOf0(leaderId);
        if (party == null) {
            return PartyResult.rejected(PartyRejection.NOT_A_MEMBER);
        }
        if (!party.isLeader(leaderId)) {
            return PartyResult.rejected(PartyRejection.NOT_LEADER);
        }
        if (!party.contains(memberId)) {
            return PartyResult.rejected(PartyRejection.NOT_A_MEMBER);
        }
        return depart(memberId, PartyChangedEvent.PartyChange.REMOVED);
    }

    /**
     * Called by B03 when a session ends (FR-034).
     *
     * <p>The only way a party shrinks through something other than a player's own action. It runs
     * the same transitions as {@link #leave}, handover included.
     */
    public void onSessionEnded(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        invites.remove(playerId);
        depart(playerId, PartyChangedEvent.PartyChange.LEFT);
    }

    private PartyResult depart(UUID playerId, PartyChangedEvent.PartyChange change) {
        Party party = partyOf0(playerId);
        if (party == null) {
            return PartyResult.rejected(PartyRejection.NOT_A_MEMBER);
        }
        UUID partyId = party.partyId();
        UUID newLeader = party.remove(playerId);
        partyOfPlayer.remove(playerId);
        publish(party, change, playerId);

        if (party.size() == 0) {
            // The last member left: the party stops existing, and no state stays behind (FR-035).
            parties.remove(partyId);
            events.publish(
                    new PartyChangedEvent(
                            partyId,
                            PartyChangedEvent.PartyChange.DISBANDED,
                            playerId,
                            playerId,
                            List.of()));
            return PartyResult.success(partyId);
        }
        if (newLeader != null) {
            publish(party, PartyChangedEvent.PartyChange.LEADER_CHANGED, newLeader);
        }
        return PartyResult.success(partyId);
    }

    // --- queries --------------------------------------------------------------------------------

    /** The party of a player, if they are in one. A copy, for display. */
    public Optional<PartyView> partyOf(UUID playerId) {
        Party party = partyOf0(playerId);
        return party == null
                ? Optional.empty()
                : Optional.of(
                        new PartyView(party.partyId(), party.leader(), party.memberList()));
    }

    /**
     * Writes the members of this player's party into {@code out} and returns how many; 0 when they
     * are in no party. Allocation-free - it runs on every kill.
     */
    public int membersOf(UUID playerId, UUID[] out) {
        Party party = partyOf0(playerId);
        return party == null ? 0 : party.copyMembersInto(out);
    }

    /** Whether two players share a party. */
    public boolean sameParty(UUID first, UUID second) {
        UUID a = partyOfPlayer.get(first);
        return a != null && a.equals(partyOfPlayer.get(second));
    }

    /** How many parties exist. For leak tests. */
    public int partyCount() {
        return parties.size();
    }

    /** How many invitations are held, expired ones included. For leak tests. */
    public int inviteCount() {
        return invites.size();
    }

    private Party partyOf0(UUID playerId) {
        UUID partyId = partyOfPlayer.get(playerId);
        return partyId == null ? null : parties.get(partyId);
    }

    private boolean expired(Invite invite) {
        return clock.millis() - invite.sentAt() >= inviteTimeoutMillis;
    }

    private void publish(Party party, PartyChangedEvent.PartyChange change, UUID affected) {
        events.publish(
                new PartyChangedEvent(
                        party.partyId(), change, affected, party.leader(), party.memberList()));
    }

    /** A party as B13 and B14 see it. */
    public record PartyView(UUID partyId, UUID leader, List<UUID> members) {}

    /** Outcome of a party action; a reason as a value, never a sentence (FR-038). */
    public record PartyResult(boolean success, PartyRejection rejection, UUID partyId) {

        static PartyResult success(UUID partyId) {
            return new PartyResult(true, PartyRejection.NONE, partyId);
        }

        static PartyResult rejected(PartyRejection rejection) {
            return new PartyResult(false, rejection, null);
        }
    }
}
