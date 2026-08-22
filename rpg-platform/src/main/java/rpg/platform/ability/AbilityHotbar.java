package rpg.platform.ability;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.google.common.collect.ImmutableMultimap;

import net.kyori.adventure.text.Component;
import rpg.core.ability.Ability;
import rpg.core.message.Messages;

/**
 * Puts the ability items into the hotbar and keeps them in step with what is unlocked (FR-055).
 *
 * <p><b>Slot 0 is B07's bound weapon</b>, and nothing here touches it. From slot 1 upwards comes one
 * item per active ability in unlock order, then the marker items of passives that carry one. How many
 * that is differs per class - warrior and mage have four actives, the rogue three (ADR-025) - so this
 * is a rule rather than a fixed table.
 *
 * <p><b>A slot for an ability that is not unlocked stays empty</b> (FR-056). Empty, not a greyed-out
 * placeholder: a placeholder is an item, and an item in a hotbar slot is something a player will try
 * to use.
 *
 * <p>The items carry <b>both</b> tags: B07's binding tag, which earns them the whole inventory lock
 * without new code (FR-057), and this block's ability tag, which says which ability the slot is.
 */
public final class AbilityHotbar {

    /** B07's bound weapon lives here. B08 starts one above it. */
    public static final int WEAPON_SLOT = 0;

    private static final int FIRST_ABILITY_SLOT = 1;
    private static final int HOTBAR_SIZE = 9;

    private final Messages messages;
    private final Logger logger;

    public AbilityHotbar(Messages messages, Logger logger) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * Lays out the hotbar for what this character has unlocked.
     *
     * <p>Called when a character enters play and on every unlock (FR-059). Rebuilding rather than
     * patching is deliberate: the state that results follows from the level alone, so there is no way
     * for a missed event to leave a slot wrong for the rest of a session.
     */
    public void layOut(Player player, List<Ability> unlocked) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(unlocked, "unlocked");

        clearAbilitySlots(player);

        int slot = FIRST_ABILITY_SLOT;
        // Actives first, in unlock order, then the passives that carry a marker. Sorting here rather
        // than trusting the caller keeps the layout stable however the list arrived.
        List<Ability> actives = unlocked.stream().filter(Ability::isActive).sorted(byId()).toList();
        List<Ability> markers =
                unlocked.stream()
                        .filter(ability -> !ability.isActive() && !ability.items().isEmpty())
                        .sorted(byId())
                        .toList();

        for (Ability ability : actives) {
            slot = place(player, slot, ability, ability.item());
        }
        for (Ability ability : markers) {
            // One slot per marker, not one per ability: the mage's Rise & Fall shows two, one for the
            // jump and one for the fall, and its three-way toggle is what those two make readable.
            for (String material : ability.items()) {
                slot = place(player, slot, ability, material);
            }
        }
    }

    /** Removes every ability item, leaving the weapon and whatever the player owns alone. */
    public void clearAbilitySlots(Player player) {
        for (int slot = FIRST_ABILITY_SLOT; slot < HOTBAR_SIZE; slot++) {
            ItemStack current = player.getInventory().getItem(slot);
            if (AbilityItemTag.isAbilityItem(current)) {
                player.getInventory().setItem(slot, null);
            }
        }
    }

    private int place(Player player, int slot, Ability ability, String material) {
        if (slot >= HOTBAR_SIZE) {
            // Cannot happen with the three shipped loadouts - the mage needs seven slots of nine - and
            // is logged rather than thrown, because losing one icon must not cost the player a session.
            logger.warning(
                    () -> "[abilities] no hotbar slot left for " + ability.id() + " - skipped");
            return slot;
        }
        ItemStack item = build(ability, material);
        if (item != null) {
            player.getInventory().setItem(slot, item);
        }
        return slot + 1;
    }

    private ItemStack build(Ability ability, String materialName) {
        Material material = Material.matchMaterial(materialName);
        if (material == null) {
            // V12 catches this at startup on a real server; here it must not take the login down.
            logger.warning(
                    () -> "[abilities] " + ability.id() + " names unknown material " + materialName);
            return null;
        }
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        meta.displayName(Component.text(messages.get(ability.displayNameKey())));

        // An ability item may be an axe. Without this it would carry the material's attack damage and
        // become a ninth value source - the trap ADR-017 and B07's FR-046 are about. Empty and NOT
        // null: null removes the override and restores the defaults (see BoundItemFactory).
        meta.setAttributeModifiers(ImmutableMultimap.of());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

        AbilityItemTag.write(meta, ability.id());
        // The binding tag is what makes B07's EquipmentLockListener refuse to move, drop or swap it -
        // FR-057 without a line of new locking code.
        rpg.platform.classes.BoundItemFactory.markBound(meta, "ability:" + ability.id());

        item.setItemMeta(meta);
        return item;
    }

    private static Comparator<Ability> byId() {
        // The unlock level is not on the definition - it lives on the class binding - so the id is the
        // stable order available here. The caller passes the list in unlock order and this only keeps
        // it deterministic if it did not.
        return Comparator.comparing(Ability::id);
    }
}
