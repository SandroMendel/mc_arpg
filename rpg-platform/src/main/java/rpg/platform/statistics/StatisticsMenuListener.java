package rpg.platform.statistics;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;

import rpg.core.statistics.Aggregation;
import rpg.core.statistics.Period;

/**
 * <b>Ein</b> Zuhörer für alle Fenster dieses Blocks (FR-045).
 *
 * <h2>Warum einer und nicht drei</h2>
 *
 * <p>B12 bekommt drei Fenster: die Rangliste, das eigene Profil und die Saisonwertung. Drei
 * Zuhörer wären die naheliegende Aufteilung — und jeder von ihnen müsste dieselbe Regel
 * durchsetzen: <b>hier wird nichts entnommen und nichts hineingelegt</b>. Beim dritten vergisst
 * es jemand, und dann ist ein Statistikfenster eine Kiste, aus der man Gegenstände nehmen kann.
 *
 * <p>Deshalb steht die Regel an einer Stelle: <b>jeder Klick in einem Fenster dieses Blocks wird
 * abgebrochen</b>, bevor irgendetwas anderes geprüft wird. Was ein Klick <em>bedeutet</em> —
 * umschalten, blättern —, entscheidet sich danach.
 *
 * <h2>Der Bestand ist eine Karte je Spieler, und er wird geleert</h2>
 *
 * <p>Beim Schließen des Fensters und beim Sitzungsende. Ohne das zweite wüchse die Karte die ganze
 * Serverlaufzeit lang — derselbe Grund, aus dem {@code VendorListener} und B09s Wegpunktmenü ein
 * {@code forget(playerId)} haben, das vom Sitzungsende aus gerufen wird und nicht von einem
 * eigenen Quit-Zuhörer.
 */
public final class StatisticsMenuListener implements Listener {

    /** Was ein Spieler gerade offen hat. */
    public record OpenView(Aggregation board, Period period) {}

    private final LeaderboardMenu menu;
    private final Clock clock;
    private final Map<UUID, OpenView> open = new HashMap<>();

    public StatisticsMenuListener(LeaderboardMenu menu, Clock clock) {
        this.menu = Objects.requireNonNull(menu, "menu");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Öffnet die Rangliste für einen Spieler. */
    public void openLeaderboard(Player player, Aggregation board, Period period) {
        open.put(player.getUniqueId(), new OpenView(board, period));
        player.openInventory(
                menu.build(board, period, player.getUniqueId(), clock.instant()));
    }

    /** Was dieser Spieler gerade offen hat — für Tests und für das Umschalten. */
    public Optional<OpenView> openView(UUID playerId) {
        return Optional.ofNullable(open.get(playerId));
    }

    /**
     * Jeder Klick in einem Fenster dieses Blocks wird abgebrochen.
     *
     * <p><b>Zuerst abbrechen, dann deuten.</b> Ein früher Rücksprung — „das ist nicht mein
     * Fenster" — vor dem Abbruch wäre der Weg, auf dem ein Gegenstand doch herausgenommen wird:
     * er lässt genau die Fälle durch, an die niemand gedacht hat.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!open.containsKey(event.getWhoClicked().getUniqueId())) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        open.remove(event.getPlayer().getUniqueId());
    }

    /**
     * Vergisst einen Spieler — vom Sitzungsende aus gerufen.
     *
     * <p>Ein eigener {@code PlayerQuitEvent}-Zuhörer wäre ein zweiter Weg in den
     * Sitzungslebenszyklus, den B01s Wächter zu Recht verbietet.
     */
    public void forget(UUID playerId) {
        open.remove(playerId);
    }

    /** Wie viele Fenster gerade offen sind — für Diagnose und Tests. */
    public int openCount() {
        return open.size();
    }
}
