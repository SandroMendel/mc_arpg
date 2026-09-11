package rpg.core.zone;

/**
 * An axis-aligned box, given by two corners (FR-004).
 *
 * <p>The corners are normalised on construction, so a configuration may write them in either order
 * without changing the meaning. {@link #contains(int, int, int)} is then six integer comparisons
 * with no allocation and no branch - it sits behind the busiest event a server has (FR-005,
 * Constitution II).
 *
 * <p><b>On the vertical bounds.</b> A cuboid without Y bounds covers the whole world height: a
 * player in a cave and a player in the sky are both inside. That is expressed by storing
 * {@link Integer#MIN_VALUE} and {@link Integer#MAX_VALUE} rather than by a flag, so no caller and no
 * hot path has to ask whether the bounds are present. A cuboid <em>with</em> Y bounds deliberately
 * does <b>not</b> contain a position above or below it - that is what makes it possible for one zone
 * to sit over another later without changing this type.
 *
 * @param minX lower x bound, inclusive
 * @param minY lower y bound, inclusive
 * @param minZ lower z bound, inclusive
 * @param maxX upper x bound, inclusive
 * @param maxY upper y bound, inclusive
 * @param maxZ upper z bound, inclusive
 */
public record Cuboid(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

    /** Chunk coordinates are block coordinates shifted by this much. */
    static final int CHUNK_SHIFT = 4;

    public Cuboid {
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            throw new IllegalArgumentException(
                    "cuboid corners must be normalised: use Cuboid.of(...) instead of the canonical"
                            + " constructor");
        }
    }

    /** A box over the full world height, corners in any order. */
    public static Cuboid of(int x1, int z1, int x2, int z2) {
        return of(x1, Integer.MIN_VALUE, z1, x2, Integer.MAX_VALUE, z2);
    }

    /** A box with vertical bounds, corners in any order. */
    public static Cuboid of(int x1, int y1, int z1, int x2, int y2, int z2) {
        return new Cuboid(
                Math.min(x1, x2),
                Math.min(y1, y2),
                Math.min(z1, z2),
                Math.max(x1, x2),
                Math.max(y1, y2),
                Math.max(z1, z2));
    }

    /** Whether this box has vertical bounds at all. Metadata only; {@link #contains} never asks. */
    public boolean boundedVertically() {
        return minY != Integer.MIN_VALUE || maxY != Integer.MAX_VALUE;
    }

    /** Six comparisons, no allocation. All bounds are inclusive. */
    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    /** Whether the two boxes share at least one block. Used at load time to refuse overlap. */
    public boolean intersects(Cuboid other) {
        return minX <= other.maxX
                && maxX >= other.minX
                && minY <= other.maxY
                && maxY >= other.minY
                && minZ <= other.maxZ
                && maxZ >= other.minZ;
    }

    /** Whether this box lies completely inside {@code outer}. Used for safe cores and spawn areas. */
    public boolean isInside(Cuboid outer) {
        return minX >= outer.minX
                && maxX <= outer.maxX
                && minY >= outer.minY
                && maxY <= outer.maxY
                && minZ >= outer.minZ
                && maxZ <= outer.maxZ;
    }

    int minChunkX() {
        return minX >> CHUNK_SHIFT;
    }

    int maxChunkX() {
        return maxX >> CHUNK_SHIFT;
    }

    int minChunkZ() {
        return minZ >> CHUNK_SHIFT;
    }

    int maxChunkZ() {
        return maxZ >> CHUNK_SHIFT;
    }
}
