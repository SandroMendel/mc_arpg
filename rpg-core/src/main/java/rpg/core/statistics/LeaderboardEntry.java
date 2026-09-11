package rpg.core.statistics;

import java.util.Objects;
import java.util.UUID;

/**
 * Eine Zeile einer Rangliste ([data-model.md] §2.2).
 *
 * <p><b>Der Anzeigename gehört zum Eintrag und nicht zur Zeile in der Datenbank</b> (FR-040). Er
 * wird beim Füllen aufgelöst, außerhalb des Ticks — ein Namensnachschlag beim Öffnen des Fensters
 * wäre je Zeile eine Frage an den Server, und bei zehn Zeilen zehn davon, während der Spieler
 * wartet.
 *
 * @param rank der Platz; Gleichstände tragen denselben (FR-034)
 * @param playerId das Konto
 * @param displayName der Name zum Zeitpunkt der Auffrischung
 * @param value der Wert, in der Einheit der Metrik
 */
public record LeaderboardEntry(int rank, UUID playerId, String displayName, long value) {

    public LeaderboardEntry {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(displayName, "displayName");
        if (rank < 1) {
            throw new IllegalArgumentException("ein Platz beginnt bei 1, war aber " + rank);
        }
    }
}
