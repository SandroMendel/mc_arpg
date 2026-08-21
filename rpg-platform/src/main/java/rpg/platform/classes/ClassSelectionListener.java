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
import rpg.core.session.PlayerCharacter;
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
    private final CharacterEntry entry;
    private final ClassSlotSource slots;
    private final SelectionTimeout timeout;
    private final Scheduler scheduler;
    private final Logger logger;

    public ClassSelectionListener(
            ClassSelection selection,
            ClassSelectionMenu menu,
            SessionRegistry sessions,
            NoCharacterGuardListener guard,
            CharacterEntry entry,
            ClassSlotSource slots,
            SelectionTimeout timeout,
            Scheduler scheduler,
            Logger logger) {
        this.selection = Objects.requireNonNull(selection, "selection");
        this.menu = Objects.requireNonNull(menu, "menu");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.guard = Objects.requireNonNull(guard, "guard");
        this.entry = Objects.requireNonNull(entry, "entry");
        this.slots = Objects.requireNonNull(slots, "slots");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * Called by the wiring once a session is gone.
     *
     * <p>No half-created character is left behind, because {@code choose} is the only path that
     * creates one (FR-037). All this has to do is stop holding a player who is no longer there, and
     * stop the clock that would otherwise kick someone who has already left.
     */
    public void onSessionEnded(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        guard.release(playerId);
        timeout.cancel(playerId);
    }

    /**
     * Opens the selection and holds the player until they choose.
     *
     * <p>On <b>every</b> join, not only for an account without a character (US1.4): the selection is
     * also how a player picks which of their characters to play, so a session that has not chosen yet
     * always ends up here. {@code needsSelection} is therefore about whether a choice has been made in
     * <em>this</em> session, not about whether the account owns anything.
     *
     * <p>Called by the wiring once a session is ready - not from a join handler here, see the class
     * comment.
     */
    public void openIfNeeded(Player player) {
        Objects.requireNonNull(player, "player");
        Optional<PlayerSession> session = sessions.find(player.getUniqueId());
        if (session.isEmpty() || !selection.needsSelection(session.get())) {
            guard.release(player.getUniqueId());
            timeout.cancel(player.getUniqueId());
            return;
        }
        guard.hold(player);
        player.openInventory(menu.build(slots.slotsFor(session.get())));
        // After the menu is up, and idempotent: reopening on every close must not push the limit out.
        timeout.start(player);
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
        Optional<CharacterClass> chosen =
                menu.classAt(slots.slotsFor(session.get()), event.getSlot());
        if (chosen.isEmpty()) {
            return;
        }
        choose(player, session.get(), chosen.get());
    }

    /**
     * The choice itself, reachable without an {@code InventoryClickEvent}.
     *
     * <p>Package-private rather than private so a test can exercise what follows a choice. MockBukkit
     * cannot construct an {@code InventoryClickEvent} - {@code SimpleInventoryViewMock.convertSlot} is
     * unimplemented and throws, which JUnit reports as <em>skipped</em> rather than failed. A test that
     * went through the event would therefore silently not run at all.
     */
    void choose(Player player, PlayerSession session, CharacterClass id) {
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

        // The character exists - created just now, or long since. Either way the player is not in the
        // game state yet: no stat holder, no level, no tiers, no equipment. Entering builds all of it.
        // Only then is the hold lifted - releasing first would put someone into the world with nothing
        // (ADR-020).
        PlayerCharacter character = result.character().orElseThrow();
        if (!enter(player, character)) {
            // The character is stored and will be offered again on the next join. Keeping the player in
            // the menu is the safe state; letting them out would be the unsafe one.
            openIfNeeded(player);
            return;
        }
        // The clock stops here and not before: everything above this line can put the player back into
        // the menu, and a limit that stopped on the attempt would never fire for someone stuck in one.
        timeout.cancel(player.getUniqueId());
        guard.release(player.getUniqueId());
        player.closeInventory();
    }

    /** Entering must not throw into the selection flow (Constitution VI, FR-031). */
    private boolean enter(Player player, PlayerCharacter character) {
        try {
            return entry.enter(player, character);
        } catch (RuntimeException failure) {
            logger.log(
                    Level.WARNING,
                    "[class] entering the game state as "
                            + character.characterId()
                            + " failed for "
                            + player.getUniqueId(),
                    failure);
            return false;
        }
    }
}
