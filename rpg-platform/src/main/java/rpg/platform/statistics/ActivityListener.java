package rpg.platform.statistics;

import java.time.Clock;
import java.util.Objects;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import rpg.core.statistics.ActivityClock;

/**
 * Der <b>fünfte</b> Handler auf {@code PlayerMoveEvent} (R7) — und er tut weniger als jeder
 * vorhandene.
 *
 * <h2>Warum hier alles frei sein muss</h2>
 *
 * <p>{@code ZoneMovementListener} schreibt es für sich selbst auf, und es gilt hier genauso:
 * <em>„{@code PlayerMoveEvent} is one of the busiest events a server has — so everything before the
 * guard has to be free."</em> Vier Handler hängen bereits daran (Zonen, Doppelsprung, Landung,
 * Zauberunterbrechung). Ein fünfter ist nur vertretbar, weil er <b>eine Zuweisung</b> ist:
 *
 * <ul>
 *   <li><b>{@link EventPriority#MONITOR}</b> — dieser Zuhörer entscheidet nichts und ändert nichts.
 *   <li><b>Keine Bedingung</b> vor der Zuweisung. Kein Vergleich von Blockkoordinaten, kein Test
 *       auf „hat sich wirklich bewegt". Die Prüfung wäre teurer als das, was sie einspart.
 *   <li><b>Keine Allokation.</b> Kein neues Objekt, kein Optional, kein Lambda mit Fangvariablen.
 *   <li><b>Kein Schreibvorgang und keine Neuberechnung</b> (FR-014c2). Aus dem Zeitstempel folgt
 *       die Untätigkeit erst dann, wenn jemand danach fragt.
 * </ul>
 *
 * <p>Was man hier an Klugheit hinzufügt, bezahlt man bei fünfzig Spielern tausendfach je Sekunde.
 *
 * <h2>Bewegung allein reicht nicht (FR-014c1)</h2>
 *
 * <p>Wer eine Stunde lang in einer Truhe sortiert, im Chat schreibt und Fähigkeiten wirkt, ohne
 * einen Schritt zu gehen, spielt — und wäre nach fünf Minuten als untätig geführt, hinge alles an
 * der Bewegung. Deshalb erneuern auch Interaktion, Menüklick und Kommando den Zeitstempel. Der
 * Kampf tut es über {@link #touch(java.util.UUID)}, das der Kampfzuhörer ruft: {@code
 * DamageDealtEvent} ist ein Kernereignis und kommt nicht über Bukkit herein.
 */
public final class ActivityListener implements Listener {

    private final ActivityClock activity;
    private final Clock clock;

    public ActivityListener(ActivityClock activity, Clock clock) {
        this.activity = Objects.requireNonNull(activity, "activity");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onMove(PlayerMoveEvent event) {
        activity.touch(event.getPlayer().getUniqueId(), clock.instant());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        activity.touch(event.getPlayer().getUniqueId(), clock.instant());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onMenuClick(InventoryClickEvent event) {
        activity.touch(event.getWhoClicked().getUniqueId(), clock.instant());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        activity.touch(event.getPlayer().getUniqueId(), clock.instant());
    }

    /**
     * Der Weg für alles, was nicht über Bukkit hereinkommt — vor allem den Kampf.
     *
     * <p>{@code DamageDealtEvent} ist ein Kernereignis; es hier abzuhören hieße, denselben Zuhörer
     * an zwei Bussen zu registrieren. Stattdessen ruft der Zuhörer, der es ohnehin schon
     * entgegennimmt, diese Methode.
     */
    public void touch(java.util.UUID playerId) {
        activity.touch(playerId, clock.instant());
    }
}
