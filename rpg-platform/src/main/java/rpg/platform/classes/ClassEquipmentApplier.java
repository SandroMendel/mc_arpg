package rpg.platform.classes;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import rpg.core.classes.BoundEquipment;
import rpg.core.classes.LadderSlot;
import rpg.core.classes.TierAppearance;

/**
 * Puts the equipment a character ought to be wearing where it belongs.
 *
 * <p><b>The tier is the truth, the item is the rendering</b> (FR-023). This class only ever writes in
 * that direction: it reads the reached tier, builds the items and puts them in place. It never reads an
 * item to learn a tier. Two properties fall out of that:
 *
 * <ul>
 *   <li>A bound item that went missing - by an admin, by a vanilla mechanic nobody foresaw - is simply
 *       there again on the next load. No repair routine, no reconciliation.
 *   <li>There is no way to gain a tier by tampering with an item (Constitution VI).
 * </ul>
 *
 * <p>Called on session load and after every advance. Not on a schedule: Constitution II forbids a
 * recurring task per player, and there is nothing to poll for - the tier only changes when something
 * changes it.
 */
public final class ClassEquipmentApplier {

    /** Which vanilla slot each armour piece goes to. Fixed, so the weapon slot stays predictable. */
    private static final Map<BoundItemFactory.ArmorPiece, EquipmentSlot> ARMOR_SLOTS =
            Map.of(
                    BoundItemFactory.ArmorPiece.HELMET, EquipmentSlot.HEAD,
                    BoundItemFactory.ArmorPiece.CHESTPLATE, EquipmentSlot.CHEST,
                    BoundItemFactory.ArmorPiece.LEGGINGS, EquipmentSlot.LEGS,
                    BoundItemFactory.ArmorPiece.BOOTS, EquipmentSlot.FEET);

    /**
     * The hotbar slot the class weapon lives in.
     *
     * <p>Fixed at 0 so it is predictable and so the lock has something definite to protect. B08 lays
     * out the remaining hotbar slots for the four active abilities; that this one is taken is the only
     * thing B07 asserts about the layout.
     */
    public static final int WEAPON_SLOT = 0;

    /**
     * How a tier is allowed to look after B11 has had its say.
     *
     * <p><b>Additive, like B11's other seam into this block.</b> With {@link #UNCHANGED} this class
     * behaves exactly as it did before B11 existed - the appearance that goes in is the appearance
     * that comes out. B11 uses it to swap the trim of a character who bought a colour and reached the
     * top of both ladders (FR-069, FR-070).
     *
     * <p><b>Why here and not in {@code BoundEquipment}.</b> The colour is B11's data and lives in
     * B11's table; the ladder is B07's. Asking the question at the moment the item is built keeps the
     * two apart - and keeps this block free of a dependency on the one above it.
     */
    @FunctionalInterface
    public interface AppearanceOverride {

        /** Leaves every appearance as the ladder defined it - the shape before B11. */
        AppearanceOverride UNCHANGED = (characterId, slot, appearance) -> appearance;

        TierAppearance apply(UUID characterId, LadderSlot slot, TierAppearance appearance);
    }

    private final BoundEquipment bound;
    private final BoundItemFactory factory;
    private final AppearanceOverride override;
    private final Logger logger;

    public ClassEquipmentApplier(
            BoundEquipment bound, BoundItemFactory factory, Logger logger) {
        this(bound, factory, AppearanceOverride.UNCHANGED, logger);
    }

    /** As above, plus B11's cosmetic override. */
    public ClassEquipmentApplier(
            BoundEquipment bound,
            BoundItemFactory factory,
            AppearanceOverride override,
            Logger logger) {
        this.bound = Objects.requireNonNull(bound, "bound");
        this.factory = Objects.requireNonNull(factory, "factory");
        this.override = Objects.requireNonNull(override, "override");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * Brings the player's worn equipment in line with the character's tiers.
     *
     * <p>Unconditional by design: comparing first and writing only on a difference would need a
     * reliable way to tell "the right item" from "an item that looks like it", and the cheap answer to
     * that is exactly the tampering route this class is meant to close. Writing four armour pieces and
     * one weapon costs nothing at the two moments it happens.
     *
     * @return {@code true} if the full set was applied; {@code false} if the character has no class, or
     *     if the weapon slot could not be freed without losing an item
     */
    public boolean apply(Player player, UUID characterId) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(characterId, "characterId");
        Optional<Map<LadderSlot, TierAppearance>> expected = bound.expectedFor(characterId);
        if (expected.isEmpty()) {
            // No class, so no equipment. Not an error: a player in the selection is in exactly this
            // state, and inventing a default set would write itself into the next save.
            return false;
        }
        try {
            PlayerInventory inventory = player.getInventory();
            applyArmor(
                    inventory,
                    characterId,
                    shown(characterId, LadderSlot.ARMOR, expected.get().get(LadderSlot.ARMOR)));
            return applyWeapon(
                    inventory,
                    characterId,
                    shown(characterId, LadderSlot.WEAPON, expected.get().get(LadderSlot.WEAPON)));
        } catch (RuntimeException failure) {
            // One character's broken configuration must not take the others with it
            // (Constitution VI). The player ends up without class equipment, which is visible and
            // recoverable, rather than with a half-applied set.
            logger.log(
                    Level.WARNING,
                    "[class] could not apply equipment for character " + characterId,
                    failure);
            return false;
        }
    }

    /**
     * The appearance as it will actually be worn.
     *
     * <p>One call, in one place. Asking the override at each of the five build sites would be
     * five chances for one of them to be forgotten - and a helmet in the tier trim next to a
     * chestplate in the bought one is the kind of wrong nobody reports as a bug.
     */
    private TierAppearance shown(UUID characterId, LadderSlot slot, TierAppearance ladder) {
        return override.apply(characterId, slot, ladder);
    }

    private void applyArmor(
            PlayerInventory inventory, UUID characterId, TierAppearance appearance) {
        String tag = bound.expectedTag(characterId, LadderSlot.ARMOR).orElseThrow();
        ARMOR_SLOTS.forEach(
                (piece, slot) ->
                        inventory.setItem(slot, factory.armorPiece(appearance, piece, tag)));
    }

    /**
     * Puts the class weapon in its slot - but never at the cost of an item already there.
     *
     * <p><b>The displaced item moves first.</b> Overwriting the slot and then trying to rehome what was
     * in it loses the item whenever the inventory is full, and ADR-018 has no route that destroys a
     * player's loot silently. So: if the slot holds something unbound and it cannot be moved, the
     * weapon is <b>not</b> applied and the next load tries again. The player sees a full inventory and
     * no class weapon - a state they can fix, and one the full-inventory warning already tells them
     * about.
     *
     * @return whether the weapon could be placed
     */
    private boolean applyWeapon(
            PlayerInventory inventory, UUID characterId, TierAppearance appearance) {
        String tag = bound.expectedTag(characterId, LadderSlot.WEAPON).orElseThrow();
        ItemStack weapon = factory.weapon(appearance, tag);
        ItemStack displaced = inventory.getItem(WEAPON_SLOT);

        if (isEmpty(displaced) || BoundItemTag.isTagged(displaced)) {
            // Empty, or the previous tier's weapon - nothing to preserve either way.
            inventory.setItem(WEAPON_SLOT, weapon);
            return true;
        }

        // Something of the player's is in the way. The weapon goes in FIRST, so the slot is occupied
        // while the displaced item looks for a home - otherwise addItem would put it straight back
        // into the slot just freed and the next write would destroy it. That mistake is easy to make
        // and impossible to see afterwards.
        inventory.setItem(WEAPON_SLOT, weapon);
        Map<Integer, ItemStack> leftover = inventory.addItem(displaced);
        if (!leftover.isEmpty()) {
            // No room anywhere. The player's item wins: it goes back exactly where it was, the weapon
            // waits for the next load, and nothing is dropped (ADR-018).
            inventory.setItem(WEAPON_SLOT, displaced);
            logger.fine(
                    () ->
                            "[class] weapon slot occupied and inventory full for "
                                    + characterId
                                    + " - kept the player's item, weapon retries on the next load");
            return false;
        }
        return true;
    }

    /** MockBukkit and Paper report an empty slot as AIR rather than null, so both count as empty. */
    private static boolean isEmpty(ItemStack item) {
        return item == null || item.getType().isAir();
    }
}
