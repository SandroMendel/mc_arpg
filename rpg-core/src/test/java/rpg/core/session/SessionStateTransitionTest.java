package rpg.core.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * T010 / data-model.md: only the declared transitions are possible, and the two that carry the
 * data-loss guarantee are pinned down explicitly.
 */
class SessionStateTransitionTest {

    private static final Instant NOW = Instant.parse("2026-08-19T12:00:00Z");

    private static PlayerSession session() {
        UUID playerId = UUID.randomUUID();
        PlayerCharacter character =
                PlayerCharacter.create(playerId, CharacterClass.WARRIOR, NOW);
        return new PlayerSession(playerId, character, List.of(character));
    }

    // --- the declared transitions ---

    @Test
    void loadingCanBecomeReady() {
        PlayerSession session = session();

        session.transitionTo(SessionState.READY, NOW);

        assertThat(session.state()).isEqualTo(SessionState.READY);
        assertThat(session.readyAt()).contains(NOW);
    }

    @Test
    void loadingCanFail() {
        PlayerSession session = session();

        session.transitionTo(SessionState.FAILED, NOW);

        assertThat(session.state()).isEqualTo(SessionState.FAILED);
    }

    @Test
    void loadingCanBeAbandonedWhenThePlayerDisconnects() {
        PlayerSession session = session();

        session.transitionTo(SessionState.UNLOADING, NOW);

        assertThat(session.state()).isEqualTo(SessionState.UNLOADING);
    }

    @Test
    void readyCanUnload() {
        PlayerSession session = session();
        session.transitionTo(SessionState.READY, NOW);

        assertThatCode(() -> session.transitionTo(SessionState.UNLOADING, NOW))
                .doesNotThrowAnyException();
    }

    // --- the two guarantees ---

    @Test
    void aFailedSessionMayNeverBeWritten() {
        // FR-012. A write from here would replace the player's real record with the nothing that a
        // failed load produced - the single worst outcome this block can cause.
        PlayerSession session = session();
        session.transitionTo(SessionState.FAILED, NOW);

        assertThat(session.mayBeWritten()).isFalse();
        assertThat(SessionState.FAILED.mayBeWritten()).isFalse();
    }

    @Test
    void aSessionStillLoadingMayNeverBeWritten() {
        // FR-015. The player never received this state, so there is nothing to save.
        PlayerSession session = session();

        assertThat(session.state()).isEqualTo(SessionState.LOADING);
        assertThat(session.mayBeWritten()).isFalse();
    }

    @Test
    void onlyReadyAndUnloadingMayBeWritten() {
        assertThat(SessionState.READY.mayBeWritten()).isTrue();
        assertThat(SessionState.UNLOADING.mayBeWritten()).isTrue();
        assertThat(SessionState.LOADING.mayBeWritten()).isFalse();
        assertThat(SessionState.FAILED.mayBeWritten()).isFalse();
    }

    // --- everything else is rejected ---

    @Test
    void aFailedSessionCannotBecomeReady() {
        PlayerSession session = session();
        session.transitionTo(SessionState.FAILED, NOW);

        assertThatThrownBy(() -> session.transitionTo(SessionState.READY, NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FAILED")
                .hasMessageContaining("READY");
    }

    @Test
    void anUnloadingSessionCannotBeRevived() {
        PlayerSession session = session();
        session.transitionTo(SessionState.READY, NOW);
        session.transitionTo(SessionState.UNLOADING, NOW);

        assertThatThrownBy(() -> session.transitionTo(SessionState.READY, NOW))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void readyCannotGoBackToLoading() {
        PlayerSession session = session();
        session.transitionTo(SessionState.READY, NOW);

        assertThatThrownBy(() -> session.transitionTo(SessionState.LOADING, NOW))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void bothTerminalStatesLeadNowhere() {
        assertThat(SessionState.FAILED.allowedTargets()).isEmpty();
        assertThat(SessionState.UNLOADING.allowedTargets()).isEmpty();
        assertThat(SessionState.FAILED.isTerminal()).isTrue();
        assertThat(SessionState.UNLOADING.isTerminal()).isTrue();
    }

    // --- queryability (FR-004) ---

    @Test
    void onlyAReadySessionIsQueryable() {
        assertThat(SessionState.READY.isQueryable()).isTrue();
        assertThat(SessionState.LOADING.isQueryable()).isFalse();
        assertThat(SessionState.UNLOADING.isQueryable()).isFalse();
        assertThat(SessionState.FAILED.isQueryable()).isFalse();
    }

    // --- the active character is fixed for the session's lifetime (FR-021b) ---

    @Test
    void thereIsNoWayToChangeTheActiveCharacter() {
        // The guarantee is the absence of a setter: allowing a swap would require the entire load
        // and unload path to work a second time for a connected player.
        assertThat(PlayerSession.class.getDeclaredMethods())
                .extracting(java.lang.reflect.Method::getName)
                .doesNotContain("setActiveCharacter", "switchCharacter", "changeCharacter");
    }

    @Test
    void aPlayerWithoutACharacterGetsASessionWithoutOne() {
        // FR-021: no character is invented for them.
        PlayerSession session = new PlayerSession(UUID.randomUUID(), null, List.of());

        assertThat(session.activeCharacter()).isEmpty();
        assertThat(session.availableCharacters()).isEmpty();
    }

    @Test
    void theActiveCharacterMustBelongToTheAccount() {
        UUID playerId = UUID.randomUUID();
        PlayerCharacter foreign =
                PlayerCharacter.create(UUID.randomUUID(), CharacterClass.MAGE, NOW);

        assertThatThrownBy(() -> new PlayerSession(playerId, foreign, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
