package rpg.platform.classes;

import java.util.Objects;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;

import com.google.common.collect.ImmutableMultimap;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.text.Component;
import rpg.core.classes.LadderSlot;
import rpg.core.classes.TierAppearance;
import rpg.core.message.Messages;

/**
 * Builds the actual items a tier stands for - and suppresses what vanilla would otherwise add.
 *
 * <p><b>The important part is the suppression.</b> A vanilla weapon carries its own attribute
 * modifiers: a sword adjusts attack speed differently from a spear. The bridge to the vanilla
 * attributes sets only the <b>base</b> value, so without doing something here the weapon type would be
 * an unmodelled ninth stat source, and ADR-008 knows eight (FR-046).
 *
 * <p>Two traps, both avoided deliberately:
 *
 * <ol>
 *   <li>{@code setAttributeModifiers(null)} is the <b>opposite</b> of empty. Null removes the
 *       <i>override</i> and thereby restores the material's defaults. The difference between "no
 *       modifiers" and "no override" is exactly the one that matters - the same class of mistake
 *       ADR-016 recorded for {@code Double.NaN} as a sentinel.
 *   <li>{@code ItemFlag.HIDE_ATTRIBUTES} only affects the tooltip. The modifier stays active. Taking
 *       the flag for neutralisation builds a bug that passes "looks right" and computes wrong. The flag
 *       is set anyway - but for display, after the modifiers are actually gone.
 * </ol>
 */
public final class BoundItemFactory {

    /** The four vanilla armour slots. An armour tier names a set; these are its pieces. */
    public enum ArmorPiece {
        HELMET,
        CHESTPLATE,
        LEGGINGS,
        BOOTS
    }

    private final Messages messages;

    public BoundItemFactory(Messages messages) {
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    /**
     * One piece of armour for a tier.
     *
     * @throws IllegalStateException if the material does not exist in this server version - that is
     *     V12, and it can only be checked here (Constitution III.1)
     */
    public ItemStack armorPiece(TierAppearance appearance, ArmorPiece piece, String tag) {
        Objects.requireNonNull(appearance, "appearance");
        Objects.requireNonNull(piece, "piece");
        String materialName = appearance.material() + "_" + piece.name();
        ItemStack item = new ItemStack(materialOf(materialName));
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            throw new IllegalStateException(materialName + " carries no item meta");
        }
        applyColour(meta, appearance);
        applyTrim(meta, appearance);
        neutraliseVanillaModifiers(meta);
        makeIndestructible(meta);
        BoundItemTag.write(meta, tag);
        item.setItemMeta(meta);
        return item;
    }

    /** The weapon of a tier. Its material is a full item name, not a set. */
    public ItemStack weapon(TierAppearance appearance, String tag) {
        Objects.requireNonNull(appearance, "appearance");
        ItemStack item = new ItemStack(materialOf(appearance.material()));
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            throw new IllegalStateException(appearance.material() + " carries no item meta");
        }
        applyTrim(meta, appearance);
        neutraliseVanillaModifiers(meta);
        makeIndestructible(meta);
        BoundItemTag.write(meta, tag);
        item.setItemMeta(meta);
        return item;
    }

    /** Convenience for a slot: armour needs four pieces, a weapon one. */
    public ItemStack[] itemsFor(TierAppearance appearance, LadderSlot slot, String tag) {
        if (slot == LadderSlot.WEAPON) {
            return new ItemStack[] {weapon(appearance, tag)};
        }
        ArmorPiece[] pieces = ArmorPiece.values();
        ItemStack[] items = new ItemStack[pieces.length];
        for (int i = 0; i < pieces.length; i++) {
            items[i] = armorPiece(appearance, pieces[i], tag);
        }
        return items;
    }

    /**
     * FR-046 - an explicitly <b>empty, non-null</b> modifier set replaces the material's defaults.
     *
     * <p>After that the flag hides the now-empty attribute section from the tooltip. Order matters only
     * for readability; the flag has no effect on the values either way, which is precisely why it is
     * not a substitute for this call.
     */
    private static void neutraliseVanillaModifiers(ItemMeta meta) {
        meta.setAttributeModifiers(ImmutableMultimap.of());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
    }

    /**
     * Takes wear out of the equation: class equipment does not degrade.
     *
     * <p>It is part of the character, not an item the player owns (ADR-017/ADR-018). A sword that broke
     * left the warrior unarmed with no way to replace it - the ladder is the only source of weapons and
     * dropping or crafting one is refused. Only a relogin brought it back, because the applier rebuilds
     * the set on every session.
     *
     * <p>Durability is also the wrong lever here for a second reason: the tier carries the numbers, and
     * a damaged item would quietly weaken a character in a way no attribute reflects. If wear is ever
     * wanted as a mechanic, it belongs to the tier, not to the item stack.
     */
    private static void makeIndestructible(ItemMeta meta) {
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
    }

    private void applyColour(ItemMeta meta, TierAppearance appearance) {
        if (!appearance.hasColor()) {
            return;
        }
        if (!(meta instanceof LeatherArmorMeta leather)) {
            // The schema already refuses a colour on a material that cannot be dyed (FR-016b). Reaching
            // here means the two checks disagree, and silently ignoring it would make the mage's tiers
            // indistinguishable in play while every test stays green.
            throw new IllegalStateException(
                    appearance.material() + " cannot be dyed, but a colour was configured");
        }
        leather.setColor(Color.fromRGB(appearance.rgb().orElseThrow()));
    }

    private void applyTrim(ItemMeta meta, TierAppearance appearance) {
        if (!appearance.hasTrim()) {
            return;
        }
        if (!(meta instanceof ArmorMeta armor)) {
            throw new IllegalStateException(
                    appearance.material() + " cannot carry a trim, but one was configured");
        }
        // RegistryAccess, not the deprecated Registry.TRIM_* constants: those are on their way out of
        // the API, and pinning to them would make the next Paper bump a code change.
        TrimMaterial material =
                lookup(
                        RegistryAccess.registryAccess().getRegistry(RegistryKey.TRIM_MATERIAL),
                        appearance.trimMaterialName().orElseThrow(),
                        "trim-material");
        TrimPattern pattern =
                lookup(
                        RegistryAccess.registryAccess().getRegistry(RegistryKey.TRIM_PATTERN),
                        appearance.trimPatternName().orElseThrow(),
                        "trim-pattern");
        armor.setTrim(new ArmorTrim(material, pattern));
    }

    private static <T extends org.bukkit.Keyed> T lookup(
            Registry<T> registry, String name, String field) {
        NamespacedKey key = NamespacedKey.fromString(name.toLowerCase(java.util.Locale.ROOT));
        T value = key == null ? null : registry.get(key);
        if (value == null) {
            throw new IllegalStateException(
                    field + " '" + name + "' does not exist in this server version");
        }
        return value;
    }

    private static Material materialOf(String name) {
        Material material = Material.matchMaterial(name);
        if (material == null) {
            // V12 - only the running server knows its materials.
            throw new IllegalStateException(
                    "material '" + name + "' does not exist in this server version");
        }
        return material;
    }

    /** Unused for now; kept so a display name can be added without touching the call sites. */
    Component nameOf(rpg.core.message.MessageKey key) {
        return Component.text(messages.get(key));
    }
}
