package rpg.core.zone;

import java.util.List;
import java.util.Objects;

/**
 * A region of space, made of one or more {@link Cuboid}s (FR-004).
 *
 * <p><b>The same shape carries region, safe core, spawn area and crystal trigger area.</b> That is
 * the whole reason this type exists: one geometry system instead of four, so a change to how space
 * is described happens once.
 *
 * <p>{@link #touchedChunks()} is for the load path only - it is how {@link ChunkZoneIndex} is built.
 * Nothing in the tick path calls it.
 *
 * @param parts the boxes making up this area; at least one
 */
public record Area(List<Cuboid> parts) {

    public Area {
        Objects.requireNonNull(parts, "parts");
        if (parts.isEmpty()) {
            throw new IllegalArgumentException("an area needs at least one cuboid");
        }
        parts = List.copyOf(parts);
    }

    public static Area of(Cuboid... parts) {
        return new Area(List.of(parts));
    }

    /**
     * Whether the position is inside any part.
     *
     * <p>A plain indexed loop rather than a stream: this runs behind the movement path, where
     * Constitution II forbids allocation and boxing. Most areas have one or two parts, so the loop
     * is short by construction.
     */
    public boolean contains(int x, int y, int z) {
        List<Cuboid> boxes = parts;
        for (int i = 0; i < boxes.size(); i++) {
            if (boxes.get(i).contains(x, y, z)) {
                return true;
            }
        }
        return false;
    }

    /** Whether any part of this area shares a block with any part of the other. */
    public boolean intersects(Area other) {
        for (Cuboid mine : parts) {
            for (Cuboid theirs : other.parts) {
                if (mine.intersects(theirs)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Whether every part of this area lies completely inside some single part of {@code outer}.
     *
     * <p>Deliberately strict: a box that is covered only by the <em>union</em> of two outer boxes is
     * refused. Accepting it would need a real geometric union, and the case it would buy - a safe
     * core straddling the seam between two boxes of its own region - is a map the operator can draw
     * differently for free. The refusal is a start failure with a message, so nobody is left
     * guessing (FR-009, FR-055).
     */
    public boolean isInside(Area outer) {
        for (Cuboid mine : parts) {
            boolean covered = false;
            for (Cuboid theirs : outer.parts) {
                if (mine.isInside(theirs)) {
                    covered = true;
                    break;
                }
            }
            if (!covered) {
                return false;
            }
        }
        return true;
    }

    /**
     * The packed chunk keys this area touches, for building the index.
     *
     * <p>Load path only. The keys are the same packing {@link ChunkZoneIndex} uses.
     */
    public long[] touchedChunks() {
        long count = 0L;
        for (Cuboid box : parts) {
            long widthX = (long) box.maxChunkX() - box.minChunkX() + 1L;
            long widthZ = (long) box.maxChunkZ() - box.minChunkZ() + 1L;
            count += widthX * widthZ;
        }
        if (count > MAX_TOUCHED_CHUNKS) {
            throw new IllegalArgumentException(
                    "area spans "
                            + count
                            + " chunks, which is beyond anything a hand-built map needs; refusing to"
                            + " build an index for it (limit "
                            + MAX_TOUCHED_CHUNKS
                            + ")");
        }
        long[] keys = new long[(int) count];
        int at = 0;
        for (Cuboid box : parts) {
            for (int cx = box.minChunkX(); cx <= box.maxChunkX(); cx++) {
                for (int cz = box.minChunkZ(); cz <= box.maxChunkZ(); cz++) {
                    keys[at++] = ChunkZoneIndex.key(cx, cz);
                }
            }
        }
        return keys;
    }

    /**
     * A guard, not a design limit. A 10.000x10.000 continent is about 390.000 chunks; anything an
     * order of magnitude past that is a typo in a coordinate, and it should fail with a sentence
     * instead of exhausting the heap.
     */
    static final long MAX_TOUCHED_CHUNKS = 4_000_000L;
}
