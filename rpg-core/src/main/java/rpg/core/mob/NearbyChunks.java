package rpg.core.mob;

import java.util.Arrays;

/**
 * Der raeumliche Index des Aufraeumens: welche Chunks nah genug an einem Spieler liegen (FR-018).
 *
 * <p><b>Die Frage, die diese Klasse umdreht.</b> Beim Aufraeumen ist zu beantworten: <em>steht ein
 * Spieler in Reichweite dieser Kreatur?</em> Naheliegend waere, sie je Kreatur ueber alle Spieler zu
 * stellen. Bei 130 Kreaturen und 200 Spielern sind das 26.000 Abstandsrechnungen je Durchlauf - und
 * es ist genau die lineare Iteration ueber alle Kandidaten, die Prinzip II verbietet.
 *
 * <p><b>Also umgekehrt: die wenigen stempeln, die vielen schlagen nach.</b> Vor dem Durchlauf
 * stempelt jeder Spieler die Chunks in seiner Reichweite hier hinein. Danach ist die Frage je
 * Kreatur ein Mengenzugriff auf den {@code chunkKey}, den sie ohnehin schon traegt. Der Aufwand
 * waechst damit mit der <b>Spielerzahl</b> und nicht mit dem Produkt aus Spielern und Kreaturen -
 * und er waechst dort, wo die Zahlen klein sind: in einer Zone stehen ein paar Dutzend Spieler,
 * nicht zweihundert.
 *
 * <p><b>Der Puffer wird wiederverwendet.</b> Eine neue Menge je Durchlauf waere eine Zuweisung im
 * Spawn-Pfad, und das ist dieselbe Begruendung, aus der {@link ChunkCount} ueberhaupt so gebaut ist.
 * {@link #clear()} setzt zurueck, ohne neu zu allozieren.
 *
 * <p><b>Grob, und das reicht.</b> Gestempelt wird ein Quadrat aus Chunks, nicht ein Kreis. Eine
 * Kreatur in der Ecke des Quadrats ist bis zu 1,41-mal so weit weg wie die konfigurierte Reichweite.
 * Das ist beim Aufraeumen die richtige Richtung: im Zweifel eine Kreatur einen Moment laenger stehen
 * lassen, statt eine wegzuraeumen, die noch jemand sieht.
 */
public final class NearbyChunks {

    private static final long EMPTY_KEY = Long.MIN_VALUE;

    private long[] keys;
    private int mask;
    private int size;

    public NearbyChunks() {
        allocate(1024);
    }

    private void allocate(int capacity) {
        this.keys = new long[capacity];
        this.mask = capacity - 1;
        Arrays.fill(this.keys, EMPTY_KEY);
        this.size = 0;
    }

    /** Leert den Puffer fuer den naechsten Durchlauf, ohne neu zu allozieren. */
    public void clear() {
        Arrays.fill(keys, EMPTY_KEY);
        size = 0;
    }

    /**
     * Stempelt die Chunks im Umkreis von {@code radiusBlocks} um diese Blockposition.
     *
     * <p>Zwei nebeneinanderstehende Spieler stempeln denselben Chunk nur einmal - die Menge ist eine
     * Menge, und genau deshalb kostet ein Pulk Spieler nicht so viel wie ihre Zahl.
     */
    public void stampAround(int blockX, int blockZ, double radiusBlocks) {
        int chunkRadius = (int) Math.ceil(radiusBlocks / 16.0);
        int centreX = blockX >> 4;
        int centreZ = blockZ >> 4;
        for (int x = centreX - chunkRadius; x <= centreX + chunkRadius; x++) {
            for (int z = centreZ - chunkRadius; z <= centreZ + chunkRadius; z++) {
                add(pack(x, z));
            }
        }
    }

    /** Ob dieser Chunk nah genug an einem Spieler liegt. Ein Zugriff, keine Schleife. */
    public boolean contains(long chunkKey) {
        int at = index(chunkKey);
        while (keys[at] != EMPTY_KEY) {
            if (keys[at] == chunkKey) {
                return true;
            }
            at = (at + 1) & mask;
        }
        return false;
    }

    /** Wie viele Chunks gestempelt sind. Fuer Tests und fuer eine Logzeile. */
    public int size() {
        return size;
    }

    /**
     * Chunk-Koordinaten in einen Schluessel.
     *
     * <p>Dieselbe Packung, die B09s Index benutzt, damit beide denselben Schluesselraum haben und
     * ein Schluessel von hier dort nachgeschlagen werden koennte.
     */
    public static long pack(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    /** Der Chunk-Schluessel dieser Blockposition. */
    public static long packBlock(int blockX, int blockZ) {
        return pack(blockX >> 4, blockZ >> 4);
    }

    private void add(long key) {
        if (key == EMPTY_KEY) {
            return;
        }
        int at = index(key);
        while (keys[at] != EMPTY_KEY) {
            if (keys[at] == key) {
                return;
            }
            at = (at + 1) & mask;
        }
        if ((size + 1) * 2 > keys.length) {
            grow();
            add(key);
            return;
        }
        keys[at] = key;
        size++;
    }

    private void grow() {
        long[] old = keys;
        allocate(old.length << 1);
        for (long key : old) {
            if (key != EMPTY_KEY) {
                add(key);
            }
        }
    }

    private int index(long key) {
        long mixed = key * 0x9E3779B97F4A7C15L;
        return (int) (mixed ^ (mixed >>> 32)) & mask;
    }
}
