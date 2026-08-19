package rpg.platform.session;

import java.util.Objects;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import com.destroystokyo.paper.event.player.PlayerConnectionCloseEvent;

import rpg.core.session.SessionLifecycle;

/**
 * Cleans up after a connection that closed without ever reaching the world (FR-015).
 *
 * <p>Paper fires this even for connections that passed pre-login and then dropped - exactly the
 * case that would otherwise leave a preloaded session in the stash and an in-flight load nobody
 * collects. Nothing is written: the player never received a state.
 *
 * <p>Distinct from {@code SessionQuitListener}, which handles players who <em>were</em> in the
 * world. Both are needed because they cover different populations, not the same one twice.
 */
public final class SessionConnectionCloseListener implements Listener {

    private final SessionLifecycle lifecycle;
    private final PendingSessionStash stash;

    public SessionConnectionCloseListener(SessionLifecycle lifecycle, PendingSessionStash stash) {
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        this.stash = Objects.requireNonNull(stash, "stash");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onConnectionClose(PlayerConnectionCloseEvent event) {
        stash.discard(event.getPlayerUniqueId());
        lifecycle.abandonLoad(event.getPlayerUniqueId());
    }
}
