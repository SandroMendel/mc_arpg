package rpg.platform.session;

import java.util.Objects;
import java.util.logging.Logger;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import rpg.core.session.SessionEndReason;
import rpg.core.session.SessionLifecycle;

/**
 * Ends a session when the player is gone (FR-007).
 *
 * <p><strong>One listener, three cases.</strong> {@code PlayerQuitEvent} fires for a deliberate
 * quit, for a kick and for a dropped connection alike. Adding a second handler on
 * {@code PlayerKickEvent} would look thorough and would in fact fire the unload twice for every
 * kick - producing the duplicate write FR-014 exists to prevent. The single trigger is the design.
 */
public final class SessionQuitListener implements Listener {

    private final SessionLifecycle lifecycle;
    private final SafeStateGuard safeState;
    private final Logger logger;

    public SessionQuitListener(
            SessionLifecycle lifecycle, SafeStateGuard safeState, Logger logger) {
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        this.safeState = Objects.requireNonNull(safeState, "safeState");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        java.util.UUID playerId = event.getPlayer().getUniqueId();
        safeState.release(playerId);

        // Bukkit does not distinguish the three cases here; the reason is recorded as QUIT and the
        // log carries the detail. What matters is that the write happens on all three paths.
        lifecycle
                .endSession(playerId, SessionEndReason.QUIT)
                .exceptionally(
                        failure -> {
                            logger.warning(
                                    "[session] unload for " + playerId + " failed: " + failure);
                            return null;
                        });
    }
}
