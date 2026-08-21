package rpg.platform.classes;

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
 * Keeps a player in place until they have chosen a class (ADR-020, FR-034).
 *
 * <p>Shaped exactly like B03's {@code SafeStateGuard}, down to the counter in front of the set: in
 * normal operation the whole hot path is one int read and a return. {@code PlayerMoveEvent} fires
 * several times a second per player, and Constitution II is explicit about that.
 *
 * <p><b>Why not reuse {@code SafeStateGuard} instead of a second listener on the same event.</b> Its
 * hold and release belong to B03's session flow - {@code SessionJoinListener} releases the moment the
 * session is ready, which is exactly when B07's hold has to begin. Sharing the set would mean two
 * blocks writing one piece of state with different end conditions, and that is what Constitution III
 * forbids. The price is a second listener whose hot path is one int read; the alternative was a
 * coupling that would break silently the next time B03 changes when it releases.
 *
 * <p>Damage is <b>not</b> handled here. A player without a character has no stat holder, so the
 * combat pipeline rejects them with {@code NO_HOLDER} before any of this matters - see
 * {@code NoCharacterNoCombatTest}. Duplicating that here would add a second answer to a settled
 * question.
 */
public final class NoCharacterGuardListener implements Listener {

    private final Set<UUID> waiting = ConcurrentHashMap.newKeySet();
    private final AtomicInteger waitingCount = new AtomicInteger();
    private final Logger logger;

    public NoCharacterGuardListener(Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /** Called when a session turned out to have no character. */
    public void hold(Player player) {
        Objects.requireNonNull(player, "player");
        if (waiting.add(player.getUniqueId())) {
            waitingCount.incrementAndGet();
            logger.fine(
                    () ->
                            "[class] holding "
                                    + player.getUniqueId()
                                    + " until a class is chosen");
        }
    }

    /** Called once the class is chosen - or when the player leaves. */
    public void release(UUID playerId) {
        if (waiting.remove(playerId)) {
            waitingCount.decrementAndGet();
        }
    }

    public boolean isHeld(UUID playerId) {
        return waiting.contains(playerId);
    }

    public int heldCount() {
        return waitingCount.get();
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        // The whole hot path in normal operation: one int read, then return.
        if (waitingCount.get() == 0) {
            return;
        }
        if (!waiting.contains(event.getPlayer().getUniqueId())) {
            return;
        }
        // Looking around is free; only leaving the block is refused. Same rule as B03's guard - a
        // frozen camera reads as a broken client, a frozen position reads as a locked state.
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
