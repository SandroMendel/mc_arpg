package rpg.platform.classes;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import net.kyori.adventure.text.Component;
import rpg.core.classes.CharacterClassDefinition;
import rpg.core.classes.ClassMessageKeys;
import rpg.core.classes.ClassRegistry;
import rpg.core.message.Messages;
import rpg.core.session.CharacterClass;

/**
 * The selection window, built from vanilla materials only (ADR-005).
 *
 * <p>Nine slots, one row - the smallest inventory that holds three offers with a gap either side. No
 * resource pack, no custom model data, no client requirement.
 *
 * <p>The menu knows no rules. Which classes are on offer is decided by
 * {@code ClassSelection.available}, and what a click does by the listener. This class turns a set of
 * classes into an inventory and a slot back into a class.
 */
public final class ClassSelectionMenu {

    /** One row. Three offers at 2, 4 and 6 - centred, with a gap either side. */
    static final int SIZE = 9;

    static final int[] OFFER_SLOTS = {2, 4, 6};

    private final ClassRegistry registry;
    private final Messages messages;

    public ClassSelectionMenu(ClassRegistry registry, Messages messages) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    /**
     * Builds the window for exactly the classes still open to this account (FR-035).
     *
     * @throws IllegalArgumentException if more classes are offered than there are slots - impossible
     *     with three, but a fourth one later hits this instead of silently losing an offer
     */
    public Inventory build(Set<CharacterClass> available) {
        Objects.requireNonNull(available, "available");
        if (available.size() > OFFER_SLOTS.length) {
            throw new IllegalArgumentException(
                    "the menu has "
                            + OFFER_SLOTS.length
                            + " offer slots but "
                            + available.size()
                            + " classes were offered - widen the menu rather than dropping one");
        }
        Inventory inventory =
                Bukkit.createInventory(
                        null,
                        SIZE,
                        Component.text(messages.get(ClassMessageKeys.SELECTION_TITLE)));
        List<CharacterClass> ordered = ordered(available);
        for (int i = 0; i < ordered.size(); i++) {
            inventory.setItem(OFFER_SLOTS[i], offerItem(ordered.get(i)));
        }
        return inventory;
    }

    /** Which class a click on {@code slot} means, if any. */
    public Optional<CharacterClass> classAt(Set<CharacterClass> available, int slot) {
        List<CharacterClass> ordered = ordered(available);
        for (int i = 0; i < ordered.size() && i < OFFER_SLOTS.length; i++) {
            if (OFFER_SLOTS[i] == slot) {
                return Optional.of(ordered.get(i));
            }
        }
        return Optional.empty();
    }

    /**
     * Stable order, so the same account always sees the same class in the same slot.
     *
     * <p>Declaration order of the enum rather than the iteration order of the set: a plain
     * {@code Set} makes no promise about order, and a menu that shuffles between joins is a menu
     * players misclick.
     */
    private static List<CharacterClass> ordered(Set<CharacterClass> available) {
        List<CharacterClass> ordered = new ArrayList<>(available.size());
        for (CharacterClass id : CharacterClass.values()) {
            if (available.contains(id)) {
                ordered.add(id);
            }
        }
        return ordered;
    }

    private ItemStack offerItem(CharacterClass id) {
        CharacterClassDefinition definition = registry.definition(id);
        Material material = Material.matchMaterial(definition.menuMaterial());
        if (material == null) {
            // V12 lives here rather than in rpg-core: only the running server knows its materials,
            // and Constitution III.1 forbids asking Bukkit from the core.
            throw new IllegalStateException(
                    "class "
                            + id
                            + ": menu-material '"
                            + definition.menuMaterial()
                            + "' does not exist in this server version");
        }
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            // The display name is a message key, never text - "Berserker" lives in messages.yml
            // (ADR-019, Constitution V).
            meta.displayName(Component.text(messages.get(definition.displayNameKey())));
            item.setItemMeta(meta);
        }
        return item;
    }
}
