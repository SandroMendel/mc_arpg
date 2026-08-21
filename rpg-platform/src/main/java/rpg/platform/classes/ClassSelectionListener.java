package rpg.platform.classes;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;

import rpg.core.classes.ClassSelection;
import rpg.core.classes.ClassSelectionResult;
import rpg.core.scheduler.EntityRef;
import rpg.core.scheduler.Scheduler;
import rpg.core.session.CharacterClass;
import rpg.core.session.PlayerSession;
import rpg.core.session.SessionRegistry;

/**
 * Opens the selection, keeps it open, and turns a click into a choice (ADR-020).
 *
 * <p><b>This class does not handle {@code PlayerJoinEvent} or {@code PlayerQuitEvent}, on purpose.</b>
 * B03 owns the session lifecycle and enforces it with a test: the lifecycle has exactly one entry and
 * one exit (FR-007/FR-014), because a second unload path was a real bug once. So the entry points here
 * are plain methods - {@link #openIfNeeded(Player)} and {@link #onSessionEnded(UUID)} - called from the
 * wiring once a session is ready or gone. Owning a join handler here would have made B07 a second
 * entry to something that is allowed exactly one.
 *
 * <p><b>The reopen is one tick later.</b> Opening inside {@code InventoryCloseEvent} is unreliable:
 * the client is still processing the close, and Paper either drops the reopen or leaves the client in
 * an inconsistent state. The delay goes through the entity-bound scheduler from B01 - never the global
 * Bukkit scheduler (Constitution I, ADR-007).
 */
public final class ClassSelectionListener implements Listener {

    private final ClassSelection selection;
    private final ClassSelectionMenu menu;
    private final SessionRegistry sessions;
    private final NoCharacterGuardListener guard;
    private final Scheduler scheduler;
    private final Logger logger;

    public ClassSelectionListener(
            ClassSelection selection,
            ClassSelectionMenu menu,
            SessionRegistry sessions,
            NoCharacterGuardListener guard,
            Scheduler scheduler,
            Logger logger) {
        this.selection = Objects.requireNonNull(selection, "selection");
        this.menu = Objects.requireNonNull(menu, "menu");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.guard = Objects.requireNonNull(guard, "guard");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * Called by the wiring once a session is gone.
     *
     * <p>No half-created character is left behind, because {@code choose} is the only path that
     * creates one (FR-037). All this has to do is stop holding a player who is no longer there.
     */
    public void onSessionEnded(UUID playerId) {
        guard.release(Objects.requireNonNull(playerId, "playerId"));
    }

    /**
     * Opens the selection if this player has no character, and holds them while it is open.
     *
     * <p>Called by the wiring once a session is ready - not from a join handler here, see the class
     * comment.
     */
    public void openIfNeeded(Player player) {
        Objects.requireNonNull(player, "player");
        Optional<PlayerSession> session = sessions.find(player.getUniqueId());
        if (session.isEmpty() || !selection.needsSelection(session.get())) {
            guard.release(player.getUniqueId());
            return;
        }
        guard.hold(player);
        player.openInventory(menu.build(selection.available(session.get())));
    }

    /**
     * Every route out of the menu leads back into it (FR-033, US1.2).
     *
     * <p>Escape, the inventory key, a command that opens something else, a world change - they all
     * end as an {@code InventoryCloseEvent}, which is why one handler covers all four rather than one
     * per route.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (!guard.isHeld(player.getUniqueId())) {
            return;
        }
        scheduler.runSyncOnEntity(
                new EntityRef(player.getUniqueId()),
                () -> {
                    if (player.isOnline() && guard.isHeld(player.getUniqueId())) {
                        openIfNeeded(player);
                    }
                });
    }

    /** A click on an offer chooses that class; every other click in the menu does nothing. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        HumanEntity clicker = event.getWhoClicked();
        if (!(clicker instanceof Player player) || !guard.isHeld(player.getUniqueId())) {
            return;
        }
        // While the menu is open nothing may be moved - not the offers, not the player's own
        // inventory. The selection is the only interaction.
        event.setCancelled(true);

        Optional<PlayerSession> session = sessions.find(player.getUniqueId());
        if (session.isEmpty()) {
            return;
        }
        Inventory clicked = event.getClickedInventory();
        if (clicked == null || clicked.equals(player.getInventory())) {
            return;
        }
        Set<CharacterClass> available = selection.available(session.get());
        Optional<CharacterClass> chosen = menu.classAt(available, event.getSlot());
        if (chosen.isEmpty()) {
            return;
        }
        choose(player, session.get(), chosen.get());
    }

    private void choose(Player player, PlayerSession session, CharacterClass id) {
        selection
                .choose(session, id)
                .whenComplete(
                        (result, failure) ->
                                scheduler.runSyncOnEntity(
                                        new EntityRef(player.getUniqueId()),
                                        () -> finish(player, id, result, failure)));
    }

    /**
     * Back on the player's own tick: close the menu and release, or keep it open with a reason.
     *
     * <p>An exception here must not take the player with it (Constitution VI, FR-031) - it is logged
     * and the menu stays open, which is the safe state.
     */
    private void finish(
            Player player, CharacterClass id, ClassSelectionResult result, Throwable failure) {
        if (failure != null) {
            logger.log(
                    Level.WARNING,
                    "[class] choosing " + id + " for " + player.getUniqueId() + " failed",
                    failure);
            return;
        }
        if (!result.accepted()) {
            // The menu stays open; the reason travels as a value and is rendered where players are
            // addressed. Reopening refreshes the offers, so a class taken in the meantime disappears.
            logger.fine(() -> "[class] " + id + " refused: " + result.rejection().orElseThrow());
            openIfNeeded(player);
            return;
        }
        guard.release(player.getUniqueId());
        player.closeInventory();
    }
}
