package rpg.platform.classes;

import java.util.Collection;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

/**
 * ADR-018 - two separate rules that must not be confused.
 *
 * <ol>
 *   <li><b>Bound items are immovable.</b> The class armour and the class weapon are part of the
 *       character, not contents of its inventory. No route may move them.
 *   <li><b>The player drop action is off entirely</b> - for every item, bound or not. Disposal runs
 *       through the ender chest, a vendor or the bin command, never through the world.
 * </ol>
 *
 * <p>What is deliberately <b>not</b> locked: moving unbound items inside the inventory, and mob loot.
 * Mobs drop as designed; the lock is player-side only. Without that, fighting would stop being a source
 * of loot, and the counter-test for it is as important as the lock itself.
 *
 * <p>Every route gets its own handler rather than one broad one. A forgotten route is a hole in a rule
 * that claims to be absolute, and one handler per route is what makes each one testable on its own.
 */
public final class EquipmentLockListener implements Listener {

    private final Logger logger;

    public EquipmentLockListener(Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * Routes 1 to 4: a click on an armour slot, a slot swap, a shift-click, a hotbar swap.
     *
     * <p>All four arrive as {@code InventoryClickEvent}, distinguished by action and slot type. They are
     * handled together because the decision is the same in all four - the item is bound, so nothing
     * happens - and splitting them would mean four handlers reading the same two fields.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        try {
            if (!(event.getWhoClicked() instanceof Player player)) {
                return;
            }
            ItemStack hotbarTarget = null;
            if (event.getAction() == InventoryAction.HOTBAR_SWAP && event.getHotbarButton() >= 0) {
                hotbarTarget = player.getInventory().getItem(event.getHotbarButton());
            }
            if (refusesClick(event.getCurrentItem(), event.getCursor(), hotbarTarget)) {
                event.setCancelled(true);
            }
        } catch (RuntimeException failure) {
            // FR-031: an exception cancels the click, not the session. Letting it escape would take the
            // player's whole interaction with it (Constitution VI).
            event.setCancelled(true);
            logger.log(Level.WARNING, "[class] equipment lock failed on a click - click refused", failure);
        }
    }

    /**
     * The decision behind routes 1 to 4, without the event.
     *
     * <p>Extracted because an {@code InventoryClickEvent} cannot be constructed against MockBukkit -
     * {@code SimpleInventoryViewMock.convertSlot} is unimplemented, and a test that tries is <b>silently
     * skipped</b> rather than failed. Testing the decision directly is not a workaround for a weak test;
     * it is the only way to check each route at all. That the handlers themselves exist is checked
     * separately - see the {@code Handlers} group in {@code EquipmentLockTest} - and that they are
     * registered is checked by the bootstrap test (ADR-012).
     *
     * @param current the item in the clicked slot
     * @param cursor the item on the cursor - a swap moves this one <b>into</b> the slot, so checking
     *     only {@code current} would let a bound item be swapped out
     * @param hotbarTarget the item in the target slot of a hotbar swap, which is neither of the above
     */
    boolean refusesClick(ItemStack current, ItemStack cursor, ItemStack hotbarTarget) {
        return isBound(current) || isBound(cursor) || isBound(hotbarTarget);
    }

    /** Route 5: the offhand swap, which is its own event and would otherwise slip past. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        try {
            if (refusesSwap(event.getMainHandItem(), event.getOffHandItem())) {
                event.setCancelled(true);
            }
        } catch (RuntimeException failure) {
            event.setCancelled(true);
            logger.log(Level.WARNING, "[class] equipment lock failed on an offhand swap", failure);
        }
    }

    /** The decision behind route 5. */
    boolean refusesSwap(ItemStack mainHand, ItemStack offHand) {
        return isBound(mainHand) || isBound(offHand);
    }

    /**
     * Route 6: the drop action, refused for <b>every</b> item (FR-027).
     *
     * <p>Not just bound ones. Items cannot be thrown into the world at all; the three sanctioned
     * disposal routes are the ender chest, a vendor and the bin command (ADR-018).
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        event.setCancelled(true);
    }

    /**
     * A drag across slots would move an item without ever being a click.
     *
     * <p>Not one of the six routes named in the spec, and that is exactly why it is here: the six were
     * a list of what someone thought of, and a rule that claims to be absolute has to cover what nobody
     * listed.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        try {
            if (refusesDrag(event.getOldCursor(), event.getNewItems().values())) {
                event.setCancelled(true);
            }
        } catch (RuntimeException failure) {
            event.setCancelled(true);
            logger.log(Level.WARNING, "[class] equipment lock failed on a drag", failure);
        }
    }

    /** The decision behind the drag route. */
    boolean refusesDrag(ItemStack oldCursor, Collection<ItemStack> newItems) {
        if (isBound(oldCursor)) {
            return true;
        }
        for (ItemStack item : newItems) {
            if (isBound(item)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether this item carries a binding tag at all.
     *
     * <p>Deliberately the owner-agnostic question. In a click there is no reliable way to know which
     * character an item was bound to without a lookup, and an item bound to <i>someone</i> must not be
     * movable either - it is not loot, it is another character's equipment.
     */
    private static boolean isBound(ItemStack item) {
        return BoundItemTag.isTagged(item);
    }
}
