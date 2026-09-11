package rpg.platform.ability;

import java.time.Duration;
import java.util.Objects;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import io.papermc.paper.event.player.PlayerStopUsingItemEvent;
import rpg.core.scheduler.EntityRef;
import rpg.core.scheduler.Scheduler;

/**
 * Haelt den Schild oben, ohne dass die Maustaste gedrueckt bleiben muss.
 *
 * <p><b>Das Problem, das dieser Zuhoerer loest.</b> Der Block des Warriors laeuft acht Sekunden und
 * ist von Anfang an {@code sustained: true} - der Zustand stimmte also, und die Milderung wirkte.
 * Die Haltung tat es nicht: {@code startUsingItem} setzt sie, aber Vanilla haelt einen Schild nur
 * so lange oben, wie die rechte Maustaste gedrueckt ist. Der Spieler klickt, laesst nach etwa
 * hundert Millisekunden los, sein Client meldet das Loslassen, und der Server nimmt die Haltung
 * herunter. Uebrig blieb ein Blinzeln, waehrend die Faehigkeit noch fast acht Sekunden lief.
 *
 * <p><b>Ein Ereignis, kein Taktgeber.</b> Die naheliegende Reparatur waere, die Haltung jede halbe
 * Sekunde neu zu setzen - das waere wiederkehrende Arbeit je Spieler fuer einen Fall, der genau
 * einmal je Block eintritt (Prinzip II). Papers {@code PlayerStopUsingItemEvent} sagt stattdessen
 * genau dann Bescheid, wenn es passiert. Wer keinen Block haelt, kostet keinen Gedanken.
 *
 * <p><b>Einen Takt spaeter, nicht sofort.</b> Das Ereignis faellt mitten in das Beenden; die Haltung
 * innerhalb desselben Aufrufs wieder zu setzen, hiesse gegen den Rest dieses Beendens anzuschreiben.
 * Ein Takt Abstand ist fuenfzig Millisekunden - unterhalb dessen, was ein Auge als Zucken erkennt -
 * und er faellt in die einzige Art Aufgabe, die dieses Projekt kennt: einmalig und an die Entitaet
 * gebunden (ADR-024).
 *
 * <p>Nur fuer den, der wirklich eine Faehigkeit haelt. Wer einen Bogen spannt oder isst, laesst
 * genauso los, und ein pauschales Festhalten waere ein Spieler, der seinen Bogen nicht mehr
 * loesen kann.
 */
public final class HeldPoseListener implements Listener {

    /**
     * Ein Servertakt.
     *
     * <p>Als Dauer und nicht als Tick-Zahl, weil der Scheduler dieses Projekts in Dauern spricht
     * (ADR-007) - und weil fuenfzig Millisekunden die Absicht besser sagen als eine Eins.
     */
    static final Duration NEXT_TICK = Duration.ofMillis(50L);

    private final AbilityFeedback feedback;
    private final Scheduler scheduler;

    public HeldPoseListener(AbilityFeedback feedback, Scheduler scheduler) {
        this.feedback = Objects.requireNonNull(feedback, "feedback");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    /**
     * Das Loslassen der Maustaste - und die Antwort darauf.
     *
     * <p>{@code MONITOR}, weil hier nichts entschieden und nichts verhindert wird: das Ereignis ist
     * ohnehin nicht abbrechbar, und was danach geschieht, ist eine Reaktion und kein Einspruch.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onStopUsingItem(PlayerStopUsingItemEvent event) {
        Player player = event.getPlayer();
        if (!feedback.shouldStayRaised(player.getUniqueId())) {
            return;
        }
        scheduler.runSyncOnEntityDelayed(
                new EntityRef(player.getUniqueId()),
                NEXT_TICK,
                // Noch einmal gefragt, nicht nur oben: zwischen dem Ereignis und diesem Takt kann die
                // Faehigkeit abgelaufen sein, und dann waere das Heben hier die Haltung, die niemand
                // mehr herunternimmt.
                () -> feedback.raiseAgain(player));
    }
}
