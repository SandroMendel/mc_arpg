package rpg.platform.classes;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAttemptPickupItemEvent;

import rpg.core.classes.ClassMessageKeys;
import rpg.core.classes.ClassNotice;

/**
 * Warns a player whose inventory is full while loot is arriving (FR-030, ADR-018).
 *
 * <p><b>No automatic anything.</b> No cleanup, no background bank, no silent discard - the player makes
 * room, through the ender chest, a vendor or the bin command. That is the whole point of the warning:
 * the item is still lying there, and the player has to decide.
 *
 * <p><b>Rate-limited, timestamp-based.</b> Standing on a pile of loot fires this event several times a
 * second; without the limit the player would get a wall of identical messages, and Constitution II
 * forbids per-tick work per player anyway. The limit is a timestamp per player, checked lazily - the
 * pattern Principle II prescribes, not a scheduled task.
 *
 * <p>The message goes through {@link ClassNotice}, never straight at the player. B13 replaces the
 * implementation later without this class changing (ADR-005).
 */
public final class InventoryFullNoticeListener implements Listener {

    /** Long enough that a pile of loot produces one warning, short enough to still be a warning. */
    static final Duration COOLDOWN = Duration.ofSeconds(15);

    private final ClassNotice notice;
    private final Clock clock;
    private final Map<UUID, Long> lastWarned = new ConcurrentHashMap<>();

    public InventoryFullNoticeListener(ClassNotice notice, Clock clock) {
        this.notice = Objects.requireNonNull(notice, "notice");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * MONITOR and not cancelling: the pickup is not the problem, the missing room is. Cancelling would
     * throw away an item the player might have wanted.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAttemptPickup(PlayerAttemptPickupItemEvent event) {
        Player player = event.getPlayer();
        if (player.getInventory().firstEmpty() != -1) {
            // Room left - the overwhelmingly common case, and the whole hot path for it.
            return;
        }
        if (!due(player.getUniqueId())) {
            return;
        }
        notice.show(player.getUniqueId(), ClassMessageKeys.INVENTORY_FULL);
    }

    /** Forgets a player who left, so the map does not grow with every session. */
    public void forget(UUID playerId) {
        lastWarned.remove(playerId);
    }

    int trackedPlayers() {
        return lastWarned.size();
    }

    private boolean due(UUID playerId) {
        long now = clock.millis();
        Long previous = lastWarned.get(playerId);
        if (previous != null && now - previous < COOLDOWN.toMillis()) {
            return false;
        }
        lastWarned.put(playerId, now);
        return true;
    }
}
