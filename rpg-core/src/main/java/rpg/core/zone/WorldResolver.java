package rpg.core.zone;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Turns a world name from {@code zones.yml} into the id a {@link Zone} carries.
 *
 * <p>This interface is why FR-002a can be checked without a running server. Resolving a name to a
 * world is Paper's business; the platform layer supplies an implementation backed by
 * {@code Bukkit.getWorld}, and a test supplies {@link #of(Map)}. The domain layer never sees a
 * {@code World} (Constitution III.1).
 *
 * <p>An empty result means <b>the world does not exist</b>, and that refuses the start (FR-002a).
 * That check used to hang off the old FR-051c, where it applied to a crystal's destination world;
 * when the crystal lost its own destination the check went with it, and {@code /analyze} found the
 * gap. It belongs to the zone, because a zone <em>is</em> {@code (worldId, geometry)}.
 */
@FunctionalInterface
public interface WorldResolver {

    /** The id of that world, or empty when no such world exists. */
    Optional<UUID> resolve(String worldName);

    /** A fixed set of known worlds. For tests and for a fully pre-loaded server. */
    static WorldResolver of(Map<String, UUID> known) {
        Map<String, UUID> copy = Map.copyOf(known);
        return name -> Optional.ofNullable(copy.get(name));
    }
}
