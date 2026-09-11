package rpg.platform.zone;

import java.util.Optional;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import rpg.core.scheduler.WorldPosition;
import rpg.core.zone.WorldResolver;

/**
 * The single place where a Paper location becomes a core value, and back (Constitution III.1).
 *
 * <p>{@code rpg.core.zone} may not see Bukkit, so everything in it speaks
 * {@link WorldPosition} - the type {@code rpg-core} already owns for exactly this reason. Without
 * this class each listener would do its own conversion, and the domain boundary would be a matter of
 * discipline rather than of structure.
 *
 * <p><b>{@link #resolver()} is what lets the domain layer refuse an unknown world</b> (FR-002a).
 * Resolving a name to a world is Paper's business; the schema only needs the answer.
 */
public final class BukkitPositions {

    private BukkitPositions() {}

    /** A world resolver backed by the running server. */
    public static WorldResolver resolver() {
        return name -> Optional.ofNullable(Bukkit.getWorld(name)).map(World::getUID);
    }

    /** The core value for this location. */
    public static WorldPosition of(Location location) {
        return new WorldPosition(
                location.getWorld().getUID(), location.getX(), location.getY(), location.getZ());
    }

    /**
     * A Bukkit location for this core value, or empty when the world is gone.
     *
     * <p>Empty rather than an exception: a world can be unloaded while a character carries a pending
     * respawn into it, and that has to end in the fallback point rather than in a failed join
     * (FR-037).
     */
    public static Optional<Location> toLocation(WorldPosition position) {
        World world = worldOf(position.worldId());
        if (world == null) {
            return Optional.empty();
        }
        return Optional.of(new Location(world, position.x(), position.y(), position.z()));
    }

    private static World worldOf(UUID worldId) {
        return Bukkit.getWorld(worldId);
    }
}
