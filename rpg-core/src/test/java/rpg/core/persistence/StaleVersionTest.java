package rpg.core.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * T030 / FR-019b: the revision carried by player state, and what a stale write reports.
 */
class StaleVersionTest {

    private static final Instant NOW = Instant.parse("2026-08-19T12:00:00Z");

    @Test
    void aFreshStateStartsAtRevisionZero() {
        PlayerState state = PlayerState.initial(UUID.randomUUID(), NOW);

        assertThat(state.revision()).isZero();
        assertThat(state.dataVersion()).isEqualTo(PlayerState.CURRENT_DATA_VERSION);
        assertThat(state.anonymized()).isFalse();
    }

    @Test
    void everyWriteAdvancesTheRevision() {
        PlayerState state = PlayerState.initial(UUID.randomUUID(), NOW);

        PlayerState next = state.nextRevision(NOW.plusSeconds(1));

        assertThat(next.revision()).isEqualTo(1L);
        assertThat(next.playerId()).isEqualTo(state.playerId());
    }

    @Test
    void aStaleWriteReportsBothRevisionsSoTheConflictIsDiagnosable() {
        StaleVersionException thrown = new StaleVersionException("player-1", 3L, 7L);

        assertThat(thrown.expectedRevision()).isEqualTo(3L);
        assertThat(thrown.actualRevision()).isEqualTo(7L);
        assertThat(thrown).hasMessageContaining("player-1").hasMessageContaining("3")
                .hasMessageContaining("7");
    }

    @Test
    void aStaleVersionIsAPersistenceFailure() {
        // Callers that catch PersistenceException must also catch this one.
        assertThat(new StaleVersionException("p", 1L, 2L)).isInstanceOf(PersistenceException.class);
    }

    @Test
    void anonymizationSurvivesFurtherWrites() {
        PlayerState anonymized =
                new PlayerState(UUID.randomUUID(), 1, 5L, NOW, true);

        assertThat(anonymized.nextRevision(NOW.plusSeconds(1)).anonymized()).isTrue();
    }
}
