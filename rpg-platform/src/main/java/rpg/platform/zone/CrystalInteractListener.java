package rpg.platform.zone;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import rpg.core.message.Messages;
import rpg.core.scheduler.WorldPosition;
import rpg.core.zone.CrystalPlacement;
import rpg.core.zone.Waypoints;
import rpg.core.zone.ZoneMessageKeys;
import rpg.core.zone.Zones;

/**
 * A right-click on a waypoint crystal (FR-046, FR-047, FR-048).
 *
 * <p><b>Temporary here.</b> Input belongs to B13 together with the window (ADR-032); this class
 * moves there with {@link WaypointMenu} and holds no rule of its own so that it can.
 *
 * <p><b>No block type is compared</b> (FR-046). A crystal is a structure somebody built, and the
 * configuration only describes the box in which a right-click counts. Hanging detection on a material
 * would make travelling depend on nobody ever mining the stone - and the first time somebody did, the
 * region would silently lose its waypoint with nothing in the log to say so.
 *
 * <p><b>The order of the checks is the whole performance story</b> (Constitution II). This event is
 * as busy as movement: it fires for every door, chest, crop and torch on the server. Everything
 * before the index lookup is free - a hand check and an action check - and the lookup itself is one
 * chunk-table read that answers "no crystal here" for practically every click (research.md R5).
 *
 * <p><b>Without {@code ignoreCancelled}, deliberately.</b> A {@link PlayerInteractEvent} on air is
 * cancelled from birth - Bukkit sets {@code useInteractedBlock} to DENY when there is no block, and
 * that is what {@code isCancelled()} measures. {@code AbilityTriggerListener} was caught by exactly
 * this. Here it matters less, because a crystal is always a block, but the condition that really
 * applies is written out below rather than delegated to a flag that means something else.
 */
public final class CrystalInteractListener implements Listener {

    /**
     * How long after opening the window a further right-click is ignored.
     *
     * <p><b>Not a preference, a necessity</b> (Constitution VI). A held-down right mouse button
     * repeats several times a second; without this, one press would build and open an inventory
     * every tick and the player would see a window that can never be interacted with. Half a second
     * is short enough to be invisible to somebody clicking on purpose.
     *
     * <p>Evaluated lazily on the next click rather than by a repeating task - the block has no
     * per-player task, and this is not worth being the first (FR-063).
     */
    static final long OPEN_COOLDOWN_MILLIS = 500L;

    private final Supplier<Zones> zones;
    private final Waypoints waypoints;
    private final Function<UUID, Optional<UUID>> characterOf;
    private final BiConsumer<Player, CrystalPlacement> openWindow;
    private final Messages messages;
    private final LongSupplier nowMillis;

    private final Map<UUID, Long> lastOpened = new ConcurrentHashMap<>();

    /**
     * @param characterOf which character this player is currently playing, or empty before the
     *     selection has decided (ADR-020)
     * @param openWindow what to do on a repeat click - kept as a callback so this class does not
     *     depend on the window it opens
     * @param nowMillis the clock, injectable so the rate limit can be tested without waiting
     */
    public CrystalInteractListener(
            Supplier<Zones> zones,
            Waypoints waypoints,
            Function<UUID, Optional<UUID>> characterOf,
            BiConsumer<Player, CrystalPlacement> openWindow,
            Messages messages,
            LongSupplier nowMillis) {
        this.zones = Objects.requireNonNull(zones, "zones");
        this.waypoints = Objects.requireNonNull(waypoints, "waypoints");
        this.characterOf = Objects.requireNonNull(characterOf, "characterOf");
        this.openWindow = Objects.requireNonNull(openWindow, "openWindow");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.nowMillis = Objects.requireNonNull(nowMillis, "nowMillis");
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onInteract(PlayerInteractEvent event) {
        // The off-hand fires a second event for the same press. Ignoring it here is what keeps one
        // click from unlocking and opening in the same breath.
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        // RIGHT_CLICK_BLOCK only, and the first draft got this wrong. It also accepted
        // RIGHT_CLICK_AIR and used the player's own position, which meant that standing inside the
        // trigger box and swinging at nothing operated the crystal - and every ability item, whose
        // whole input is a right-click into thin air, stopped working inside a safe core.
        // FullBootstrapTest caught it. A crystal is a structure: you click ON it, not near it.
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }

        Optional<CrystalPlacement> placement = crystalAt(event.getClickedBlock().getLocation());
        if (placement.isEmpty()) {
            // The overwhelmingly common case, and it has now cost one table read.
            return;
        }

        Player player = event.getPlayer();
        Optional<UUID> characterId = characterOf.apply(player.getUniqueId());
        if (characterId.isEmpty()) {
            return;
        }

        // The crystal is not a block to be used while this is happening - no chest opening behind
        // it, no block placed against it.
        event.setCancelled(true);
        handle(player, characterId.get(), placement.get());
    }

    /** Forgets a player's cooldown when they leave. Called by the session observer. */
    public void forget(UUID playerId) {
        lastOpened.remove(playerId);
    }

    private void handle(Player player, UUID characterId, CrystalPlacement placement) {
        String crystalKey = placement.crystal().key();

        if (waypoints.unlock(characterId, crystalKey)) {
            // The first right-click discovers and does nothing else (FR-047). Opening the window in
            // the same breath would hide the discovery behind an inventory the player did not ask
            // for, and the message announcing it would scroll past unread.
            player.sendMessage(
                    messages.get(
                            ZoneMessageKeys.WAYPOINT_UNLOCKED,
                            Map.of(
                                    "zone",
                                    messages.get(
                                            ZoneMessageKeys.nameOf(placement.zone().key())))));
            lastOpened.put(player.getUniqueId(), nowMillis.getAsLong());
            return;
        }

        if (!mayOpen(player.getUniqueId())) {
            return;
        }
        openWindow.accept(player, placement);
    }

    /**
     * Whether enough quiet has passed since this player's last click on a crystal.
     *
     * <p><b>The stamp is refreshed on every click, including a refused one</b>, and that is what
     * turns a time window into "one press, one window". A stamp that only moved on a successful open
     * would let a held button through again every {@link #OPEN_COOLDOWN_MILLIS} - the window would
     * rebuild itself under the player's cursor for as long as they held the mouse down. Refreshing
     * pushes the deadline out while the button is held, so the next window needs an actual pause.
     */
    private boolean mayOpen(UUID playerId) {
        long now = nowMillis.getAsLong();
        Long last = lastOpened.put(playerId, now);
        return last == null || now - last >= OPEN_COOLDOWN_MILLIS;
    }

    private Optional<CrystalPlacement> crystalAt(Location location) {
        WorldPosition position = BukkitPositions.of(location);
        return zones.get().crystalAt(position);
    }
}
