package rpg.core.mob;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Wer weg soll: eine Zone ohne Spieler nach Ablauf der Frist (FR-019), und wer ausserhalb der
 * Reichweite jedes Spielers steht (FR-020).
 */
class CleanupRuleTest {

    private static final Duration CLEANUP_AFTER = Duration.ofSeconds(60);

    @Test
    @DisplayName("vor Ablauf der Frist ist eine leere Zone noch nicht verlassen")
    void beforeTheDeadlineAnEmptyZoneIsNotYetAbandoned() {
        assertThat(CleanupRule.zoneIsAbandoned(Duration.ofSeconds(59), CLEANUP_AFTER)).isFalse();
    }

    @Test
    @DisplayName("nach Ablauf der Frist gilt sie als verlassen")
    void afterTheDeadlineItIsAbandoned() {
        assertThat(CleanupRule.zoneIsAbandoned(Duration.ofSeconds(60), CLEANUP_AFTER)).isTrue();
        assertThat(CleanupRule.zoneIsAbandoned(Duration.ofSeconds(90), CLEANUP_AFTER)).isTrue();
    }

    @Test
    @DisplayName("eine verlassene Zone raeumt jede Kreatur, unabhaengig vom Chunk")
    void anAbandonedZoneClearsEveryCreatureRegardlessOfChunk() {
        NearbyChunks empty = new NearbyChunks();

        assertThat(CleanupRule.shouldRemove(true, NearbyChunks.pack(0, 0), empty, false)).isTrue();
    }

    @Test
    @DisplayName("in einer bevoelkerten Zone bleibt, wer in Reichweite steht")
    void inAPopulatedZoneWhoeverIsInRangeStays() {
        NearbyChunks nearby = new NearbyChunks();
        long chunk = NearbyChunks.pack(3, 4);
        nearby.stampAround(3 * 16 + 8, 4 * 16 + 8, 32.0);

        assertThat(CleanupRule.shouldRemove(false, chunk, nearby, false))
                .as("der Chunk liegt im gestempelten Umkreis")
                .isFalse();
    }

    @Test
    @DisplayName("wer weiter weg ist als jeder Spieler, wird entfernt")
    void whoeverIsFartherThanEveryPlayerIsRemoved() {
        NearbyChunks nearby = new NearbyChunks();
        nearby.stampAround(0, 0, 32.0);
        long farChunk = NearbyChunks.pack(500, 500);

        assertThat(CleanupRule.shouldRemove(false, farChunk, nearby, false)).isTrue();
    }
}
