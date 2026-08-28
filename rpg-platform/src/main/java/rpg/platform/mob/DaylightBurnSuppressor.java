package rpg.platform.mob;

import org.bukkit.entity.Mob;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;

/**
 * Verhindert, dass eigene Kreaturen tagsueber in der Sonne verbrennen.
 *
 * <p><b>Gefunden auf dem echten Server bei T112.</b> Der Boss verbrannte binnen weniger Minuten
 * nach seinem Respawn, bevor ein Spieler ihn erreichen konnte - derselbe Mechanismus fraess die
 * ganze Session ueber unbemerkt an der gewoehnlichen Horde: fast jeder "burned to death"-Eintrag im
 * Server-Log war eine unserer eigenen Kreaturen (`base: ZOMBIE`/`HUSK`/`SKELETON`/`WITHER_SKELETON`
 * deckt fast den ganzen Bestand in `mobs.yml` ab), lange bevor irgendein Spieler in ihre Naehe kam.
 * Kein Pruefschritt in `quickstart.md` fragt danach - nur der laufende Server zeigt es.
 *
 * <p><b>Jede Entzuendung, nicht nur die Sonne.</b> {@link EntityCombustEvent}s Unterklassen fuer
 * Lava, einen Feuerblock oder eine andere Entitaet teilen sich dieselbe Handler-Liste mit der
 * Oberklasse - ein Zuhoerer auf {@code EntityCombustEvent} faengt sie alle ab. Eine Unterscheidung
 * waere ohnehin nur Mehraufwand ohne Wirkung: der eigentliche Schaden laeuft nie ueber die
 * Entzuendung selbst, sondern ueber B05s Pipeline ({@code FIRE_TICK} ist wie jede andere
 * Vanilla-Schadensursache auf null gesetzt und umgeleitet, {@code VanillaDamageListener}) - das
 * Unterdruecken der Entzuendung nimmt nur die zusaetzliche, unkontrollierte Brenn-Animation samt
 * Vanilla-eigenem Sekundenschaden weg.
 */
public final class DaylightBurnSuppressor implements Listener {

    @EventHandler
    public void onCombust(EntityCombustEvent event) {
        if (event.getEntity() instanceof Mob mob && MobKindTag.isOurs(mob)) {
            event.setCancelled(true);
        }
    }
}
