package rpg.platform.inventory;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import rpg.platform.classes.BoundItemTag;

/**
 * Turning a player's inventory into bytes and back.
 *
 * <p>The only place that knows the format. {@code rpg-core} carries the result as an opaque blob
 * (Constitution III.1), which is not squeamishness: the format is the server's, it is versioned by
 * the server, and reproducing it in the domain would make every Minecraft update a migration.
 *
 * <p><b>Class equipment is left out.</b> It is rebuilt from the reached tier on every entry, so storing
 * it would be a second copy that can disagree - after a tier advance, or a change to
 * {@code classes.yml}, the save would hand back the old sword. Skipping it also means the restore can
 * never fight the equipment applier for a slot.
 *
 * <p>Every call touches Bukkit and therefore belongs on the owning tick. Nothing here schedules; the
 * caller is responsible for being in the right place.
 */
public final class PlayerInventoryContents {

    private PlayerInventoryContents() {}

    /**
     * What this player is carrying, class equipment excluded.
     *
     * <p>Slot positions are preserved: the array keeps its length and holds empty stacks where a bound
     * item was, so a restore puts everything back where the player left it.
     *
     * @return the serialised contents, or an empty array if there is nothing worth storing
     */
    public static byte[] capture(Player player, Logger logger) {
        return capture(player.getInventory(), player, logger);
    }

    /**
     * What this player has in their ender chest.
     *
     * <p>Stored per character for the same reason as the backpack: vanilla hangs it off the player, so
     * without this it would be the one container shared between a player's three characters.
     */
    public static byte[] captureEnderChest(Player player, Logger logger) {
        return capture(player.getEnderChest(), player, logger);
    }

    private static byte[] capture(Inventory source, Player player, Logger logger) {
        ItemStack[] contents = source.getContents();
        ItemStack[] storable = new ItemStack[contents.length];
        boolean anything = false;
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (item == null || item.getType().isAir() || isBound(item)) {
                storable[slot] = null;
                continue;
            }
            storable[slot] = item;
            anything = true;
        }
        if (!anything) {
            // An empty array rather than the serialisation of 41 empty slots: it is the common case
            // for a fresh character, and it makes "carrying nothing" cheap to store and obvious to read.
            return new byte[0];
        }
        try {
            return ItemStack.serializeItemsAsBytes(storable);
        } catch (RuntimeException failure) {
            // Losing the inventory of one player must not cost them their session (Constitution VI).
            // Loud, because silently storing nothing looks exactly like an empty inventory.
            logger.log(
                    Level.SEVERE,
                    "[inventory] could not serialise the inventory of " + player.getUniqueId(),
                    failure);
            return new byte[0];
        }
    }

    /**
     * Puts stored contents back, into the slots they came from.
     *
     * <p>Does <b>not</b> clear first and does not touch a slot the blob has nothing for. The caller
     * clears, then restores, then applies the class equipment - in that order, so the equipment always
     * wins the slots it owns.
     *
     * @return whether anything was restored
     */
    public static boolean restore(Player player, byte[] contents, Logger logger) {
        return restore(player.getInventory(), contents, player, logger);
    }

    /** The ender chest half of {@link #restore}. */
    public static boolean restoreEnderChest(Player player, byte[] contents, Logger logger) {
        return restore(player.getEnderChest(), contents, player, logger);
    }

    private static boolean restore(
            Inventory target, byte[] contents, Player player, Logger logger) {
        if (contents.length == 0) {
            return false;
        }
        ItemStack[] stored;
        try {
            stored = ItemStack.deserializeItemsFromBytes(contents);
        } catch (RuntimeException failure) {
            // A blob written by a newer server, or a corrupted row. The player enters without their
            // items rather than not at all, and the row is left untouched for inspection.
            logger.log(
                    Level.SEVERE,
                    "[inventory] could not read the stored inventory of "
                            + player.getUniqueId()
                            + " - entering without it",
                    failure);
            return false;
        }
        int slots = Math.min(stored.length, target.getSize());
        boolean restored = false;
        for (int slot = 0; slot < slots; slot++) {
            ItemStack item = stored[slot];
            if (item == null || item.getType().isAir()) {
                continue;
            }
            target.setItem(slot, item);
            restored = true;
        }
        if (stored.length > target.getSize()) {
            // Only reachable if a future version shrinks the inventory. Saying so beats dropping items
            // without a word.
            logger.warning(
                    "[inventory] "
                            + (stored.length - target.getSize())
                            + " stored slot(s) of "
                            + player.getUniqueId()
                            + " do not fit this server version and were not restored");
        }
        return restored;
    }

    /** Whether this item belongs to a character rather than to the player carrying it. */
    private static boolean isBound(ItemStack item) {
        return BoundItemTag.isTagged(item);
    }
}
