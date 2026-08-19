package rpg.core.scheduler;

import java.util.Objects;
import java.util.UUID;

/**
 * A position in a world, expressed without any Paper/Bukkit type.
 *
 * <p>{@code rpg-core} must compile and be testable without Bukkit on the classpath (Constitution
 * III.1, FR-015), so the {@link Scheduler} contract's "location" parameter is modelled by this
 * core-owned value type. The platform adapter maps it to an {@code org.bukkit.Location}.
 *
 * <p>This is a refinement of {@code contracts/scheduler.md}, which sketches the signatures with
 * Bukkit types: the behavioural contract is unchanged - a synchronous task still cannot be submitted
 * without a location or entity binding - but the type boundary is honoured.
 *
 * @param worldId unique id of the world
 * @param x block/entity x coordinate
 * @param y block/entity y coordinate
 * @param z block/entity z coordinate
 */
public record WorldPosition(UUID worldId, double x, double y, double z) {

    public WorldPosition {
        Objects.requireNonNull(worldId, "worldId");
    }
}
