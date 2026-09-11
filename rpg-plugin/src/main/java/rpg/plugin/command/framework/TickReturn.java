package rpg.plugin.command.framework;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import rpg.core.scheduler.Scheduler;
import rpg.core.scheduler.WorldPosition;

/**
 * Bringt das Ergebnis einer Abfrage zurück in den Tick (T025).
 *
 * <h2>Kein {@code join()}</h2>
 *
 * <p>{@code AuditLogRepository.between} und die Ranglisten geben ein {@link CompletableFuture}. Ein
 * {@code join()} darauf liefe im Server-Thread und hielte den Tick an, bis die Datenbank antwortet
 * — bei einer langsamen Abfrage sieht das für jeden Spieler aus wie ein hängender Server. Constitution I
 * verbietet es, und dieser Typ ist die Stelle, an der man es nicht mehr versehentlich tut.
 *
 * <h2>{@code runSyncAtLocation}, nicht {@code runSyncOnEntity}</h2>
 *
 * <p><b>Die Falle, die B10 einmal gekostet hat.</b> {@code runSyncOnEntity} aus einem asynchronen
 * Zusammenhang scheitert <em>still</em>: kann die Entität in diesem Moment nicht aufgelöst werden,
 * kommt ein bereits abgebrochener Handle zurück und die Aufgabe läuft nie — ohne Fehler, ohne
 * Logzeile. Genau das ist hier der Normalfall: die Antwort kommt Millisekunden bis Sekunden später,
 * und bis dahin kann der Spieler abgemeldet sein.
 *
 * <p>Deshalb wird die <b>Position beim Absetzen</b> festgehalten und {@code runSyncAtLocation}
 * benutzt. Die Position eines Spielers, der inzwischen weg ist, ist immer noch ein Ort, an dem der
 * Server tickt.
 *
 * <p>Für die Konsole gibt es keine Position. Sie bekommt die Ausgangswelt — irgendein Ort, an dem
 * getickt wird, mehr braucht es nicht: der Rückruf schreibt in eine Konsole und fasst nichts in der
 * Welt an.
 */
public final class TickReturn {

    private final Scheduler scheduler;
    private final Server server;
    private final Logger log;

    public TickReturn(Scheduler scheduler, Server server, Logger log) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.server = Objects.requireNonNull(server, "server");
        this.log = Objects.requireNonNull(log, "log");
    }

    /**
     * Wartet auf {@code pending} und übergibt das Ergebnis im Tick an {@code whenReady}.
     *
     * <p>Der Absender wird <b>nicht</b> festgehalten, um ihn später zu benutzen — nur seine
     * Position. Ob er dann noch da ist, entscheidet der Rückruf; ein abgemeldeter Spieler nimmt
     * keine Nachricht mehr entgegen, und das ist kein Fehler.
     */
    public <T> void deliver(
            CommandSender sender, CompletableFuture<T> pending, Consumer<T> whenReady) {

        WorldPosition where = positionOf(sender);

        pending.whenComplete(
                (result, failure) -> {
                    if (failure != null) {
                        // Eine gescheiterte Abfrage ist eine Betreibermeldung, kein Spielertext -
                        // sie gehoert ins Log, mit dem Grund. Der Absender sieht nichts; das ist
                        // die Luecke, die FR-032s Nachfolger fuellen muss, sobald es einen Text
                        // dafuer gibt.
                        log.log(Level.WARNING, "[command] Abfrage fehlgeschlagen", failure);
                        return;
                    }
                    scheduler.runSyncAtLocation(where, () -> whenReady.accept(result));
                });
    }

    private WorldPosition positionOf(CommandSender sender) {
        if (sender instanceof Player player) {
            Location at = player.getLocation();
            return new WorldPosition(
                    at.getWorld().getUID(), at.getX(), at.getY(), at.getZ());
        }
        World fallback = server.getWorlds().isEmpty() ? null : server.getWorlds().get(0);
        if (fallback == null) {
            throw new IllegalStateException(
                    "kein geladenes Welt-Objekt - ohne Welt gibt es keinen Tick, in den etwas"
                            + " zurueckkehren koennte");
        }
        Location spawn = fallback.getSpawnLocation();
        return new WorldPosition(
                fallback.getUID(), spawn.getX(), spawn.getY(), spawn.getZ());
    }
}
