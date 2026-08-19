package rpg.platform.session;

import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import rpg.core.session.PlayerSession;
import rpg.core.session.SessionLifecycle;

/**
 * Collects the preloaded session and releases the player (FR-003).
 *
 * <p>In the expected case this does almost nothing: the session was loaded before the player
 * entered, so it is taken from the stash and the player is free immediately - the 500 ms target
 * from SC-001 is met by not needing the time at all.
 *
 * <p>If the stash is empty the fallback engages: the player is held - frozen and invulnerable - and
 * the session is loaded now. That path should never run, and it exists precisely because "should
 * never" is not "cannot".
 */
public final class SessionJoinListener implements Listener {

    private final SessionLifecycle lifecycle;
    private final PendingSessionStash stash;
    private final SafeStateGuard safeState;
    private final Logger logger;

    public SessionJoinListener(
            SessionLifecycle lifecycle,
            PendingSessionStash stash,
            SafeStateGuard safeState,
            Logger logger) {
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        this.stash = Objects.requireNonNull(stash, "stash");
        this.safeState = Objects.requireNonNull(safeState, "safeState");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Optional<PlayerSession> preloaded = stash.take(player.getUniqueId());

        if (preloaded.isPresent()) {
            lifecycle.markReady(player.getUniqueId());
            safeState.release(player);
            return;
        }

        // Should not happen. Holding the player is the only correct response: letting them play
        // with default values would make those values permanent at the next write.
        logger.warning(
                "[session] no preloaded session for "
                        + player.getUniqueId()
                        + " - holding them and loading now");
        safeState.hold(player);
    }
}
