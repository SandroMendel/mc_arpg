package rpg.platform.classes;

import java.util.Objects;
import java.util.Optional;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Reads and writes the binding tag on an item.
 *
 * <p><b>Persistent data container, not lore parsing.</b> The same choice ADR-004 made for items, and it
 * stays right here even though ADR-017 took the values out of the item: lore is display, and display is
 * something a client can be made to lie about.
 *
 * <p>The tag itself is defined by {@code rpg-core} - see {@code BoundEquipment.tagFor}. This class only
 * knows where to put the string, which is the whole reason the core can stay Bukkit-free.
 */
public final class BoundItemTag {

    /** One key for the whole block. Fixed, because it has to survive a restart to mean anything. */
    static final NamespacedKey KEY = Objects.requireNonNull(NamespacedKey.fromString("rpg:class_bound"));

    private BoundItemTag() {}

    /** Writes the tag. Called only while building a bound item - nothing else may create one. */
    static void write(ItemMeta meta, String tag) {
        Objects.requireNonNull(meta, "meta");
        Objects.requireNonNull(tag, "tag");
        meta.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, tag);
    }

    /**
     * Reads the tag, if there is one.
     *
     * <p>In the path of every inventory click, so it does the cheapest thing that can work: no meta
     * clone where the API allows avoiding it, no exception on an untagged item, and {@code empty} for
     * everything that is not ours - which is almost every item a player touches.
     */
    static Optional<String> read(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return Optional.empty();
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return Optional.empty();
        }
        PersistentDataContainer container = meta.getPersistentDataContainer();
        if (container.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(container.get(KEY, PersistentDataType.STRING));
    }

    /** Whether this item carries any binding tag at all. */
    static boolean isTagged(ItemStack item) {
        return read(item).isPresent();
    }
}
