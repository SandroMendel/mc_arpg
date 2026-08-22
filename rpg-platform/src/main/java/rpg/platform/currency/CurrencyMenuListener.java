package rpg.platform.currency;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

import rpg.core.currency.CoinLedger;
import rpg.core.currency.LedgerEntry;
import rpg.core.scheduler.EntityRef;
import rpg.core.scheduler.Scheduler;
import rpg.core.session.PlayerCharacter;

/**
 * Drives the currency window: clicks, paging, and the lock against taking anything out (ADR-028).
 *
 * <p><b>Every click in this window is cancelled.</b> The entries are display, not items - a ledger
 * row is a fact, and a fact cannot be dragged into a backpack. Cancelling first and interpreting
 * afterwards is what makes that true even for a click nobody thought about.
 *
 * <p><b>The database read never happens on the tick.</b> A page is fetched asynchronously and the
 * result handed back into the tick through the project's scheduler abstraction (Constitution I) -
 * never through shared mutable state.
 */
public final class CurrencyMenuListener implements Listener {

    /** What a player currently has open, so a click can be interpreted. */
    private record OpenView(
            UUID targetPlayerId, List<PlayerCharacter> characters, PlayerCharacter viewing, int page) {}

    private final CurrencyMenu menu;
    private final CoinLedger ledger;
    private final Scheduler scheduler;
    private final Logger logger;

    private final Map<UUID, OpenView> open = new ConcurrentHashMap<>();

    public CurrencyMenuListener(
            CurrencyMenu menu, CoinLedger ledger, Scheduler scheduler, Logger logger) {
        this.menu = Objects.requireNonNull(menu, "menu");
        this.ledger = Objects.requireNonNull(ledger, "ledger");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /** Opens the character selection for {@code viewer}, showing {@code target}'s characters. */
    public void openSelection(
            Player viewer,
            UUID targetPlayerId,
            List<PlayerCharacter> characters,
            Map<UUID, Long> balances) {
        Objects.requireNonNull(viewer, "viewer");
        open.put(viewer.getUniqueId(), new OpenView(targetPlayerId, characters, null, 0));
        viewer.openInventory(menu.buildSelection(characters, balances));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        OpenView view = open.get(player.getUniqueId());
        if (view == null) {
            return;
        }

        // Cancel first, interpret second. Anything else leaves a path where a click nobody
        // anticipated moves an item out of a window that has no items in it.
        event.setCancelled(true);

        Inventory clicked = event.getClickedInventory();
        if (clicked == null || !clicked.equals(event.getView().getTopInventory())) {
            // The player's own inventory. Cancelled anyway - while this window is open, it is not a
            // place to rearrange a backpack.
            return;
        }

        int slot = event.getSlot();
        if (view.viewing() == null) {
            menu.characterAt(view.characters(), slot)
                    .ifPresent(character -> showPage(player, view, character, 0));
            return;
        }
        if (CurrencyMenu.isPreviousPage(slot) && view.page() > 0) {
            showPage(player, view, view.viewing(), view.page() - 1);
        } else if (CurrencyMenu.isNextPage(slot)) {
            showPage(player, view, view.viewing(), view.page() + 1);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player
                && open.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        open.remove(event.getPlayer().getUniqueId());
    }

    /**
     * Reads one page and shows it.
     *
     * <p>Two queries - the page and the total - because the buttons have to know where the ends are.
     * Both off the tick; the window is opened from the tick the scheduler hands back.
     */
    private void showPage(Player player, OpenView view, PlayerCharacter character, int page) {
        UUID characterId = character.characterId();
        int limit = menu.pageSize();
        int offset = page * limit;

        ledger.historyOf(characterId, offset, limit)
                .thenCombine(ledger.historyCount(characterId), PageResult::new)
                .whenComplete(
                        (result, failure) -> {
                            if (failure != null) {
                                logger.log(
                                        Level.WARNING,
                                        "[currency] could not read the ledger page of " + characterId,
                                        failure);
                                return;
                            }
                            scheduler.runSyncOnEntity(
                                    new EntityRef(player.getUniqueId()),
                                    () -> {
                                        if (!player.isOnline()) {
                                            return;
                                        }
                                        open.put(
                                                player.getUniqueId(),
                                                new OpenView(
                                                        view.targetPlayerId(),
                                                        view.characters(),
                                                        character,
                                                        page));
                                        player.openInventory(
                                                menu.buildHistory(
                                                        character,
                                                        result.entries(),
                                                        page,
                                                        result.total()));
                                    });
                        });
    }

    private record PageResult(List<LedgerEntry> entries, long total) {}
}
