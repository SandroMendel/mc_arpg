package rpg.core.mob;

import java.time.Duration;
import java.util.Objects;

/**
 * WER weg soll - ohne Bukkit, testbar (FR-019, FR-020, FR-021, FR-022).
 *
 * <p><b>Zwei Ausloeser, eine Ausnahme.</b> Eine Zone ohne Spieler wird nach Ablauf einer Frist
 * insgesamt geraeumt (FR-019) - erst nach der Frist, damit ein kurzer Rueckweg oder ein
 * Zonenwechsel nicht sofort alles wegwischt. Solange die Frist laeuft, bleibt alles unangetastet;
 * eine Zone mit Spielern raeumt stattdessen laufend, wer weiter als die Reichweite von jedem
 * Spieler entfernt ist (FR-020). Beide Wege spart eine Kreatur im Kampf aus (FR-022) - B05 fragt,
 * keine eigene Buchfuehrung.
 *
 * <p><b>Bekommt die fertige Chunk-Menge aus {@link NearbyChunks}, nicht die Spielerliste</b>
 * (research.md R3a). Was man nicht in der Hand hat, kann man nicht in einer Schleife durchgehen -
 * dieselbe Bauart wie {@code CombatStatusSource} in B05.
 */
public final class CleanupRule {

    private CleanupRule() {}

    /**
     * Ob eine seit {@code emptyFor} unbevoelkerte Zone jetzt insgesamt geraeumt werden soll
     * (FR-019).
     */
    public static boolean zoneIsAbandoned(Duration emptyFor, Duration cleanupAfter) {
        Objects.requireNonNull(emptyFor, "emptyFor");
        Objects.requireNonNull(cleanupAfter, "cleanupAfter");
        return emptyFor.compareTo(cleanupAfter) >= 0;
    }

    /**
     * Ob genau diese eine Kreatur jetzt weg soll.
     *
     * <p>Im Kampf nie (FR-022). Sonst: weg, wenn ihre Zone insgesamt geraeumt wird, oder wenn ihr
     * Chunk in keinem gestempelten Umkreis eines Spielers liegt (FR-020).
     *
     * @param zoneAbandoned ob die Zone dieser Kreatur insgesamt geraeumt wird (FR-019)
     * @param chunkKey der Chunk, in dem sie steht - gepackt wie in {@link NearbyChunks}
     * @param nearbyPlayers die vor dem Durchlauf gestempelte Menge erreichbarer Chunks
     * @param inCombat ob sie gerade im Kampf mit einem Spieler steht (B05, FR-022)
     */
    public static boolean shouldRemove(
            boolean zoneAbandoned, long chunkKey, NearbyChunks nearbyPlayers, boolean inCombat) {
        Objects.requireNonNull(nearbyPlayers, "nearbyPlayers");
        if (inCombat) {
            return false;
        }
        return zoneAbandoned || !nearbyPlayers.contains(chunkKey);
    }
}
