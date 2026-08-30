package rpg.platform.ui;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;

/**
 * Klicks in der Charakterübersicht — und der Charakterwechsel.
 *
 * <h2>Jeder Klick wird abgefangen</h2>
 *
 * <p>Die Übersicht ist <b>zum Lesen da</b>. Ohne diesen Listener könnte ein Spieler die Attribute
 * herausnehmen und behalten: ein Fenster ist in Vanilla ein Inventar, und ein Inventar gibt her,
 * was man anklickt. Das ist keine theoretische Lücke — es ist der erste Fehler, den jedes
 * Menü-Plugin macht.
 *
 * <h2>Beim Charakterwechsel wird geschlossen, nicht neu aufgebaut</h2>
 *
 * <p>FR-055. Der Inhalt gehörte dann einem Charakter, den der Spieler nicht mehr spielt.
 *
 * <p><b>Ein stiller Neuaufbau ist der plausiblere Fehler</b>, und deshalb steht die Entscheidung
 * ausdrücklich in der Spec: es sähe aus, als hätte sich der Server verrechnet — der Spieler klickt
 * weiter, ohne zu merken, dass er woanders ist. Schließen ist die einzige Antwort, die er
 * <em>bemerkt</em>.
 */
public final class CharacterSheetListener implements Listener {

    private final CharacterSheetMenu menu;

    /** Wer die Übersicht gerade offen hat. */
    private final Set<UUID> open = ConcurrentHashMap.newKeySet();

    public CharacterSheetListener(CharacterSheetMenu menu) {
        this.menu = Objects.requireNonNull(menu, "menu");
    }

    /** Merkt sich, dass dieser Spieler die Übersicht offen hat. */
    public void opened(UUID playerId) {
        open.add(Objects.requireNonNull(playerId, "playerId"));
    }

    /** Ob dieser Spieler sie gerade offen hat. */
    public boolean hasOpen(UUID playerId) {
        return open.contains(playerId);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        HumanEntity who = event.getWhoClicked();
        if (!(who instanceof Player player) || !open.contains(player.getUniqueId())) {
            return;
        }
        // Jeder Klick, nicht nur die auf belegte Plaetze: auch Shift-Klick aus dem eigenen Inventar
        // und das Ablegen auf einen leeren Platz gehen durch dieses Ereignis.
        event.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            open.remove(player.getUniqueId());
        }
    }

    /**
     * Der Spieler hat den Server verlassen.
     *
     * <p><b>Kein {@code PlayerQuitEvent}-Handler</b>, und das ist keine Nachlässigkeit: B03 besitzt
     * den Sitzungslebenszyklus und lässt für dieses Ereignis <b>genau einen</b> Handler zu (FR-007)
     * — {@code FullBootstrapTest} hält das fest. Ein zweiter wäre der Weg, auf dem ein doppeltes
     * Laden oder Entladen entsteht, ohne dass irgendetwas falsch aussieht.
     *
     * <p>Der vorgesehene Weg hinein ist {@code SessionObserver.onSessionEnded}, und die Verdrahtung
     * ruft das hier von dort. Dieselbe Regel, der B09 mit seinem Zonen-Tracker folgt.
     */
    public void sessionEnded(UUID playerId) {
        // Beides raeumen: das offene Fenster UND den Zwischenspeicher. Ein Eintrag, der stehen
        // bleibt, ist ein Leck, das erst nach Stunden auffaellt - dieselbe Sorte wie bei der
        // Bossbar (FR-004c).
        open.remove(playerId);
        menu.forget(playerId);
    }

    /**
     * Der Spieler hat den Charakter gewechselt.
     *
     * <p>Wird von der Verdrahtung gerufen, nicht von einem Bukkit-Ereignis: welcher Charakter aktiv
     * ist, entscheidet B03, und diese Klasse fragt es nicht selbst nach (FR-074).
     */
    public void characterSwitched(Player player) {
        UUID playerId = player.getUniqueId();
        menu.forget(playerId);
        if (open.remove(playerId)) {
            // SCHLIESSEN und nicht neu aufbauen (FR-055).
            player.closeInventory();
        }
    }

    /** Wie viele Fenster gerade offen sind — für den Test gegen ein Leck. */
    int openCount() {
        return open.size();
    }
}
