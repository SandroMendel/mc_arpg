package rpg.plugin.command.admin;

import java.util.List;
import java.util.Optional;

import rpg.core.message.MessageKey;
import rpg.plugin.command.framework.RpgCommand;

/**
 * Die Wurzel {@code /rpg} — <b>Fundament, nicht Teil einer Geschichte</b> (T015).
 *
 * <p>Sie lag im ersten Zuschnitt in US3 („Items geben"), weil das die erste Geschichte war, die sie
 * brauchte. Der zweite {@code /speckit-analyze} hat aufgedeckt, was das bedeutet hätte: US4 bis US8
 * brauchen sie ebenfalls, also hätten fünf Geschichten an einer sechsten gehangen, während Plan und
 * Aufgabenliste ihre Unabhängigkeit behaupteten.
 *
 * <p><b>Die Wurzel trägt kein eigenes Recht.</b> Sie ist eine Verzweigung; sichtbar ist sie genau
 * dann, wenn mindestens ein Kind sichtbar ist, und das entscheidet Brigadier über die
 * {@code requires} der Kinder. Ein Recht an der Wurzel wäre eine zweite Hürde vor derselben Tür —
 * und die schlimmere Sorte davon: Wer {@code rpg.admin.reload} hat, aber das Wurzelrecht nicht,
 * bekäme ein Recht, das nichts nützt, ohne dass irgendwo stünde warum.
 */
public final class RpgRootCommand {

    private RpgRootCommand() {}

    /** Der Name, unter dem alles Betreiberliche hängt. */
    public static final String NAME = "rpg";

    /** Beschreibung für die Serverhilfe — ein Schlüssel, kein Text (FR-009). */
    public static final MessageKey DESCRIPTION = MessageKey.of("command.rpg.description");

    /**
     * Die Wurzel mit ihren Unterkommandos.
     *
     * <p><b>Leer heißt: gar nicht registrieren.</b> Ein {@code /rpg} ohne ein einziges
     * Unterkommando wäre ein Kommando, das auf jede Eingabe „unvollständig" antwortet — es stünde
     * in der Vervollständigung, nähme Platz und täte nie etwas. Solange keine Geschichte etwas
     * darunter gehängt hat, gibt es die Wurzel nicht.
     *
     * <p>Das ist auch der Grund für {@link Optional} statt einer Ausnahme: dass hier während der
     * Entwicklung noch nichts hängt, ist der erwartete Zustand und kein Fehler.
     *
     * @param children was die Geschichten beigesteuert haben
     */
    public static Optional<RpgCommand> of(List<RpgCommand> children) {
        if (children == null || children.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(RpgCommand.branch(NAME, DESCRIPTION, null, children));
    }
}
