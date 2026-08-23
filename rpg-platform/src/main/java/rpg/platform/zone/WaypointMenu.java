package rpg.platform.zone;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import rpg.core.message.Messages;
import rpg.core.zone.CrystalPlacement;
import rpg.core.zone.ZoneMessageKeys;

/**
 * The waypoint selection window (FR-048).
 *
 * <p><b>Temporary, and said so out loud</b> (ADR-032). Display belongs to B13; until that block
 * exists the window lives here, built from plain vanilla materials (ADR-005) after the pattern
 * {@code CurrencyMenu} set in ADR-028. What makes it able to leave later is that it knows no rule: it
 * turns data into an inventory and a slot back into a meaning. What a click does is the listener's
 * business, and whether a journey is allowed is {@code Travel}'s.
 *
 * <p><b>Locked crystals are shown, not hidden</b> (FR-048). A window that only shows what you already
 * have never tells you that there is more, and the shape of the world is exactly what a player is
 * meant to learn here. So a locked entry carries the name and the level band of its region
 * (FR-048a) - it is a destination, not a riddle.
 *
 * <p><b>The name comes from the same key as everywhere else</b> (FR-048b, SC-026). There is no second
 * place for zone names; {@link ZoneMessageKeys#nameOf} is it.
 */
public final class WaypointMenu {

    /** Nine slots per row, as in every Bukkit chest. */
    static final int ROW = 9;

    /** Six rows is the most a chest window can have. */
    static final int MAX_ROWS = 6;

    private final Messages messages;

    public WaypointMenu(Messages messages) {
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    /**
     * The crystals in the order the window puts them.
     *
     * <p>By level band rather than by configuration order: the window then reads like the path
     * through the world, and re-sorting {@code zones.yml} does not move everybody's familiar slots.
     */
    public static List<CrystalPlacement> order(List<CrystalPlacement> crystals) {
        List<CrystalPlacement> ordered = new ArrayList<>(crystals);
        ordered.sort(
                Comparator.<CrystalPlacement>comparingInt(
                                placement -> placement.zone().levelBand().min())
                        .thenComparing(placement -> placement.crystal().key()));
        return List.copyOf(ordered);
    }

    /**
     * Builds the window.
     *
     * @param crystals every crystal in the world - discovered and not
     * @param unlocked what this character has found
     * @param standingAt the crystal the player is standing at, or {@code null}
     */
    public Inventory build(
            List<CrystalPlacement> crystals, Set<String> unlocked, String standingAt) {
        Objects.requireNonNull(crystals, "crystals");
        Objects.requireNonNull(unlocked, "unlocked");

        List<CrystalPlacement> ordered = order(crystals);
        int size = size(ordered.size());
        Inventory inventory =
                Bukkit.createInventory(
                        null, size, Component.text(messages.get(ZoneMessageKeys.MENU_TITLE)));

        for (int slot = 0; slot < ordered.size() && slot < size; slot++) {
            inventory.setItem(slot, entry(ordered.get(slot), unlocked, standingAt));
        }
        return inventory;
    }

    /** Which crystal a click on {@code slot} means, if any. */
    public Optional<CrystalPlacement> crystalAt(List<CrystalPlacement> crystals, int slot) {
        List<CrystalPlacement> ordered = order(crystals);
        if (slot < 0 || slot >= ordered.size()) {
            return Optional.empty();
        }
        return Optional.of(ordered.get(slot));
    }

    /** As many full rows as the crystals need - at least one, at most six. */
    static int size(int crystalCount) {
        int rows = Math.max(1, Math.min(MAX_ROWS, (crystalCount + ROW - 1) / ROW));
        return rows * ROW;
    }

    private ItemStack entry(CrystalPlacement placement, Set<String> unlocked, String standingAt) {
        String crystalKey = placement.crystal().key();
        Map<String, String> values =
                Map.of(
                        "zone",
                        messages.get(ZoneMessageKeys.nameOf(placement.zone().key())),
                        "band-min",
                        String.valueOf(placement.zone().levelBand().min()),
                        "band-max",
                        String.valueOf(placement.zone().levelBand().max()),
                        "price",
                        String.valueOf(placement.crystal().price()));

        if (crystalKey.equals(standingAt)) {
            // Where you already are is not a destination. It still appears, because a gap in the
            // row would be harder to read than an entry that says "you are here".
            return item(
                    Material.AMETHYST_CLUSTER,
                    messages.get(ZoneMessageKeys.ENTRY_HERE, values),
                    NamedTextColor.AQUA);
        }
        if (unlocked.contains(crystalKey)) {
            return item(
                    Material.AMETHYST_SHARD,
                    messages.get(ZoneMessageKeys.ENTRY_UNLOCKED, values),
                    NamedTextColor.LIGHT_PURPLE);
        }
        return item(
                Material.GRAY_STAINED_GLASS_PANE,
                messages.get(ZoneMessageKeys.ENTRY_LOCKED, values),
                NamedTextColor.GRAY);
    }

    private static ItemStack item(Material material, String text, NamedTextColor colour) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(
                    Component.text(text).color(colour).decoration(TextDecoration.ITALIC, false));
            stack.setItemMeta(meta);
        }
        return stack;
    }
}
