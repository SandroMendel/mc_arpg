package rpg.platform.session;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * Keeps a player from acting while their session is not ready (FR-002).
 *
 * <p>In normal operation this never engages: the session is loaded during the pre-login event, so a
 * player who reaches the world already has one. It exists for the case where that did not happen -
 * another plugin interfered, the stash expired, a future change bypassed the preload. Then the
 * player must not play with invented values, and this is what stops them.
 *
 * <p><strong>The hot path.</strong> {@link PlayerMoveEvent} is among the most frequently fired
 * events in Minecraft: several times per tick per player, so at 200 players it is the single
 * busiest listener in the plugin. Two things keep it within Constitution II:
 *
 * <ol>
 *   <li>The handler reads one {@code int} first. In normal operation it is zero, and the method
 *       returns - no map lookup, no allocation, no iteration.
 *   <li>Only when someone is actually being held does it look further, and even then it ignores
 *       movement inside the same block, so looking around costs nothing.
 * </ol>
 *
 * <p>Damage immunity uses {@code setInvulnerable}, which the Paper API offers directly. There is no
 * equivalent for movement: {@code setWalkSpeed(0)} stops walking but not falling, knockback or
 * momentum, so it is not an assurance and is not used.
 */
public final class SafeStateGuard implements Listener {

    private final Set<UUID> held = ConcurrentHashMap.newKeySet();

    /**
     * Mirrors {@link #held}'s size for the hot path.
     *
     * <p>A plain int read instead of {@code held.isEmpty()} - the set is concurrent, and its
     * emptiness check is cheap but not free. This is read several times per tick per player.
     */
    private final AtomicInteger heldCount = new AtomicInteger();

    private final Logger logger;

    public SafeStateGuard(Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /** Freezes a player and makes them immune until their session is ready. */
    public void hold(Player player) {
        Objects.requireNonNull(player, "player");
        if (held.add(player.getUniqueId())) {
            heldCount.incrementAndGet();
        }
        player.setInvulnerable(true);
        logger.fine("[session] holding " + player.getUniqueId() + " until their session is ready");
    }

    /** Releases a player once their session is ready (FR-003). */
    public void release(Player player) {
        Objects.requireNonNull(player, "player");
        if (held.remove(player.getUniqueId())) {
            heldCount.decrementAndGet();
        }
        player.setInvulnerable(false);
    }

    /** Releases by id, for a player object that is no longer available. */
    public void release(UUID playerId) {
        if (held.remove(playerId)) {
            heldCount.decrementAndGet();
        }
    }

    /** Whether this player is currently held. */
    public boolean isHeld(UUID playerId) {
        return held.contains(playerId);
    }

    /** How many players are held; zero in normal operation. */
    public int heldCount() {
        return heldCount.get();
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        // The whole hot path in normal operation: one int read, then return.
        if (heldCount.get() == 0) {
            return;
        }
        if (!held.contains(event.getPlayer().getUniqueId())) {
            return;
        }
        // Looking around is free; only leaving the block is refused.
        if (!changedBlock(event.getFrom(), event.getTo())) {
            return;
        }
        event.setCancelled(true);
    }

    private static boolean changedBlock(Location from, Location to) {
        if (to == null) {
            return false;
        }
        return from.getBlockX() != to.getBlockX()
                || from.getBlockY() != to.getBlockY()
                || from.getBlockZ() != to.getBlockZ();
    }
}
