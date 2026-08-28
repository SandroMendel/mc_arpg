package rpg.platform.item;

import java.util.Objects;
import java.util.logging.Logger;

import rpg.core.combat.CombatDeathEvent;
import rpg.core.event.EventBus;
import rpg.core.item.DefaultGearConditions;

/**
 * Der Tod kostet — und zwar deutlich mehr als ein Kampf (FR-042, FR-043).
 *
 * <p><b>Der einzige Teil des Verschleißes, der an einem Ereignis hängt.</b> Der laufende Verschleiß
 * sitzt in {@link WearInterceptor}, weil er die Herkunft des Schadens und den Wert vor der Abwehr
 * braucht; der Tod braucht beides nicht. Er braucht nur die Todesursache — und B05 veröffentlicht
 * sie samt {@code playerVictim}, dem Haken, den dieser Block hier bereits vorfand.
 *
 * <p><b>Warum der Tod überhaupt etwas kostet.</b> Nach ADR-017 verliert ein Spieler beim Tod weder
 * Gegenstände noch Erfahrung. Damit ist der Verschleiß das Einzige, was ein Tod kostet — und wenn er
 * nichts kostete, gäbe es keinen Grund, ihn zu vermeiden.
 */
public final class WearListener {

    private final DefaultGearConditions conditions;
    private final Logger logger;

    public WearListener(DefaultGearConditions conditions, Logger logger) {
        this.conditions = Objects.requireNonNull(conditions, "conditions");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public void subscribeTo(EventBus events) {
        Objects.requireNonNull(events, "events").subscribe(CombatDeathEvent.class, this::onDeath);
    }

    void onDeath(CombatDeathEvent event) {
        if (!event.playerVictim() || event.victimCharacterId() == null) {
            // Eine gestorbene Kreatur traegt keine Ausruestung. Der Haken heisst genau deshalb
            // playerVictim und wurde von B05 fuer diesen Fall gesetzt.
            return;
        }
        conditions.onDeath(event.victimCharacterId(), event.cause());
        logger.fine(
                () ->
                        "[item] wear: death of character "
                                + event.victimCharacterId()
                                + " ("
                                + event.cause()
                                + ")");
    }
}
