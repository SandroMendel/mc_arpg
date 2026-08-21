package rpg.platform.progression;

import java.util.Objects;
import java.util.UUID;

import org.bukkit.Server;
import org.bukkit.entity.Player;

import rpg.core.progression.ProximityCheck;
import rpg.core.progression.WorldPoint;

/**
 * Measures the distance from the dead creature to each party member (FR-041a, FR-044, FR-045).
 *
 * <p>The one thing in B06 that needs Bukkit, which is why it sits behind an interface: {@code
 * rpg-core} has no Bukkit dependency and cannot measure a distance.
 *
 * <p><b>Squared distances, no square root.</b> This runs on every kill of every party member. And a
 * member in another world is never in range - {@link WorldPoint#distanceSquaredTo} returns infinity
 * for that, so the range comparison does the world check as well.
 */
public final class PaperProximityCheck implements ProximityCheck {

    private final Server server;

    public PaperProximityCheck(Server server) {
        this.server = Objects.requireNonNull(server, "server");
    }

    @Override
    public int inRange(
            WorldPoint origin, UUID[] candidates, int candidateCount, double range, UUID[] out) {
        double rangeSquared = range * range;
        int written = 0;
        for (int i = 0; i < candidateCount; i++) {
            UUID candidate = candidates[i];
            Player player = server.getPlayer(candidate);
            if (player == null || !player.isOnline()) {
                // Logged out between the kill and the split. Their share lapses silently, which is
                // the same thing FR-014 says about a session that is no longer ready.
                continue;
            }
            var location = player.getLocation();
            var world = location.getWorld();
            if (world == null) {
                continue;
            }
            WorldPoint at =
                    new WorldPoint(world.getUID(), location.getX(), location.getY(), location.getZ());
            if (origin.distanceSquaredTo(at) <= rangeSquared) {
                out[written++] = candidate;
            }
        }
        return written;
    }
}
