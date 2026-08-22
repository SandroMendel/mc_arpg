package rpg.platform.ability;

import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import rpg.core.ability.EndCause;

/**
 * Everything that stops a running ability from the outside (FR-042, FR-045g).
 *
 * <p>Five of the six causes live here; the sixth - the player pressing the slot again - is the
 * trigger listener's, because it arrives as an ordinary right-click.
 *
 * <p><b>Movement is not in this list.</b> It is checked by the runtime only for abilities that asked
 * for it (FR-043), and hooking {@code PlayerMoveEvent} unconditionally would put a lookup on the
 * busiest event the server has for a case almost no ability wants. The handler below returns on a
 * single map read for everyone who has nothing running.
 */
public final class CastInterruptListener implements Listener {

    private final Function<Player, UUID> characterOf;

    /** Whether this character has something running that moving would stop. */
    private final Function<UUID, Boolean> interruptedByMovement;

    private final BiConsumer<UUID, EndCause> end;

    public CastInterruptListener(
            Function<Player, UUID> characterOf,
            Function<UUID, Boolean> interruptedByMovement,
            BiConsumer<UUID, EndCause> end) {
        this.characterOf = Objects.requireNonNull(characterOf, "characterOf");
        this.interruptedByMovement =
                Objects.requireNonNull(interruptedByMovement, "interruptedByMovement");
        this.end = Objects.requireNonNull(end, "end");
    }

    /**
     * Damage above zero after mitigation stops it.
     *
     * <p>{@code MONITOR} and after B05 priced it: the amount has to be final, and a hit that another
     * block cancelled must not interrupt anything. Zero damage is not an interruption - a shield that
     * absorbed everything did its job.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || event.getFinalDamage() <= 0.0) {
            return;
        }
        stop(player, EndCause.DAMAGE_TAKEN);
    }

    /** Switching hotbar slot stops it - the ability being cast is no longer the one in hand. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSlotChange(PlayerItemHeldEvent event) {
        stop(event.getPlayer(), EndCause.SLOT_CHANGED);
    }

    // Leaving also stops a running ability, and it is deliberately NOT handled here: B03 owns the
    // session lifecycle and permits exactly one entry and one exit (FR-007, FR-014). B08 is told
    // about the end through its SessionAttachment, which is the seam that exists for this - see
    // AbilityModule.onSessionClosing. A quit handler here compiled, worked, and was caught by
    // NoCompetingSessionListenersTest, which is what that test is for.

    /**
     * Movement, but only for an ability that asked for it (FR-043).
     *
     * <p>The order is the whole design: a map read decides it for every player with nothing running,
     * which is nearly all of them nearly all the time.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        // Standing still fires this event too. Comparing blocks rather than exact positions keeps a
        // player turning on the spot from cancelling their own cast.
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }
        UUID characterId = characterOf.apply(event.getPlayer());
        if (characterId == null || !interruptedByMovement.apply(characterId)) {
            return;
        }
        end.accept(characterId, EndCause.MOVED);
    }

    private void stop(Player player, EndCause cause) {
        UUID characterId = characterOf.apply(player);
        if (characterId != null) {
            end.accept(characterId, cause);
        }
    }
}
