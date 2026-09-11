package rpg.core.mob;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Was dieser Block gerade in der Welt haelt.
 *
 * <p><b>Im Speicher und nirgends sonst.</b> Eine Kreatur ueberlebt keinen Neustart (FR-023), also
 * gibt es nichts zu persistieren - keinen Aggregattyp, keine Migration, keine Tabelle. Der Bestand
 * ist Zustand der laufenden Welt, nicht des Charakters.
 *
 * <p><b>Drei Zaehlungen werden mitgefuehrt statt bei Bedarf gezaehlt.</b> Zone, Chunk und Gesamt
 * stehen im Spawn-Pfad, und dort ist eine Schleife ueber den Bestand genau das, was Prinzip II
 * verbietet. Sie werden beim Eintragen und Austragen fortgeschrieben; ein Eintrag verschwindet,
 * wenn seine Zaehlung auf null faellt.
 *
 * <p><b>Die Zone ist die Ursprungszone</b> (FR-017). Bewegt sich eine Kreatur ueber die Grenze,
 * bleibt sie dem Budget zugerechnet, aus dem sie stammt. Sonst waere das Budget zu umgehen, indem
 * man Kreaturen hinausschiebt - und ein Budget, das man umgehen kann, ist keines.
 */
public final class HordeRegistry {

    /** Woher eine Kreatur stammt. Die Herkunft bleibt absichtlich nur im fluechtigen Bestand. */
    public enum Origin {
        BUDGET,
        ADMIN
    }

    /**
     * Ein Eintrag.
     *
     * @param entityId die Entitaet in der Welt
     * @param kindKey ihre Art - fuer Werte, Erfahrung und Coins
     * @param zoneKey die Zone, aus der sie stammt, nicht die, in der sie steht
     * @param chunkKey gepackt, wie in B09s Index
     * @param spawnedAt wann sie gesetzt wurde. Die Aufraeumfrist haengt am letzten Spieler und nicht
     *     hieran - dieser Zeitstempel ist fuer Auswertung und fuer „die aelteste zuerst"
     * @param origin regulärer Budget-Spawn oder manueller Admin-Spawn
     */
    public record Entry(
            UUID entityId,
            String kindKey,
            String zoneKey,
            long chunkKey,
            Instant spawnedAt,
            Origin origin) {

        public Entry {
            Objects.requireNonNull(entityId, "entityId");
            Objects.requireNonNull(kindKey, "kindKey");
            Objects.requireNonNull(zoneKey, "zoneKey");
            Objects.requireNonNull(spawnedAt, "spawnedAt");
            Objects.requireNonNull(origin, "origin");
        }
    }

    // LinkedHashMap: die Reihenfolge ist die des Setzens, und damit ist "die aelteste zuerst" ohne
    // Sortierung zu haben - dieselbe Ueberlegung, die B08b fuer die Deckelung der Haufen anstellt.
    private final Map<UUID, Entry> byEntity = new LinkedHashMap<>();
    private final Map<String, Integer> budgetByZone = new HashMap<>();
    private final ChunkCount byChunk = new ChunkCount();
    private int budgetTotal;
    private int adminTotal;

    /** Traegt eine gesetzte Kreatur ein und schreibt die drei Zaehlungen fort. */
    public void add(Entry entry) {
        Objects.requireNonNull(entry, "entry");
        if (byEntity.putIfAbsent(entry.entityId(), entry) != null) {
            // Zweimal dieselbe Entitaet waere eine doppelte Zaehlung, und die faellt erst auf, wenn
            // das Budget nicht mehr stimmt. Lieber hier auffallen.
            throw new IllegalStateException("entity already registered: " + entry.entityId());
        }
        if (entry.origin() == Origin.BUDGET) {
            budgetTotal++;
            budgetByZone.merge(entry.zoneKey(), 1, Integer::sum);
        } else {
            adminTotal++;
        }
        byChunk.increment(entry.chunkKey());
    }

    /**
     * Traegt sie aus. Antwortet mit dem Eintrag, falls es einen gab.
     *
     * <p>Unbekannt ist kein Fehler: eine Kreatur kann auf einem Weg verschwinden, den dieser Block
     * nicht ausgeloest hat - ein Betreiber, eine entladene Welt, ein anderes Plugin. Der Bestand
     * hoert dem Entfernungs-Ereignis zu, damit er ehrlich bleibt, und darf dabei nicht werfen.
     */
    public Entry remove(UUID entityId) {
        Entry entry = byEntity.remove(entityId);
        if (entry == null) {
            return null;
        }
        if (entry.origin() == Origin.BUDGET) {
            budgetTotal--;
            budgetByZone.computeIfPresent(
                    entry.zoneKey(), (key, count) -> count <= 1 ? null : count - 1);
        } else {
            adminTotal--;
        }
        byChunk.decrement(entry.chunkKey());
        return entry;
    }

    /** Ob diese Entitaet zu diesem Block gehoert. */
    public boolean holds(UUID entityId) {
        return byEntity.containsKey(entityId);
    }

    /** Der Eintrag, oder {@code null}. */
    public Entry find(UUID entityId) {
        return byEntity.get(entityId);
    }

    /** Wie viele insgesamt - gegen das serverweite Budget. */
    public int total() {
        return budgetTotal;
    }

    /** Wie viele in dieser Zone. Null fuer eine Zone, in der keine steht. */
    public int countIn(String zoneKey) {
        return budgetByZone.getOrDefault(zoneKey, 0);
    }

    /** Wie viele manuell durch einen Admin gesetzt wurden - gegen die separate Admin-Grenze. */
    public int countAdmin() {
        return adminTotal;
    }

    /** Wie viele in diesem Chunk. */
    public int countInChunk(long chunkKey) {
        return byChunk.get(chunkKey);
    }

    /** Wie viele Chunks belegt sind - fuer Tests und fuer eine Logzeile. */
    public int occupiedChunks() {
        return byChunk.occupiedChunks();
    }

    /** Alle Eintraege, in der Reihenfolge des Setzens. Unveraenderlich. */
    public Collection<Entry> all() {
        return Collections.unmodifiableCollection(byEntity.values());
    }

    /** Leert den Bestand. Beim Herunterfahren, nachdem die Entitaeten entfernt sind (FR-023). */
    public void clear() {
        byEntity.clear();
        budgetByZone.clear();
        budgetTotal = 0;
        adminTotal = 0;
        byChunk.clear();
    }
}
