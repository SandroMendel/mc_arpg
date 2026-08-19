package rpg.platform.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import rpg.core.session.CharacterClass;
import rpg.core.session.PlayerCharacter;
import rpg.core.session.PlayerSession;

/**
 * T029, T045: the handover between pre-login and join, and why its entries expire.
 *
 * <p>A login can pass the pre-login stage and never reach the world: another plugin refuses it, the
 * connection drops, the client gives up. The session loaded for that login has nowhere to go, and
 * without an expiry it would sit here for as long as the server runs. That is a leak measured in
 * weeks of uptime rather than minutes, which is exactly the kind this block is supposed to rule out.
 *
 * <p>Nothing is written when an entry expires. The player never entered the world, so there is no
 * state they ever received - writing one would replace their real record with a session they never
 * played.
 */
class PendingSessionStashTest {

    private static final Instant START = Instant.parse("2026-08-19T12:00:00Z");
    private static final Duration EXPIRY = Duration.ofSeconds(30);
    private static final Logger QUIET = Logger.getLogger("pending-session-stash-test");

    private MovableClock clock;
    private PendingSessionStash stash;

    @BeforeEach
    void setUp() {
        QUIET.setLevel(Level.OFF);
        clock = new MovableClock(START);
        stash = new PendingSessionStash(EXPIRY, clock, QUIET);
    }

    @Test
    void aStashedSessionIsHandedOverExactlyOnce() {
        UUID playerId = UUID.randomUUID();
        stash.put(session(playerId));

        assertThat(stash.take(playerId)).isPresent();
        // A second collection must find nothing: the session has moved on and may already have been
        // unloaded.
        assertThat(stash.take(playerId)).isEmpty();
        assertThat(stash.size()).isZero();
    }

    @Test
    void takingAnUnknownPlayerIsEmptyRatherThanAFailure() {
        assertThat(stash.take(UUID.randomUUID())).isEmpty();
    }

    @Test
    void discardingRemovesAnEntryWithoutCollectingIt() {
        UUID playerId = UUID.randomUUID();
        stash.put(session(playerId));

        stash.discard(playerId);

        assertThat(stash.size()).isZero();
        assertThat(stash.take(playerId)).isEmpty();
    }

    @Test
    void anEntryYoungerThanTheExpiryIsKept() {
        UUID playerId = UUID.randomUUID();
        stash.put(session(playerId));
        clock.advance(EXPIRY.minusSeconds(1));

        assertThat(stash.expireStale()).isZero();
        assertThat(stash.take(playerId)).isPresent();
    }

    @Test
    void anEntryOlderThanTheExpiryIsRemoved() {
        UUID playerId = UUID.randomUUID();
        stash.put(session(playerId));
        clock.advance(EXPIRY.plusSeconds(1));

        assertThat(stash.expireStale()).isEqualTo(1);
        assertThat(stash.size()).isZero();
    }

    @Test
    void onlyTheStaleEntriesGo() {
        UUID old = UUID.randomUUID();
        stash.put(session(old));
        clock.advance(EXPIRY.plusSeconds(1));
        UUID fresh = UUID.randomUUID();
        stash.put(session(fresh));

        assertThat(stash.expireStale()).isEqualTo(1);

        assertThat(stash.take(old)).isEmpty();
        assertThat(stash.take(fresh)).isPresent();
    }

    @Test
    void tenThousandUncollectedLoginsLeaveNothingBehindAfterOneSweep() {
        // The shape of the leak this guards against: individually harmless, cumulatively fatal.
        for (int i = 0; i < 10_000; i++) {
            stash.put(session(UUID.randomUUID()));
        }
        clock.advance(EXPIRY.plusSeconds(1));

        assertThat(stash.expireStale()).isEqualTo(10_000);
        assertThat(stash.size()).isZero();
        assertThat(stash.waiting()).isEmpty();
    }

    @Test
    void aSweepOverAnEmptyStashReportsNothing() {
        assertThat(stash.expireStale()).isZero();
    }

    private static PlayerSession session(UUID playerId) {
        PlayerCharacter character = PlayerCharacter.create(playerId, CharacterClass.WARRIOR, START);
        return new PlayerSession(playerId, character, List.of(character));
    }

    /** A clock the test moves by hand, so no test has to wait for real time to pass. */
    private static final class MovableClock extends Clock {

        private Instant now;

        MovableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
