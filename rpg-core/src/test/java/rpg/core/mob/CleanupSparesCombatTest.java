package rpg.core.mob;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * FR-022: eine Kreatur im Kampf mit einem Spieler wird nicht entfernt - weder weil sie zu weit weg
 * steht, noch weil ihre ganze Zone verlassen ist.
 */
class CleanupSparesCombatTest {

    @Test
    @DisplayName("im Kampf bleibt sie, obwohl sie ausserhalb jeder Reichweite steht")
    void staysInCombatEvenThoughOutOfEveryRange() {
        NearbyChunks farFromEveryone = new NearbyChunks();

        assertThat(
                        CleanupRule.shouldRemove(
                                false, NearbyChunks.pack(999, 999), farFromEveryone, true))
                .isFalse();
    }

    @Test
    @DisplayName("im Kampf bleibt sie, obwohl ihre ganze Zone als verlassen gilt")
    void staysInCombatEvenThoughTheWholeZoneIsAbandoned() {
        NearbyChunks empty = new NearbyChunks();

        assertThat(CleanupRule.shouldRemove(true, NearbyChunks.pack(0, 0), empty, true)).isFalse();
    }

    @Test
    @DisplayName("ohne Kampf gilt die normale Regel wieder")
    void withoutCombatTheOrdinaryRuleAppliesAgain() {
        NearbyChunks empty = new NearbyChunks();

        assertThat(CleanupRule.shouldRemove(false, NearbyChunks.pack(0, 0), empty, false)).isTrue();
    }
}
