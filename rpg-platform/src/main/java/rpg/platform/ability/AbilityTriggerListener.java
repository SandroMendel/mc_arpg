package rpg.platform.ability;

import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import rpg.core.ability.AbilityResult;
import rpg.core.message.MessageKey;

/**
 * The only way a player triggers an ability: right-click on its hotbar slot (FR-053, ADR-005).
 *
 * <p>No keybinds, no client requirement. {@link PlayerInteractEvent} is the one place that sees both
 * halves at once - which slot is held and which button was pressed.
 *
 * <p><b>The left click needs two hands, not one</b> (FR-054, research.md R4). Cancelling the interact
 * event stops the block interaction, but a swing at a <em>creature</em> arrives as
 * {@link EntityDamageByEntityEvent} and would run straight into B05's melee path. Both are refused,
 * or a player hits a mob with the goat horn and deals weapon damage with it.
 */
public final class AbilityTriggerListener implements Listener {

    private final BiFunction<Player, String, AbilityResult> trigger;
    private final Notifier notifier;
    private final Logger logger;

    /** Tells the player why a trigger was refused. B13 will draw it properly. */
    @FunctionalInterface
    public interface Notifier {
        void notify(Player player, MessageKey key);
    }

    public AbilityTriggerListener(
            BiFunction<Player, String, AbilityResult> trigger, Notifier notifier, Logger logger) {
        this.trigger = Objects.requireNonNull(trigger, "trigger");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        // Off-hand fires a second event for the same click. Ignoring it here is what keeps one press
        // from triggering an ability twice.
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Optional<String> abilityId = AbilityItemTag.read(event.getItem());
        if (abilityId.isEmpty()) {
            return;
        }

        Action action = event.getAction();
        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            // Half of FR-054. An ability item is input, and a left click on it means nothing at all -
            // not the ability, and not a block interaction either.
            event.setCancelled(true);
            return;
        }
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        // The item must not also place a block or open something while it triggers.
        event.setCancelled(true);
        fire(event.getPlayer(), abilityId.get());
    }

    /**
     * The other half of FR-054: a swing at a creature with an ability item is not an attack.
     *
     * <p>{@code LOWEST} and before B05 sees it. Letting it through would give the goat horn weapon
     * damage, and the player would have found that out by accident.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSwing(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        if (AbilityItemTag.isAbilityItem(held)) {
            event.setCancelled(true);
        }
    }

    private void fire(Player player, String abilityId) {
        try {
            AbilityResult result = trigger.apply(player, abilityId);
            if (!result.isSuccess()) {
                notifier.notify(player, result.messageKey());
            }
        } catch (RuntimeException failure) {
            // Confined to this one click. An exception here must not put the player into an
            // inconsistent state or take the session down (Principle VI).
            logger.log(
                    Level.WARNING,
                    "[abilities] triggering " + abilityId + " failed and was contained",
                    failure);
        }
    }
}
