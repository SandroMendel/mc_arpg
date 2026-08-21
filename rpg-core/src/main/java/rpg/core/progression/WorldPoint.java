package rpg.core.progression;

import java.util.Objects;
import java.util.UUID;

/**
 * A place, without a Bukkit dependency (FR-041a, FR-045).
 *
 * <p><b>Why this type exists at all.</b> {@code rpg-core} may not depend on Bukkit (Principle III),
 * and B05's death event carries no location. The alternative was passing the dead creature's id and
 * looking the location up in the platform layer - but the creature is dead:
 * {@code Bukkit.getEntity(id)} only succeeds while B05's death handling is still running and the
 * entity has not been removed. That is a timing condition nobody can see at a public extension point
 * and one that breaks the first time somebody calls it a tick later. The listener reads the location
 * where it is certainly valid and passes a value.
 *
 * @param worldId which world; two points in different worlds are never in range
 * @param x block coordinate
 * @param y block coordinate
 * @param z block coordinate
 */
public record WorldPoint(UUID worldId, double x, double y, double z) {

    public WorldPoint {
        Objects.requireNonNull(worldId, "worldId");
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException(
                    "coordinates must be finite, but were " + x + "/" + y + "/" + z);
        }
    }

    /**
     * Squared distance, so no square root is taken in the combat path.
     *
     * <p>Different worlds yield {@link Double#POSITIVE_INFINITY} rather than an exception: the caller
     * compares against a range, and infinity makes that comparison do the world check as well
     * (FR-045). An exception would force every caller to check the world first and remember why.
     */
    public double distanceSquaredTo(WorldPoint other) {
        Objects.requireNonNull(other, "other");
        if (!worldId.equals(other.worldId)) {
            return Double.POSITIVE_INFINITY;
        }
        double dx = x - other.x;
        double dy = y - other.y;
        double dz = z - other.z;
        return dx * dx + dy * dy + dz * dz;
    }
}
