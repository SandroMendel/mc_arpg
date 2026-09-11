package rpg.platform.zone;

import java.util.Objects;
import java.util.function.BiConsumer;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;

import rpg.core.message.MessageKey;
import rpg.core.zone.RespawnRouting;
import rpg.core.zone.ZoneMessageKeys;

/**
 * Sends a death back to the safe core of the region it happened in (FR-033 to FR-035).
 *
 * <p><b>Priority NORMAL, deliberately.</b> B05 already listens on this event at MONITOR to refill
 * health and mana, and MONITOR means "look, do not touch" - so the location has to be set before it
 * runs. The two do not compete: B05 answers "how much life does the character have", this one answers
 * "where does the character stand", and neither reads the other's answer.
 *
 * <p><b>{@code PlayerRespawnEvent} is not a session lifecycle event</b>, so a second handler on it is
 * allowed. That is not true of joining and leaving - those belong to B03 alone (FR-007), which is why
 * the placement on join goes through its observer instead of a listener of our own.
 *
 * <p>The message is the same either way. "You died" is true whether the trip ended in the region's own
 * core or at the fallback point, and a second wording would be a distinction the player has no use
 * for.
 */
public final class ZoneRespawnListener implements Listener {

    private final RespawnRouting routing;
    private final BiConsumer<Player, MessageKey> messages;

    public ZoneRespawnListener(RespawnRouting routing, BiConsumer<Player, MessageKey> messages) {
        this.routing = Objects.requireNonNull(routing, "routing");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Location target =
                BukkitPositions.toLocation(routing.respawnFor(player.getUniqueId())).orElse(null);
        if (target == null) {
            // The configured point names a world that is not loaded. Leaving the vanilla respawn in
            // place is better than refusing the respawn - a player who cannot come back at all is a
            // worse outcome than one who comes back in the wrong place, and the log says so.
            return;
        }
        event.setRespawnLocation(target);
        messages.accept(player, ZoneMessageKeys.DIED);
    }
}
