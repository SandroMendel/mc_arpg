package rpg.core.mob;

/**
 * Wie viele Kreaturen dieses Blocks in einem Chunk stehen - {@code long} auf {@code int}, ohne
 * Boxing.
 *
 * <p><b>Warum B09s {@code ChunkTable} hier nicht wiederverwendet wird.</b> Die ist beim Laden gebaut
 * und danach ausschliesslich gelesen; genau das macht sie zuweisungsfrei und erlaubt ihr, nie zu
 * wachsen. Diese Zaehlung ist das Gegenteil: sie aendert sich bei jedem Setzen und bei jedem Tod.
 * Dieselbe Klasse fuer beides haette die Zusage aufgegeben, die die eine traegt. Uebernommen wird
 * der gepackte Schluessel, nicht die Struktur.
 *
 * <p><b>Und warum keine {@code HashMap<Long, Integer>}.</b> Sie boxt bei jedem Zugriff -
 * {@link Long#valueOf} cacht nur -128 bis 127, und Chunk-Schluessel liegen nirgends in der Naehe.
 * Der Zugriff liegt im Spawn-Pfad, wo Prinzip II Zuweisungen verbietet.
 *
 * <p><b>Es stehen nur belegte Chunks drin.</b> Das ist die Antwort auf die Frage, wie man das
 * Chunk-Budget fuehrt, ohne je Chunk eine Zaehlung mitzuschleppen: die Zahl der <em>moeglichen</em>
 * Chunks ist irrelevant. Eine Region von 10.000x10.000 Bloecken hat rund 390.000 davon; darin
 * stehen hoechstens ein paar hundert Kreaturen, also hoechstens ein paar hundert belegte Chunks. Ein
 * Eintrag verschwindet, wenn er auf null faellt - nichts waechst dauerhaft.
 *
 * <p>Offene Adressierung mit linearem Sondieren, Kapazitaet als Zweierpotenz, Lastfaktor 0,5. Waechst
 * beim Ueberschreiten - anders als {@code ChunkTable}, die nie waechst, weil sie nie einfuegt.
 */
final class ChunkCount {

    /**
     * Der eine Chunk-Schluessel, der nicht vorkommen kann.
     *
     * <p>Er braeuchte ein Chunk-x von {@link Integer#MIN_VALUE}, also 34 Milliarden Bloecke
     * hinaus - weit hinter jeder Weltgrenze, die Minecraft zulaesst. Dieselbe Wahl wie in B09s
     * {@code ChunkTable}, damit beide denselben Schluesselraum benutzen.
     */
    static final long EMPTY_KEY = Long.MIN_VALUE;

    private long[] keys;
    private int[] counts;
    private int mask;
    private int size;

    ChunkCount() {
        this(64);
    }

    ChunkCount(int expectedEntries) {
        allocate(Integer.highestOneBit(Math.max(16, expectedEntries * 2 - 1)) << 1);
    }

    private void allocate(int capacity) {
        this.keys = new long[capacity];
        this.counts = new int[capacity];
        this.mask = capacity - 1;
        java.util.Arrays.fill(this.keys, EMPTY_KEY);
    }

    /** Wie viele in diesem Chunk stehen. Null fuer jeden Chunk, in dem keine steht. */
    int get(long key) {
        int at = index(key);
        while (keys[at] != EMPTY_KEY) {
            if (keys[at] == key) {
                return counts[at];
            }
            at = (at + 1) & mask;
        }
        return 0;
    }

    /** Eine dazu. */
    void increment(long key) {
        if (key == EMPTY_KEY) {
            throw new IllegalArgumentException("chunk key collides with the empty marker");
        }
        int at = index(key);
        while (keys[at] != EMPTY_KEY) {
            if (keys[at] == key) {
                counts[at]++;
                return;
            }
            at = (at + 1) & mask;
        }
        if ((size + 1) * 2 > keys.length) {
            grow();
            increment(key);
            return;
        }
        keys[at] = key;
        counts[at] = 1;
        size++;
    }

    /**
     * Eine weniger. Faellt der Zaehler auf null, verschwindet der Eintrag.
     *
     * <p>Beim Entfernen aus einer offen adressierten Tabelle muss die Sondierkette hinter der Luecke
     * neu eingehaengt werden - sonst waeren Eintraege dahinter nicht mehr auffindbar. Das ist der
     * Preis dafuer, dass die Struktur wirklich schrumpft, und er wird hier bezahlt statt verdraengt.
     */
    void decrement(long key) {
        int at = index(key);
        while (keys[at] != EMPTY_KEY) {
            if (keys[at] == key) {
                if (--counts[at] > 0) {
                    return;
                }
                keys[at] = EMPTY_KEY;
                counts[at] = 0;
                size--;
                rehashFrom((at + 1) & mask);
                return;
            }
            at = (at + 1) & mask;
        }
    }

    /** Wie viele Chunks belegt sind. Nur fuer Tests - die Zusage ist, dass die Zahl faellt. */
    int occupiedChunks() {
        return size;
    }

    /** Alles auf null. Beim Herunterfahren, nachdem die Entitaeten entfernt sind. */
    void clear() {
        java.util.Arrays.fill(keys, EMPTY_KEY);
        java.util.Arrays.fill(counts, 0);
        size = 0;
    }

    private void rehashFrom(int from) {
        int at = from;
        while (keys[at] != EMPTY_KEY) {
            long movedKey = keys[at];
            int movedCount = counts[at];
            keys[at] = EMPTY_KEY;
            counts[at] = 0;
            size--;
            insert(movedKey, movedCount);
            at = (at + 1) & mask;
        }
    }

    private void insert(long key, int count) {
        int at = index(key);
        while (keys[at] != EMPTY_KEY) {
            at = (at + 1) & mask;
        }
        keys[at] = key;
        counts[at] = count;
        size++;
    }

    private void grow() {
        long[] oldKeys = keys;
        int[] oldCounts = counts;
        allocate(oldKeys.length << 1);
        size = 0;
        for (int i = 0; i < oldKeys.length; i++) {
            if (oldKeys[i] != EMPTY_KEY) {
                insert(oldKeys[i], oldCounts[i]);
            }
        }
    }

    private int index(long key) {
        long mixed = key * 0x9E3779B97F4A7C15L;
        return (int) (mixed ^ (mixed >>> 32)) & mask;
    }
}
