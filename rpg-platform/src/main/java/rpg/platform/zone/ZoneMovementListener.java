package rpg.platform.zone;

import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import rpg.core.zone.MovementGuard;
import rpg.core.zone.ZoneTracker;
import rpg.core.zone.Zones;

/**
 * Turns movement into zone evaluations (FR-017, FR-019, FR-020).
 *
 * <p><b>{@code PlayerMoveEvent} is one of the busiest events a server has</b> - {@code
 * DoubleJumpListener} says the same thing for the same reason - so everything before the guard has to
 * be free. The order here is deliberate:
 *
 * <ol>
 *   <li>Integer arithmetic on the block coordinates the event already carries. No {@code Chunk}
 *       object, no allocation, no lookup.
 *   <li>Only if that says the step matters: one table access for the chunk (see {@code ChunkTable}).
 *   <li>Only then the character lookup, which is a map access, and the evaluation.
 * </ol>
 *
 * <p>The character lookup comes <b>last</b> on purpose. It is the most expensive of the three and the
 * least likely to change the answer - somebody without a character is not in play, but somebody with
 * one still spends almost every step inside a single chunk.
 *
 * <p><b>A teleport needs its own handler, and the first version of this class did not have one.</b>
 * {@link PlayerTeleportEvent} extends {@link PlayerMoveEvent}, so it looks as if the move handler
 * would see it - the Javadoc here said exactly that. It does not: the teleport event owns a separate
 * handler list, and registering for the move event never enters it. A teleport is how a player
 * crosses a border most often - respawning, travelling by crystal, an operator moving somebody - and
 * every one of them would have left the tracker believing they were still where they started
 * (FR-017, SC-005). {@code FullBootstrapTest} counted the handlers and found zero.
 *
 * <p>Both handlers run the same body. There is no second code path to keep in sync - only a second
 * door into the one that exists.
 */
public final class ZoneMovementListener implements Listener {

    private final Supplier<Zones> zones;
    private final ZoneTracker tracker;
    private final Function<Player, UUID> characters;

    public ZoneMovementListener(
            Supplier<Zones> zones, ZoneTracker tracker, Function<Player, UUID> characters) {
        this.zones = zones;
        this.tracker = tracker;
        this.characters = characters;
    }

    /**
     * MONITOR, and cancelled events are ignored: this block observes where somebody ended up, it
     * never decides whether they may go there. A cancelled move did not happen.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        evaluate(event);
    }

    /**
     * The same thing for a teleport, which the move handler never sees.
     *
     * <p>Every arrival this block arranges itself comes through here: the respawn after a death, the
     * journey between two crystals, the placement of a character who logged out in combat.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        evaluate(event);
    }

    private void evaluate(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) {
            return;
        }
        Zones current = zones.get();
        if (current == null) {
            return;
        }

        UUID worldId = to.getWorld().getUID();
        // Changing world is always worth a look, and comparing the ids is cheaper than resolving
        // anything: a cross-world step can never stay in the same zone.
        boolean sameWorld = worldId.equals(from.getWorld().getUID());
        if (sameWorld
                && !MovementGuard.needsEvaluation(
                        current,
                        worldId,
                        from.getBlockX(),
                        from.getBlockZ(),
                        to.getBlockX(),
                        to.getBlockZ())) {
            return;
        }

        UUID characterId = characters.apply(event.getPlayer());
        if (characterId == null) {
            return;
        }
        tracker.evaluate(characterId, BukkitPositions.of(to));
    }
}
