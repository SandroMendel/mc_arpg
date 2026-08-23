package rpg.platform.zone;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

import rpg.core.message.MessageKey;
import rpg.core.message.Messages;
import rpg.core.zone.CrystalPlacement;
import rpg.core.zone.Travel;
import rpg.core.zone.TravelResult;
import rpg.core.zone.Waypoints;
import rpg.core.zone.ZoneMessageKeys;
import rpg.core.zone.Zones;

/**
 * Drives the waypoint window: opening it, and turning a click into a journey (ADR-032).
 *
 * <p><b>Temporary here</b>, with {@link WaypointMenu}, and it moves to B13 with it. What makes that
 * possible is that it decides nothing about travel: it reads a slot, asks {@link Travel}, and says
 * what came back.
 *
 * <p><b>Everything is checked when clicked, not when the window opened.</b> A reload can drop a
 * region between the two, coins can be spent elsewhere in the same second, and combat can start. A
 * window built a moment ago is a picture of the past; only the click is now. That is why
 * {@code NO_SUCH_CRYSTAL} exists as an outcome at all.
 *
 * <p><b>Every click is cancelled first and interpreted afterwards.</b> The entries are display, not
 * items - the same rule {@code CurrencyMenuListener} follows, and what makes it true even for a click
 * nobody thought about.
 */
public final class WaypointMenuListener implements Listener {

    /** What a player has open, so a click can be given a meaning. */
    private record OpenView(UUID characterId, List<CrystalPlacement> crystals, String standingAt) {}

    private final WaypointMenu menu;
    private final Supplier<Zones> zones;
    private final Waypoints waypoints;
    private final Travel travel;
    private final Function<UUID, Optional<UUID>> characterOf;
    private final Messages messages;

    private final Map<UUID, OpenView> open = new ConcurrentHashMap<>();

    public WaypointMenuListener(
            WaypointMenu menu,
            Supplier<Zones> zones,
            Waypoints waypoints,
            Travel travel,
            Function<UUID, Optional<UUID>> characterOf,
            Messages messages) {
        this.menu = Objects.requireNonNull(menu, "menu");
        this.zones = Objects.requireNonNull(zones, "zones");
        this.waypoints = Objects.requireNonNull(waypoints, "waypoints");
        this.travel = Objects.requireNonNull(travel, "travel");
        this.characterOf = Objects.requireNonNull(characterOf, "characterOf");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    /**
     * Opens the selection for the crystal this player is standing at.
     *
     * <p>Handed to {@code CrystalInteractListener} as a callback, so the input side does not need to
     * know the window exists.
     */
    public void open(Player player, CrystalPlacement standingAt) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(standingAt, "standingAt");

        Optional<UUID> characterId = characterOf.apply(player.getUniqueId());
        if (characterId.isEmpty()) {
            return;
        }
        List<CrystalPlacement> crystals = zones.get().crystals();
        open.put(
                player.getUniqueId(),
                new OpenView(characterId.get(), crystals, standingAt.crystal().key()));
        player.openInventory(
                menu.build(
                        crystals,
                        waypoints.unlockedBy(characterId.get()),
                        standingAt.crystal().key()));
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

        // Cancel first, interpret second.
        event.setCancelled(true);

        Inventory clicked = event.getClickedInventory();
        if (clicked == null || !clicked.equals(event.getView().getTopInventory())) {
            return;
        }

        Optional<CrystalPlacement> chosen = menu.crystalAt(view.crystals(), event.getSlot());
        if (chosen.isEmpty()) {
            return;
        }
        String crystalKey = chosen.get().crystal().key();

        if (crystalKey.equals(view.standingAt())) {
            // Nobody pays for a journey to where they already are.
            say(player, ZoneMessageKeys.ENTRY_HERE, chosen.get());
            return;
        }
        if (!waypoints.isUnlocked(view.characterId(), crystalKey)) {
            // Asked again rather than read off the item: the picture is old, the unlock is not.
            say(player, ZoneMessageKeys.WAYPOINT_LOCKED, chosen.get());
            return;
        }

        TravelResult result =
                travel.travelTo(player.getUniqueId(), view.characterId(), crystalKey);
        if (result.isSuccess()) {
            player.closeInventory();
            say(player, ZoneMessageKeys.TRAVELLED, chosen.get());
            return;
        }
        // Every refusal carries its own text; the block never decides wording (Constitution V).
        say(player, result.messageKey(), chosen.get());
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

    /** Forgets a player's open window when they leave. Called by the session observer. */
    public void forget(UUID playerId) {
        open.remove(playerId);
    }

    private void say(Player player, MessageKey key, CrystalPlacement placement) {
        player.sendMessage(
                messages.get(
                        key,
                        Map.of(
                                "zone",
                                messages.get(ZoneMessageKeys.nameOf(placement.zone().key())),
                                "band-min",
                                String.valueOf(placement.zone().levelBand().min()),
                                "band-max",
                                String.valueOf(placement.zone().levelBand().max()),
                                "price",
                                String.valueOf(placement.crystal().price()))));
    }
}
