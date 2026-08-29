package rpg.core.statistics;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * FR-020, FR-021 — <b>das Level eines Kontos ist der höchste seiner Charaktere.</b>
 *
 * <p>Nicht die Summe: wer eine Figur auf 60 gespielt hat und daneben zwei Anfänger stehen hat, ist
 * Stufe 60 und nicht Stufe 62. Und nicht der Durchschnitt: dann würde jede neue Figur den Spieler
 * in der Rangliste nach unten ziehen, und niemand probierte je eine zweite Klasse aus.
 *
 * <p><b>Der Gleichstandsentscheid ist die XP <em>innerhalb</em> dieses Levels</b>, nicht eine
 * Gesamterfahrung. Der Unterschied ist wichtig, weil {@code xp_in_level} genau das ist, was der
 * Name sagt: ein Wert, der bei jedem Aufstieg wieder bei null beginnt. Ein Vergleich über
 * Levelgrenzen hinweg wäre bedeutungslos — deshalb ordnet er nur <em>innerhalb</em> eines
 * Gleichstands.
 *
 * <p>Und angezeigt wird die Klasse <b>genau dieses</b> Charakters — nicht irgendeine des Kontos.
 * Wer als Magier Stufe 60 erreicht hat, steht als Magier in der Liste, auch wenn er daneben einen
 * Krieger auf Stufe 3 hat.
 */
class AccountLevelIsTheHighestCharacterTest {

    private static final Instant AT = Instant.parse("2026-08-30T12:00:00Z");

    @Test
    @DisplayName("FR-021 - das Konto traegt den hoechsten Level, nicht die Summe")
    void theAccountCarriesTheHighestLevel() {
        UUID account = UUID.randomUUID();

        // Drei Charaktere: 60, 3, 12. Die Summe waere 75, der Durchschnitt 25.
        Leaderboard board = levelBoard(Map.of(account, 60L), Map.of(account, 0L));

        assertThat(board.top()).singleElement().extracting(LeaderboardEntry::value).isEqualTo(60L);
    }

    @Test
    @DisplayName("FR-020 - bei gleichem Level entscheidet die XP INNERHALB des Levels")
    void atequalLevelTheXpInsideTheLevelDecides() {
        UUID ahead = UUID.randomUUID();
        UUID behind = UUID.randomUUID();

        Map<UUID, Long> levels = new LinkedHashMap<>();
        levels.put(behind, 42L);
        levels.put(ahead, 42L);

        Map<UUID, Long> xp = new LinkedHashMap<>();
        xp.put(behind, 100L);
        xp.put(ahead, 9_000L);

        Leaderboard board = levelBoard(levels, xp);

        assertThat(board.top()).extracting(LeaderboardEntry::playerId).containsExactly(ahead, behind);
    }

    @Test
    @DisplayName("FR-034 - beide teilen sich trotzdem den Platz, denn ihr LEVEL ist gleich")
    void bothStillShareTheRank() {
        UUID ahead = UUID.randomUUID();
        UUID behind = UUID.randomUUID();

        Leaderboard board =
                levelBoard(
                        Map.of(ahead, 42L, behind, 42L),
                        Map.of(ahead, 9_000L, behind, 100L));

        // Der angezeigte Wert ist der Level, und der ist gleich. Die XP ordnet nur die Zeilen
        // untereinander - sie ist kein zweiter sichtbarer Wert und darf keinen Platz kosten.
        assertThat(board.top()).extracting(LeaderboardEntry::rank).containsExactly(1, 1);
    }

    @Test
    @DisplayName("die XP entscheidet NUR innerhalb eines Gleichstands, nie darueber hinweg")
    void thexpDecidesOnlyWithinATie() {
        UUID higherLevel = UUID.randomUUID();
        UUID moreXp = UUID.randomUUID();

        Leaderboard board =
                levelBoard(
                        Map.of(higherLevel, 43L, moreXp, 42L),
                        Map.of(higherLevel, 0L, moreXp, 999_999L));

        assertThat(board.top()).extracting(LeaderboardEntry::playerId)
                .as("ein Level mehr schlaegt jede Menge XP im niedrigeren Level")
                .containsExactly(higherLevel, moreXp);
    }

    @Test
    @DisplayName("der Anzeigename traegt die Klasse genau dieses Charakters")
    void thedisplayNameCarriesThatCharactersClass() {
        UUID account = UUID.randomUUID();

        Leaderboard board =
                Leaderboard.of(
                        Aggregation.LEVEL,
                        Period.ALL_TIME,
                        "",
                        Map.of(account, 60L),
                        Map.of(),
                        10,
                        Map.of(account, "Sandro (MAGE)"),
                        AT);

        assertThat(board.top()).singleElement().extracting(LeaderboardEntry::displayName)
                .isEqualTo("Sandro (MAGE)");
    }

    private static Leaderboard levelBoard(Map<UUID, Long> levels, Map<UUID, Long> xpInLevel) {
        return Leaderboard.of(
                Aggregation.LEVEL, Period.ALL_TIME, "", levels, xpInLevel, 10, Map.of(), AT);
    }
}
