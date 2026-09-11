package rpg.core.statistics;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * FR-034, FR-030 — <b>Gleichstand teilt den Platz, und die Reihenfolge bleibt stabil.</b>
 *
 * <p>Die Stabilität ist die unauffälligere der beiden Zusagen und die wichtigere. Wäre die
 * Reihenfolge bei Gleichstand die einer Hash-Map, tauschten zwei Spieler bei jeder Auffrischung
 * die Plätze, ohne dass sich an ihren Werten etwas geändert hätte. Beide sähen, wie sie
 * abwechselnd steigen und fallen; beide hielten die Rangliste für kaputt, und sie hätten recht.
 */
class LeaderboardCacheTest {

    private static final Instant AT = Instant.parse("2026-08-30T10:00:00Z");

    @Test
    @DisplayName("FR-034 - gleiche Werte tragen denselben Platz, der naechste ueberspringt")
    void equalValuesShareARankAndTheNextOneSkips() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID third = UUID.randomUUID();
        UUID fourth = UUID.randomUUID();

        Map<UUID, Long> values = new LinkedHashMap<>();
        values.put(first, 100L);
        values.put(second, 50L);
        values.put(third, 50L);
        values.put(fourth, 10L);

        Leaderboard board = board(values, 10);

        assertThat(board.top()).extracting(LeaderboardEntry::rank).containsExactly(1, 2, 2, 4);
    }

    @Test
    @DisplayName("FR-034 - die Reihenfolge im Gleichstand ist ueber Aufrufe hinweg dieselbe")
    void theOrderWithinATieIsStableAcrossCalls() {
        Map<UUID, Long> values = new LinkedHashMap<>();
        for (int i = 0; i < 12; i++) {
            values.put(UUID.randomUUID(), 50L);
        }

        List<UUID> firstRun = board(values, 12).top().stream().map(LeaderboardEntry::playerId).toList();
        List<UUID> secondRun =
                board(shuffled(values), 12).top().stream().map(LeaderboardEntry::playerId).toList();

        assertThat(secondRun)
                .as("dieselben Werte, andere Einfuegereihenfolge - dieselbe Rangliste")
                .containsExactlyElementsOf(firstRun);
    }

    @Test
    @DisplayName("FR-033 - die eigene Platzierung steht auch ausserhalb der ersten N fest")
    void theOwnRankIsKnownEvenOutsideTheTop() {
        Map<UUID, Long> values = new LinkedHashMap<>();
        UUID far = UUID.randomUUID();
        for (int i = 0; i < 30; i++) {
            values.put(UUID.randomUUID(), 1000L - i);
        }
        values.put(far, 1L);

        Leaderboard board = board(values, 10);

        assertThat(board.top()).hasSize(10);
        assertThat(board.entryFor(far)).isEmpty();
        assertThat(board.rankFor(far).getAsInt())
                .as("ohne die vollstaendige Rangzuordnung waere das eine Abfrage beim Oeffnen")
                .isEqualTo(31);
    }

    @Test
    @DisplayName("wer keinen Wert hat, hat keinen Platz - und das ist kein Fehler")
    void someoneWithoutAValueHasNoRank() {
        assertThat(board(Map.of(UUID.randomUUID(), 5L), 10).rankFor(UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("FR-030 - der Stand wird im Ganzen ausgetauscht, nie halb gefuellt")
    void thecacheIsReplacedWholeSale() {
        LeaderboardCache cache = LeaderboardCache.empty();
        LeaderboardCache.Key key =
                LeaderboardCache.Key.of(Aggregation.MOB_KILLS, Period.ALL_TIME);

        assertThat(cache.isReady()).isFalse();
        assertThat(cache.board(key)).isEmpty();

        cache.replace(Map.of(key, board(Map.of(UUID.randomUUID(), 5L), 10)), AT);

        assertThat(cache.isReady()).isTrue();
        assertThat(cache.refreshedAt()).contains(AT);
        assertThat(cache.board(key)).isPresent();
        assertThat(cache.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("FR-035 - vor der ersten Auffrischung ist der Stand leer, nicht falsch")
    void beforeTheFirstRefreshTheCacheIsEmptyRatherThanWrong() {
        LeaderboardCache cache = LeaderboardCache.empty();

        // Wichtig ist der Unterschied zwischen "noch nicht da" und "leer": das eine ist ein
        // Hinweis an den Spieler, das andere waere die Behauptung, niemand habe je etwas getan.
        assertThat(cache.isReady()).isFalse();
        assertThat(cache.refreshedAt()).isEmpty();
    }

    @Test
    @DisplayName("eine leere Rangliste ist eine Rangliste, keine fehlende")
    void anemptyBoardIsStillABoard() {
        Leaderboard empty =
                Leaderboard.empty(Aggregation.DAMAGE_MAX, Period.ALL_TIME, "", AT);

        assertThat(empty.isEmpty()).isTrue();
        assertThat(empty.refreshedAt()).isEqualTo(AT);
    }

    @Test
    @DisplayName("die Anzeigenamen kommen aus dem Stand, nicht aus einer Nachfrage")
    void thedisplayNamesComeFromTheCache() {
        UUID player = UUID.randomUUID();

        Leaderboard board =
                Leaderboard.of(
                        Aggregation.MOB_KILLS,
                        Period.ALL_TIME,
                        "",
                        Map.of(player, 42L),
                        10,
                        Map.of(player, "Sandro"),
                        AT);

        assertThat(board.top()).singleElement().extracting(LeaderboardEntry::displayName)
                .isEqualTo("Sandro");
    }

    @Test
    @DisplayName("ohne bekannten Namen steht die Kennung da - keine leere Zeile")
    void withoutAKnownNameTheIdIsShown() {
        UUID player = UUID.randomUUID();

        Leaderboard board = board(Map.of(player, 42L), 10);

        assertThat(board.top()).singleElement().extracting(LeaderboardEntry::displayName)
                .isEqualTo(player.toString());
    }

    private static Leaderboard board(Map<UUID, Long> values, int places) {
        return Leaderboard.of(
                Aggregation.MOB_KILLS, Period.ALL_TIME, "", values, places, Map.of(), AT);
    }

    private static Map<UUID, Long> shuffled(Map<UUID, Long> values) {
        List<Map.Entry<UUID, Long>> entries = new java.util.ArrayList<>(values.entrySet());
        java.util.Collections.shuffle(entries);
        Map<UUID, Long> out = new LinkedHashMap<>();
        entries.forEach(entry -> out.put(entry.getKey(), entry.getValue()));
        return out;
    }
}
