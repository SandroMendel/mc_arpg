package rpg.core.progression;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.event.DefaultEventBus;
import rpg.core.event.EventBus;

/**
 * Party membership as pure state transitions (FR-029 to FR-036).
 *
 * <p>No server, no database, and a steered clock - invite expiry is timestamp based, so a test that
 * really waited sixty seconds would be both slow and flaky.
 */
class PartyRegistryTest {

    private ProgressionFixture.TestClock clock;
    private ProgressionFixture.TestSessions sessions;
    private ProgressionFixture.CountingScheduler scheduler;
    private PartyRegistry parties;
    private List<PartyChangedEvent> events;

    @BeforeEach
    void setUp() {
        Logger logger = Logger.getLogger(PartyRegistryTest.class.getName());
        logger.setLevel(Level.OFF);
        clock = new ProgressionFixture.TestClock();
        sessions = new ProgressionFixture.TestSessions();
        scheduler = new ProgressionFixture.CountingScheduler();
        EventBus bus = new DefaultEventBus(logger);
        events = new ArrayList<>();
        bus.subscribe(PartyChangedEvent.class, events::add);
        parties = new PartyRegistry(sessions, bus, clock, 5, Duration.ofSeconds(60));
    }

    private UUID player() {
        UUID id = UUID.randomUUID();
        sessions.markReady(id);
        return id;
    }

    @Test
    @DisplayName("invitation plus acceptance makes two players one party")
    void inviteAndAccept() {
        UUID leader = player();
        UUID guest = player();

        assertThat(parties.invite(leader, guest).success()).isTrue();
        assertThat(parties.accept(guest).success()).isTrue();

        assertThat(parties.sameParty(leader, guest)).isTrue();
        assertThat(parties.partyOf(leader).orElseThrow().members()).containsExactly(leader, guest);
        assertThat(parties.partyOf(leader).orElseThrow().leader()).isEqualTo(leader);
    }

    @Test
    @DisplayName("inviting without a party founds one, so B14 needs one command and not two")
    void inviteFoundsImplicitly() {
        UUID leader = player();
        UUID guest = player();

        parties.invite(leader, guest);

        assertThat(parties.partyOf(leader)).isPresent();
        assertThat(events)
                .extracting(PartyChangedEvent::change)
                .containsExactly(PartyChangedEvent.PartyChange.CREATED);
    }

    @Test
    @DisplayName("an invitation past its lifetime is refused, and no task was involved")
    void inviteExpiresLazily() {
        UUID leader = player();
        UUID guest = player();
        parties.invite(leader, guest);

        clock.advance(Duration.ofSeconds(61));

        assertThat(parties.accept(guest).rejection()).isEqualTo(PartyRejection.INVITE_EXPIRED);
        assertThat(scheduler.scheduled).as("FR-031, FR-061: checked when asked").isZero();
        assertThat(parties.inviteCount()).as("evicted where the answer was needed").isZero();
    }

    @Test
    @DisplayName("one second before the deadline it still works")
    void inviteJustInTime() {
        UUID leader = player();
        UUID guest = player();
        parties.invite(leader, guest);

        clock.advance(Duration.ofSeconds(59));

        assertThat(parties.accept(guest).success()).isTrue();
    }

    @Test
    @DisplayName("a player already in a party cannot accept a second invitation")
    void alreadyInParty() {
        UUID first = player();
        UUID second = player();
        UUID guest = player();
        parties.invite(first, guest);
        parties.accept(guest);

        assertThat(parties.invite(second, guest).rejection())
                .isEqualTo(PartyRejection.ALREADY_IN_PARTY);
    }

    @Test
    @DisplayName("a full party refuses another member")
    void partyFull() {
        UUID leader = player();
        for (int i = 0; i < 4; i++) {
            UUID guest = player();
            parties.invite(leader, guest);
            parties.accept(guest);
        }
        assertThat(parties.partyOf(leader).orElseThrow().members()).hasSize(5);

        UUID sixth = player();
        assertThat(parties.invite(leader, sixth).rejection()).isEqualTo(PartyRejection.PARTY_FULL);
    }

    @Test
    @DisplayName("a member without the leader role may neither invite nor remove")
    void notLeader() {
        UUID leader = player();
        UUID member = player();
        UUID outsider = player();
        parties.invite(leader, member);
        parties.accept(member);

        assertThat(parties.invite(member, outsider).rejection())
                .isEqualTo(PartyRejection.NOT_LEADER);
        assertThat(parties.remove(member, leader).rejection()).isEqualTo(PartyRejection.NOT_LEADER);
    }

    @Test
    @DisplayName("inviting yourself and inviting somebody not ready are both refused")
    void selfInviteAndNotReady() {
        UUID leader = player();
        UUID offline = UUID.randomUUID();

        assertThat(parties.invite(leader, leader).rejection())
                .isEqualTo(PartyRejection.SELF_INVITE);
        assertThat(parties.invite(leader, offline).rejection())
                .isEqualTo(PartyRejection.TARGET_NOT_READY);
    }

    @Test
    @DisplayName("when the leader drops, the longest-serving member takes over")
    void leadershipGoesToTheLongestServing() {
        UUID leader = player();
        UUID second = player();
        UUID third = player();
        parties.invite(leader, second);
        parties.accept(second);
        clock.advance(Duration.ofSeconds(5));
        parties.invite(leader, third);
        parties.accept(third);
        events.clear();

        parties.onSessionEnded(leader);

        PartyRegistry.PartyView view = parties.partyOf(second).orElseThrow();
        assertThat(view.leader()).as("second joined before third").isEqualTo(second);
        assertThat(view.members()).containsExactly(second, third);
    }

    @Test
    @DisplayName("a handover publishes two events, not one with two meanings")
    void handoverPublishesTwoEvents() {
        UUID leader = player();
        UUID second = player();
        parties.invite(leader, second);
        parties.accept(second);
        events.clear();

        parties.onSessionEnded(leader);

        assertThat(events)
                .extracting(PartyChangedEvent::change)
                .containsExactly(
                        PartyChangedEvent.PartyChange.LEFT,
                        PartyChangedEvent.PartyChange.LEADER_CHANGED);
        assertThat(events.get(1).leader()).isEqualTo(second);
    }

    @Test
    @DisplayName("the party is never without a leader, at any point of the handover")
    void neverWithoutALeader() {
        UUID leader = player();
        UUID second = player();
        UUID third = player();
        parties.invite(leader, second);
        parties.accept(second);
        parties.invite(leader, third);
        parties.accept(third);

        parties.onSessionEnded(leader);
        assertThat(parties.partyOf(second).orElseThrow().leader()).isNotNull();
        parties.onSessionEnded(second);
        assertThat(parties.partyOf(third).orElseThrow().leader()).isEqualTo(third);
    }

    @Test
    @DisplayName("when the last member leaves, the party stops existing")
    void lastMemberDisbands() {
        UUID leader = player();
        UUID second = player();
        parties.invite(leader, second);
        parties.accept(second);

        parties.leave(second);
        assertThat(parties.partyCount()).as("a party of one is legal").isEqualTo(1);
        parties.leave(leader);

        assertThat(parties.partyCount()).isZero();
        assertThat(parties.partyOf(leader)).isEmpty();
        assertThat(events)
                .extracting(PartyChangedEvent::change)
                .contains(PartyChangedEvent.PartyChange.DISBANDED);
    }

    @Test
    @DisplayName("disbanding still names the last leader, so B13 can close the right display")
    void disbandNamesTheLastLeader() {
        UUID leader = player();
        parties.create(leader);
        events.clear();

        parties.leave(leader);

        PartyChangedEvent disbanded =
                events.stream()
                        .filter(e -> e.change() == PartyChangedEvent.PartyChange.DISBANDED)
                        .findFirst()
                        .orElseThrow();
        assertThat(disbanded.leader()).isEqualTo(leader);
        assertThat(disbanded.members()).isEmpty();
    }

    @Test
    @DisplayName("a party of one behaves exactly like no party at all")
    void partyOfOne() {
        UUID solo = player();
        parties.create(solo);

        UUID[] out = new UUID[5];
        assertThat(parties.membersOf(solo, out)).isEqualTo(1);
        assertThat(out[0]).isEqualTo(solo);
        assertThat(parties.sameParty(solo, solo)).isTrue();
    }

    @Test
    @DisplayName("the member array must be big enough, rather than truncating silently")
    void memberArrayTooSmall() {
        UUID leader = player();
        UUID second = player();
        parties.invite(leader, second);
        parties.accept(second);

        // Truncating in the combat path would hand some members no experience and leave no trace.
        assertThatThrownBy(() -> parties.membersOf(leader, new UUID[1]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("party.max-size");
    }

    @Test
    @DisplayName("nothing is scheduled, whatever happens")
    void noScheduledWork() {
        UUID leader = player();
        for (int i = 0; i < 20; i++) {
            UUID guest = player();
            parties.invite(leader, guest);
            parties.accept(guest);
            parties.leave(guest);
        }

        assertThat(scheduler.scheduled).isZero();
    }

    @Test
    @DisplayName("declining removes the invitation and joins nothing")
    void decline() {
        UUID leader = player();
        UUID guest = player();
        parties.invite(leader, guest);

        assertThat(parties.decline(guest).success()).isTrue();
        assertThat(parties.inviteCount()).isZero();
        assertThat(parties.sameParty(leader, guest)).isFalse();
        assertThat(parties.decline(guest).rejection()).isEqualTo(PartyRejection.INVITE_UNKNOWN);
    }

    @Test
    @DisplayName("a party never survives a restart, because it was never stored")
    void nothingIsPersisted() {
        UUID leader = player();
        UUID second = player();
        parties.invite(leader, second);
        parties.accept(second);
        assertThat(parties.partyCount()).isEqualTo(1);

        // A restart is a new registry - there is no repository to load from, by design (FR-029).
        PartyRegistry afterRestart =
                new PartyRegistry(
                        sessions,
                        new DefaultEventBus(Logger.getLogger("quiet")),
                        clock,
                        5,
                        Duration.ofSeconds(60));

        assertThat(afterRestart.partyCount()).isZero();
        assertThat(afterRestart.partyOf(leader)).isEmpty();
    }
}
