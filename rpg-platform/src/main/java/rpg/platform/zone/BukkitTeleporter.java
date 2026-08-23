package rpg.platform.zone;

import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.entity.Player;

import rpg.core.scheduler.WorldPosition;
import rpg.core.zone.Teleporter;

/**
 * Moves a player, and says honestly whether it worked.
 *
 * <p><b>The honest return value is the point of this class.</b> Travel debits coins before it moves
 * anybody, so a failure that looked like a success would cost a player their money for a journey they
 * did not make (FR-050f, research.md R1). Every way this can go wrong is turned into {@code false}
 * rather than an exception or a shrug:
 *
 * <ul>
 *   <li>the player is no longer online - they left between the click and the call
 *   <li>the world is gone - a zone can point into a world that was unloaded (FR-037)
 *   <li>{@code Player.teleport} refused - another plugin cancelled the event
 *   <li>something threw - contained and logged, because a fault here must not leave a player in an
 *       unclear state (Constitution VI)
 * </ul>
 *
 * <p>Runs in the tick, like every Paper call in this project (Constitution I).
 */
public final class BukkitTeleporter implements Teleporter {

    private final Server server;
    private final Logger logger;

    public BukkitTeleporter(Server server, Logger logger) {
        this.server = Objects.requireNonNull(server, "server");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public boolean teleport(UUID holderId, WorldPosition destination) {
        try {
            Player player = server.getPlayer(holderId);
            if (player == null) {
                return false;
            }
            Location target = BukkitPositions.toLocation(destination).orElse(null);
            if (target == null) {
                logger.warning(
                        "[zone] teleport refused: the world of "
                                + destination
                                + " is not loaded - falling back is the caller's decision");
                return false;
            }
            return player.teleport(target);
        } catch (RuntimeException failure) {
            logger.log(
                    Level.SEVERE,
                    "[zone] teleporting holder " + holderId + " to " + destination + " failed",
                    failure);
            return false;
        }
    }
}
