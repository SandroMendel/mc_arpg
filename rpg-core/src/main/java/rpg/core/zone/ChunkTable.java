package rpg.core.zone;

/**
 * An open-addressed map from a packed chunk key to one value, without boxing.
 *
 * <p>This exists because {@code HashMap<Long, ?>} allocates a {@link Long} on every single lookup -
 * {@link Long#valueOf} only caches -128..127, and chunk keys are nowhere near that range. The lookup
 * sits behind {@code PlayerMoveEvent}, where Constitution II forbids allocation in the hot path, so a
 * long-keyed table is not a micro-optimisation here but the requirement (FR-005, FR-006).
 *
 * <p>Built once when the configuration is read, then <b>only read</b> - no mutable global state in
 * the gameplay path (Constitution I). Linear probing with a power-of-two capacity and a load factor
 * of 0.5: the table is small (a 10.000x10.000 continent is about 390.000 chunks) and never resized
 * after the build, so probe chains stay short and predictable.
 *
 * <p>{@link #EMPTY_KEY} is {@link Long#MIN_VALUE}. It is the one chunk coordinate pair that cannot
 * occur - it would need a chunk x of {@code Integer.MIN_VALUE}, which is 34 billion blocks out, far
 * past any world border Minecraft allows.
 */
final class ChunkTable {

    static final long EMPTY_KEY = Long.MIN_VALUE;

    private final long[] keys;
    private final Object[] values;
    private final int mask;
    private int size;

    ChunkTable(int expectedEntries) {
        int capacity = Integer.highestOneBit(Math.max(16, expectedEntries * 2 - 1)) << 1;
        this.keys = new long[capacity];
        this.values = new Object[capacity];
        this.mask = capacity - 1;
        java.util.Arrays.fill(this.keys, EMPTY_KEY);
    }

    /** Inserts or replaces. Load path only. */
    void put(long key, Object value) {
        if (key == EMPTY_KEY) {
            throw new IllegalArgumentException("chunk key collides with the empty marker");
        }
        int at = index(key);
        while (keys[at] != EMPTY_KEY) {
            if (keys[at] == key) {
                values[at] = value;
                return;
            }
            at = (at + 1) & mask;
        }
        if ((size + 1) * 2 > keys.length) {
            throw new IllegalStateException(
                    "chunk table built with too small an estimate: " + size + " of " + keys.length);
        }
        keys[at] = key;
        values[at] = value;
        size++;
    }

    /** The value for this chunk, or {@code null}. No allocation, no boxing. */
    Object get(long key) {
        int at = index(key);
        while (true) {
            long found = keys[at];
            if (found == key) {
                return values[at];
            }
            if (found == EMPTY_KEY) {
                return null;
            }
            at = (at + 1) & mask;
        }
    }

    int size() {
        return size;
    }

    private int index(long key) {
        // Fibonacci-style mixing: chunk keys are two adjacent ints, so the low bits alone would
        // cluster badly for a world laid out along an axis.
        long mixed = key * 0x9E3779B97F4A7C15L;
        return (int) (mixed >>> 32) & mask;
    }
}
