package rpg.core.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * T014 / FR-014: a second session for the same player is rejected, and the first survives intact.
 *
 * <p>The survival part is the point. Replacing would drop the first session together with whatever
 * it had not written yet - at exactly the moment a player reconnects quickly, which is when this
 * block is under the most pressure.
 */
class SessionRegistryUniquenessTest {

    private static final Instant NOW = Instant.parse("2026-08-19T12:00:00Z");

    private static PlayerSession sessionFor(UUID playerId) {
        PlayerCharacter character = PlayerCharacter.create(playerId, CharacterClass.WARRIOR, NOW);
        return new PlayerSession(playerId, character, List.of(character));
    }

    @Test
    void aSecondSessionForTheSamePlayerIsRejected() {
        DefaultSessionRegistry registry = new DefaultSessionRegistry();
        UUID playerId = UUID.randomUUID();
        registry.open(sessionFor(playerId));

        assertThatThrownBy(() -> registry.open(sessionFor(playerId)))
                .isInstanceOf(DuplicateSessionException.class)
                .hasMessageContaining(playerId.toString());
    }

    @Test
    void theFirstSessionSurvivesTheRejectedSecond() {
        DefaultSessionRegistry registry = new DefaultSessionRegistry();
        UUID playerId = UUID.randomUUID();
        PlayerSession first = sessionFor(playerId);
        registry.open(first);
        first.transitionTo(SessionState.READY, NOW);

        try {
            registry.open(sessionFor(playerId));
        } catch (DuplicateSessionException expected) {
            // ignored on purpose - the assertion is about what survived
        }

        assertThat(registry.peek(playerId)).containsSame(first);
        assertThat(registry.require(playerId)).isSameAs(first);
        assertThat(registry.activeSessionCount()).isEqualTo(1);
    }

    @Test
    void differentPlayersEachGetTheirOwnSession() {
        DefaultSessionRegistry registry = new DefaultSessionRegistry();
        registry.open(sessionFor(UUID.randomUUID()));
        registry.open(sessionFor(UUID.randomUUID()));

        assertThat(registry.activeSessionCount()).isEqualTo(2);
    }

    @Test
    void aLoadingSessionIsReportedAsAbsentRatherThanAsDefaults() {
        // FR-004: "not ready" and "here are some values" must never look the same.
        DefaultSessionRegistry registry = new DefaultSessionRegistry();
        UUID playerId = UUID.randomUUID();
        registry.open(sessionFor(playerId));

        assertThat(registry.find(playerId)).isEmpty();
        assertThat(registry.isReady(playerId)).isFalse();
        assertThatThrownBy(() -> registry.require(playerId))
                .isInstanceOf(SessionNotReadyException.class);
    }

    @Test
    void aReadySessionIsFound() {
        DefaultSessionRegistry registry = new DefaultSessionRegistry();
        UUID playerId = UUID.randomUUID();
        PlayerSession session = sessionFor(playerId);
        registry.open(session);
        session.transitionTo(SessionState.READY, NOW);

        assertThat(registry.find(playerId)).containsSame(session);
        assertThat(registry.isReady(playerId)).isTrue();
    }

    @Test
    void anUnknownPlayerIsAbsentWithoutThrowingFromFind() {
        DefaultSessionRegistry registry = new DefaultSessionRegistry();

        assertThat(registry.find(UUID.randomUUID())).isEmpty();
        assertThatThrownBy(() -> registry.require(UUID.randomUUID()))
                .isInstanceOf(SessionNotReadyException.class);
    }

    @Test
    void reopeningAfterRemovalIsAllowed() {
        // The rejection is about concurrent sessions, not about ever connecting again.
        DefaultSessionRegistry registry = new DefaultSessionRegistry();
        UUID playerId = UUID.randomUUID();
        registry.open(sessionFor(playerId));
        registry.remove(playerId);

        registry.open(sessionFor(playerId));

        assertThat(registry.activeSessionCount()).isEqualTo(1);
    }

    @Test
    void orphanedSessionsAreThoseWithoutAConnectedPlayer() {
        DefaultSessionRegistry registry = new DefaultSessionRegistry();
        UUID connected = UUID.randomUUID();
        UUID gone = UUID.randomUUID();
        registry.open(sessionFor(connected));
        registry.open(sessionFor(gone));

        assertThat(registry.orphanedAgainst(List.of(connected)))
                .extracting(PlayerSession::playerId)
                .containsExactly(gone);
    }

    @Test
    void theRegistryInterfaceOffersNoWayToOpenOrRemoveASession() {
        // contracts/session-registry.md: a block that could open a session could open a second one.
        assertThat(SessionRegistry.class.getDeclaredMethods())
                .extracting(java.lang.reflect.Method::getName)
                .containsExactlyInAnyOrder("find", "require", "isReady", "activeSessionCount");
    }
}
