package rpg.platform.mob;

import java.util.List;
import java.util.logging.Logger;

import org.bukkit.GameRule;
import org.bukkit.GameRules;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.world.WorldLoadEvent;

import rpg.core.mob.SpawnReasonPolicy;

/**
 * Schaltet Vanillas natuerliches Spawnen ab - ueberall (FR-018c).
 *
 * <p><b>Zwei Schichten, und die Reihenfolge ist der Punkt</b> (research.md R1). Schicht 1 haelt den
 * Spawner-Durchlauf selbst an: sieben Spielregeln je Welt, gesetzt beim Start und bei jedem
 * {@link WorldLoadEvent}. Das ist die billige Variante - es entsteht gar kein Kandidat. Schicht 2
 * ist der Riegel: {@link CreatureSpawnEvent} auf {@code HIGHEST}, der jeden nicht ausdruecklich
 * erlaubten Grund abbricht. Die Regeln decken keinen der gut vierzig {@code SpawnReason}-Werte
 * lueckenlos ab - Raids, Portale, Verstaerkung, Jockeys haengen an keiner Regel -, und genau die
 * faengt der Riegel. Er ist billig, gerade weil Schicht 1 die Masse schon abgehalten hat.
 *
 * <p><b>Absichtliches Setzen bleibt moeglich</b> (FR-018d): {@code CUSTOM} (unsere eigenen
 * Kreaturen, FR-018e), {@code COMMAND}, {@code SPAWNER_EGG}, {@code DISPENSE_EGG} - siehe
 * {@link SpawnReasonPolicy}.
 */
public final class VanillaSpawnSuppressor implements Listener {

    /** Die sieben Regeln, die den Spawner-Durchlauf je Welt anhalten (research.md R1). */
    private static final List<GameRule<Boolean>> RULES =
            List.of(
                    GameRules.SPAWN_MOBS,
                    GameRules.SPAWN_MONSTERS,
                    GameRules.SPAWN_PATROLS,
                    GameRules.SPAWN_PHANTOMS,
                    GameRules.SPAWN_WANDERING_TRADERS,
                    GameRules.SPAWN_WARDENS,
                    GameRules.SPAWNER_BLOCKS_WORK);

    private final Logger logger;

    public VanillaSpawnSuppressor(Logger logger) {
        this.logger = java.util.Objects.requireNonNull(logger, "logger");
    }

    /** Schicht 1 auf jede geladene Welt - beim Start. */
    public void applyTo(Server server) {
        int worlds = 0;
        for (World world : server.getWorlds()) {
            apply(world);
            worlds++;
        }
        logger.info(
                "[mob] phase=START state=SUPPRESSED - "
                        + RULES.size()
                        + " game rules set on "
                        + worlds
                        + " world(s); layer 2 (the event guard) catches the rest (FR-018c)");
    }

    /** Eine spaeter geladene Welt bekommt dieselben Regeln - sonst waere sie ausgenommen. */
    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        apply(event.getWorld());
    }

    /**
     * Schicht 2: bricht jeden nicht ausdruecklich erlaubten Spawn-Grund ab.
     *
     * <p>{@code HIGHEST}, damit andere Plugins zuerst entscheiden koennen - dieser Riegel ist die
     * letzte Instanz, nicht die erste. {@code ignoreCancelled}, weil ein bereits abgebrochenes
     * Ereignis nichts mehr zu unterdruecken hat.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!SpawnReasonPolicy.isAllowed(event.getSpawnReason().name())) {
            event.setCancelled(true);
        }
    }

    private void apply(World world) {
        for (GameRule<Boolean> rule : RULES) {
            world.setGameRule(rule, false);
        }
    }
}
