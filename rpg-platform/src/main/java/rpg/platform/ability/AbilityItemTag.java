package rpg.platform.ability;

import java.util.Objects;
import java.util.Optional;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Which ability an item on the hotbar triggers.
 *
 * <p><b>A second tag next to B07's {@code BoundItemTag}, not a replacement for it.</b> That one says
 * the item belongs to the character and must not be moved, and it earns B08 the whole inventory lock
 * without a line of new code (FR-057). This one says <em>which</em> ability the slot carries.
 *
 * <p><b>The ability is never derived from the material</b> (FR-058). Two abilities may share one, and
 * more importantly an item obtained some other way must grant nothing - the item is input, never
 * authority. A goat horn found in a chest is a goat horn.
 *
 * <p>Read in the path of every right-click, so it does the cheapest thing that can work: no meta
 * clone where the API allows avoiding it, and {@code empty} for everything that is not ours - which
 * is almost every item a player holds.
 */
public final class AbilityItemTag {

    /** Fixed, because it has to survive a restart to mean anything. */
    static final NamespacedKey KEY = Objects.requireNonNull(NamespacedKey.fromString("rpg:ability_id"));

    private AbilityItemTag() {}

    /** Writes the id. Called only while building a hotbar item - nothing else may create one. */
    static void write(ItemMeta meta, String abilityId) {
        Objects.requireNonNull(meta, "meta");
        Objects.requireNonNull(abilityId, "abilityId");
        meta.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, abilityId);
    }

    /** The ability this item triggers, or empty if it is not one of ours. */
    public static Optional<String> read(ItemStack item) {
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

    /** Whether this item is an ability item at all. */
    public static boolean isAbilityItem(ItemStack item) {
        return read(item).isPresent();
    }
}
