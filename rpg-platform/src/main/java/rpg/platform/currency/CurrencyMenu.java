package rpg.platform.currency;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import rpg.core.currency.CurrencyMessageKeys;
import rpg.core.currency.LedgerEntry;
import rpg.core.message.Messages;
import rpg.core.session.PlayerCharacter;

/**
 * The window: pick a character, then read their ledger (ADR-028).
 *
 * <p><b>Provisional.</b> Display belongs to B13; until it exists this is built from pure vanilla
 * materials (ADR-005) after the pattern of {@code ClassSelectionMenu} in B07, and every string comes
 * from a message key (Constitution V).
 *
 * <p><b>The selection is not a formality.</b> A player has up to three characters and each has its
 * own balance (ADR-011). The window therefore shows three balances side by side and <b>never a
 * sum</b> - a total over three purses would be a number that does not exist anywhere in the game.
 *
 * <p><b>Reading is paged because the table is large.</b> Every page is one database query, so the
 * page size is a ceiling rather than a hint (FR-046a).
 *
 * <p>This class knows no rules: which characters exist is decided by the caller, what a click does by
 * the listener. It turns data into an inventory and a slot back into a meaning.
 */
public final class CurrencyMenu {

    /** One row for the selection: three characters at 2, 4 and 6, centred with a gap either side. */
    static final int SELECTION_SIZE = 9;

    static final int[] CHARACTER_SLOTS = {2, 4, 6};

    /** Six rows for the history: five of entries, the bottom one for navigation. */
    static final int HISTORY_SIZE = 54;

    static final int PREVIOUS_SLOT = 45;
    static final int NEXT_SLOT = 53;

    /** Where the bottom row starts - nothing above it is navigation. */
    static final int NAVIGATION_ROW = 45;

    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("dd.MM. HH:mm").withZone(ZoneOffset.UTC);

    private final Messages messages;
    private final int pageSize;

    public CurrencyMenu(Messages messages, int pageSize) {
        this.messages = Objects.requireNonNull(messages, "messages");
        if (pageSize <= 0 || pageSize > NAVIGATION_ROW) {
            throw new IllegalArgumentException(
                    "page size must be between 1 and "
                            + NAVIGATION_ROW
                            + ", but was "
                            + pageSize
                            + " - the bottom row carries the paging buttons");
        }
        this.pageSize = pageSize;
    }

    public int pageSize() {
        return pageSize;
    }

    /**
     * The first level: one entry per character, each with its own balance.
     *
     * @param balances balance per character; a character absent from the map is not loaded and shows
     *     what the repository said instead - the caller has resolved that already
     */
    public Inventory buildSelection(List<PlayerCharacter> characters, Map<UUID, Long> balances) {
        Objects.requireNonNull(characters, "characters");
        Objects.requireNonNull(balances, "balances");

        Inventory inventory =
                Bukkit.createInventory(
                        null,
                        SELECTION_SIZE,
                        Component.text(messages.get(CurrencyMessageKeys.MENU_TITLE_CHARACTERS)));
        for (int i = 0; i < characters.size() && i < CHARACTER_SLOTS.length; i++) {
            PlayerCharacter character = characters.get(i);
            long balance = balances.getOrDefault(character.characterId(), 0L);
            inventory.setItem(CHARACTER_SLOTS[i], characterEntry(character, balance));
        }
        return inventory;
    }

    /** Which character a click on {@code slot} means, if any. */
    public Optional<PlayerCharacter> characterAt(List<PlayerCharacter> characters, int slot) {
        for (int i = 0; i < characters.size() && i < CHARACTER_SLOTS.length; i++) {
            if (CHARACTER_SLOTS[i] == slot) {
                return Optional.of(characters.get(i));
            }
        }
        return Optional.empty();
    }

    /**
     * The second level: one page of a character's ledger, newest first.
     *
     * @param page zero-based
     * @param totalEntries how many exist altogether, so the buttons know where the ends are
     */
    public Inventory buildHistory(
            PlayerCharacter character, List<LedgerEntry> entries, int page, long totalEntries) {
        Objects.requireNonNull(character, "character");
        Objects.requireNonNull(entries, "entries");

        Inventory inventory =
                Bukkit.createInventory(
                        null,
                        HISTORY_SIZE,
                        Component.text(
                                messages.get(
                                        CurrencyMessageKeys.MENU_TITLE_HISTORY,
                                        Map.of("character", character.characterClass().name()))));

        if (entries.isEmpty()) {
            // Not an empty window with no explanation - a player who has never earned anything
            // should be told that, not left guessing whether the window is broken.
            inventory.setItem(22, plain(Material.PAPER, messages.get(CurrencyMessageKeys.MENU_EMPTY)));
            return inventory;
        }

        for (int i = 0; i < entries.size() && i < pageSize; i++) {
            inventory.setItem(i, historyEntry(entries.get(i)));
        }

        if (page > 0) {
            inventory.setItem(
                    PREVIOUS_SLOT,
                    plain(
                            Material.ARROW,
                            messages.get(CurrencyMessageKeys.MENU_PAGE_PREVIOUS)));
        }
        if ((long) (page + 1) * pageSize < totalEntries) {
            inventory.setItem(
                    NEXT_SLOT,
                    plain(Material.ARROW, messages.get(CurrencyMessageKeys.MENU_PAGE_NEXT)));
        }
        return inventory;
    }

    /** Whether this slot is the "previous page" button. */
    public static boolean isPreviousPage(int slot) {
        return slot == PREVIOUS_SLOT;
    }

    /** Whether this slot is the "next page" button. */
    public static boolean isNextPage(int slot) {
        return slot == NEXT_SLOT;
    }

    private ItemStack characterEntry(PlayerCharacter character, long balance) {
        ItemStack item = new ItemStack(Material.GOLD_INGOT);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(
                    Component.text(
                                    messages.get(
                                            CurrencyMessageKeys.MENU_CHARACTER_ENTRY,
                                            Map.of(
                                                    "character",
                                                    character.characterClass().name(),
                                                    "amount",
                                                    String.valueOf(balance))))
                            .color(NamedTextColor.GOLD)
                            .decoration(TextDecoration.ITALIC, false));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack historyEntry(LedgerEntry entry) {
        boolean credit = entry.direction() == LedgerEntry.Direction.CREDIT;
        ItemStack item = new ItemStack(credit ? Material.EMERALD : Material.REDSTONE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(
                    Component.text(
                                    messages.get(
                                            CurrencyMessageKeys.MENU_HISTORY_ENTRY,
                                            Map.of(
                                                    "direction", credit ? "+" : "-",
                                                    "amount", String.valueOf(entry.amount()),
                                                    "reason", entry.reason().name())))
                            .color(credit ? NamedTextColor.GREEN : NamedTextColor.RED)
                            .decoration(TextDecoration.ITALIC, false));
            meta.lore(lore(entry));
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * The detail an operator needs to settle a complaint: when, and what the balance was on either
     * side of it. Plus who did it, when somebody did.
     */
    private List<Component> lore(LedgerEntry entry) {
        List<Component> lore = new ArrayList<>(3);
        lore.add(grey(WHEN.format(entry.occurredAt())));
        lore.add(grey(entry.balanceBefore() + " -> " + entry.balanceAfter()));
        entry.actor().ifPresent(actor -> lore.add(grey("by " + actor)));
        return lore;
    }

    private static ItemStack plain(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(
                    Component.text(name)
                            .color(NamedTextColor.WHITE)
                            .decoration(TextDecoration.ITALIC, false));
            item.setItemMeta(meta);
        }
        return item;
    }

    private static Component grey(String text) {
        return Component.text(text)
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false);
    }
}
