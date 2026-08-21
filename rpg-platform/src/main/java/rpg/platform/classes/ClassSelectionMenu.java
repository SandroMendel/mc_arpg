package rpg.platform.classes;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import rpg.core.classes.CharacterClassDefinition;
import rpg.core.classes.ClassMessageKeys;
import rpg.core.classes.ClassRegistry;
import rpg.core.classes.ClassSlot;
import rpg.core.classes.LadderSlot;
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
     * Builds the window: one slot per class, in a fixed order (US1.4).
     *
     * <p>Every class, every join - a slot the account plays shows what that character has reached, a
     * free slot shows what creating one would mean. The slot position is the class's position in the
     * enum and never moves, so a player's own character is always in the same place.
     *
     * @throws IllegalArgumentException if there are more classes than slots - impossible with three,
     *     but a fourth one later hits this instead of silently losing an entry
     */
    public Inventory build(List<ClassSlot> slots) {
        Objects.requireNonNull(slots, "slots");
        if (slots.size() > OFFER_SLOTS.length) {
            throw new IllegalArgumentException(
                    "the menu has "
                            + OFFER_SLOTS.length
                            + " slots but "
                            + slots.size()
                            + " classes were offered - widen the menu rather than dropping one");
        }
        Inventory inventory =
                Bukkit.createInventory(
                        null,
                        SIZE,
                        Component.text(messages.get(ClassMessageKeys.SELECTION_TITLE)));
        List<ClassSlot> ordered = ordered(slots);
        for (int i = 0; i < ordered.size(); i++) {
            inventory.setItem(OFFER_SLOTS[i], itemFor(ordered.get(i)));
        }
        return inventory;
    }

    /** Which class a click on {@code slot} means, if any. */
    public Optional<CharacterClass> classAt(List<ClassSlot> slots, int slot) {
        List<ClassSlot> ordered = ordered(slots);
        for (int i = 0; i < ordered.size() && i < OFFER_SLOTS.length; i++) {
            if (OFFER_SLOTS[i] == slot) {
                return Optional.of(ordered.get(i).characterClass());
            }
        }
        return Optional.empty();
    }

    /**
     * Stable order, so the same account always sees the same class in the same slot.
     *
     * <p>Declaration order of the enum rather than the caller's order: a menu that shuffles between
     * joins is a menu players misclick, and this one is now shown on every join.
     */
    private static List<ClassSlot> ordered(List<ClassSlot> slots) {
        List<ClassSlot> ordered = new ArrayList<>(slots.size());
        for (CharacterClass id : CharacterClass.values()) {
            slots.stream()
                    .filter(slot -> slot.characterClass() == id)
                    .findFirst()
                    .ifPresent(ordered::add);
        }
        return ordered;
    }

    private ItemStack itemFor(ClassSlot slot) {
        CharacterClassDefinition definition = registry.definition(slot.characterClass());
        Material material = Material.matchMaterial(definition.menuMaterial());
        if (material == null) {
            // V12 lives here rather than in rpg-core: only the running server knows its materials,
            // and Constitution III.1 forbids asking Bukkit from the core.
            throw new IllegalStateException(
                    "class "
                            + slot.characterClass()
                            + ": menu-material '"
                            + definition.menuMaterial()
                            + "' does not exist in this server version");
        }
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            // The display name is a message key, never text - "Berserker" lives in messages.yml
            // (ADR-019, Constitution V).
            meta.displayName(
                    Component.text(messages.get(definition.displayNameKey()))
                            .color(slot.isPlayed() ? NamedTextColor.GREEN : NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false));
            meta.lore(lore(slot, definition));
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * What the slot says about itself.
     *
     * <p>For a character in play: the level, how far each ladder has come, and when it was last
     * played - the three things that answer "which of my characters is this". The ladder lengths come
     * from the configuration, so a lengthened ladder is reflected without touching this.
     *
     * <p>For a free slot: that it is empty and what the class is for. Every line is a message key
     * (Constitution V); the numbers are placeholders in it.
     */
    private List<Component> lore(ClassSlot slot, CharacterClassDefinition definition) {
        List<Component> lines = new ArrayList<>(4);
        if (!slot.isPlayed()) {
            lines.add(line(messages.get(ClassMessageKeys.SLOT_EMPTY), NamedTextColor.DARK_GRAY));
            lines.add(line(messages.get(ClassMessageKeys.SLOT_CREATE), NamedTextColor.GRAY));
            return lines;
        }
        lines.add(
                line(
                        messages.get(
                                ClassMessageKeys.SLOT_LEVEL,
                                Map.of("level", Integer.toString(slot.level()))),
                        NamedTextColor.YELLOW));
        lines.add(
                line(
                        messages.get(
                                ClassMessageKeys.SLOT_TIERS,
                                Map.of(
                                        "armor", Integer.toString(slot.armorTier()),
                                        "armor_max",
                                                Integer.toString(
                                                        definition.ladder(LadderSlot.ARMOR).length()),
                                        "weapon", Integer.toString(slot.weaponTier()),
                                        "weapon_max",
                                                Integer.toString(
                                                        definition
                                                                .ladder(LadderSlot.WEAPON)
                                                                .length()))),
                        NamedTextColor.AQUA));
        slot.lastPlayedAt()
                .ifPresent(
                        at ->
                                lines.add(
                                        line(
                                                messages.get(
                                                        ClassMessageKeys.SLOT_LAST_PLAYED,
                                                        Map.of("when", ago(at))),
                                                NamedTextColor.DARK_GRAY)));
        lines.add(line(messages.get(ClassMessageKeys.SLOT_RESUME), NamedTextColor.GREEN));
        return lines;
    }

    /**
     * How long ago, coarsely.
     *
     * <p>Coarse on purpose: the point is telling two characters apart, not reporting a timestamp. Built
     * here rather than in the core because it is presentation - and deliberately not localised beyond
     * the unit letters, which is a limitation worth naming rather than hiding.
     */
    private static String ago(Instant at) {
        Duration since = Duration.between(at, Instant.now());
        if (since.isNegative() || since.toMinutes() < 1) {
            return "just now";
        }
        if (since.toHours() < 1) {
            return since.toMinutes() + "m";
        }
        if (since.toDays() < 1) {
            return since.toHours() + "h";
        }
        return since.toDays() + "d";
    }

    /** Lore lines are italic by default in vanilla, which reads as unfinished. */
    private static Component line(String text, NamedTextColor color) {
        return Component.text(text).color(color).decoration(TextDecoration.ITALIC, false);
    }
}
