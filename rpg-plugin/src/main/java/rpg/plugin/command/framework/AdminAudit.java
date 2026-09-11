package rpg.plugin.command.framework;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import rpg.core.persistence.AuditEntry;
import rpg.core.persistence.AuditLogRepository;

/**
 * <b>Die eine Stelle, die ins Audit-Log schreibt</b> (T022, FR-028, FR-031).
 *
 * <p>Das Log ist seit B02 append-only — es gibt kein {@code update} und kein {@code delete}, und
 * das Fehlen dieser Methoden <em>ist</em> die Zusage. Diese Klasse ist das Gegenstück dazu auf der
 * Schreibseite: nicht „jedes Admin-Kommando schreibt brav mit", sondern „es gibt genau einen Weg
 * hinein, und ein Architekturtest schlägt an, sobald aus B14 heraus ein zweiter entsteht".
 *
 * <h2>Die Konsole ist ein Handelnder</h2>
 *
 * <p>Sie bekommt die feste Null-UUID {@code 00000000-0000-0000-0000-000000000000} — das Muster
 * stammt aus {@code XpCommand.CONSOLE} und steht hier zum zweiten Mal, damit es nicht bei jedem
 * neuen Kommando neu erfunden wird.
 *
 * <p><b>Warum nicht „kein Handelnder"?</b> Weil das Log dann eine Zeile trüge, in der niemand etwas
 * getan hat. Die Konsole <em>ist</em> jemand: wer Zugang zu ihr hat, hat ihn bekommen, und die feste
 * Null-UUID macht genau diese Zeilen greppbar, statt sie als Lücke zu tarnen.
 */
public final class AdminAudit {

    /**
     * Wen das Log nennt, wenn die Konsole gehandelt hat.
     *
     * <p>Fest und nicht zufällig: eine erzeugte UUID je Serverstart wäre nach dem dritten Neustart
     * drei verschiedene „Betreiber", und niemand könnte die Zeilen zusammenziehen.
     */
    public static final UUID CONSOLE = new UUID(0L, 0L);

    private final AuditLogRepository log;
    private final Clock clock;

    public AdminAudit(AuditLogRepository log, Clock clock) {
        this.log = Objects.requireNonNull(log, "log");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Hält einen Eingriff fest.
     *
     * @param sender wer es getan hat — Spieler oder Konsole
     * @param action was, als Schlüssel: {@code item_granted}, {@code level_set}
     * @param target der betroffene Spieler, falls es einen gibt
     * @param details alles Weitere, was die Zeile später erklärt
     */
    public void record(
            CommandSender sender, String action, UUID target, Map<String, Object> details) {
        Objects.requireNonNull(action, "action");
        log.append(
                new AuditEntry(
                        clock.instant(),
                        actorOf(sender).toString(),
                        action,
                        Optional.ofNullable(target),
                        details == null ? Map.of() : new LinkedHashMap<>(details)));
    }

    /** Ein Eingriff ohne betroffenen Spieler — {@code /rpg reload} etwa. */
    public void record(CommandSender sender, String action, Map<String, Object> details) {
        record(sender, action, null, details);
    }

    /** Spieler-UUID oder die feste Null-UUID der Konsole. */
    public static UUID actorOf(CommandSender sender) {
        return sender instanceof Player player ? player.getUniqueId() : CONSOLE;
    }
}
