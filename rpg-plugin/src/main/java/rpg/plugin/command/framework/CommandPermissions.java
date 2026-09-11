package rpg.plugin.command.framework;

import org.bukkit.command.CommandSender;

/**
 * <b>Die eine Prüfstelle</b> (T019, FR-003, FR-013).
 *
 * <p>Ein Kommando kann seine Rechteprüfung nicht vergessen, <b>weil es sie nicht durchführt</b>.
 * Das ist der ganze Punkt: nicht „jedes Kommando muss daran denken", sondern „keines kommt an die
 * Stelle heran, an der man es vergessen könnte".
 *
 * <p>Warum das eine eigene Klasse ist und keine Zeile in {@link CommandTree}: die Zusage FR-003
 * lautet, dass es <em>genau eine</em> Stelle gibt. Eine Zeile mitten im Baumbau ist keine Stelle,
 * auf die ein Test zeigen kann — diese Klasse ist eine.
 *
 * <h2>Sichtbarkeit und Ausführbarkeit sind dieselbe Frage</h2>
 *
 * <p>Brigadiers {@code requires} entscheidet über beides zugleich: ein Knoten, dessen Bedingung
 * nicht zutrifft, ist weder ausführbar (FR-013) noch in der Vervollständigung sichtbar (FR-014).
 * Zwei Zusagen, eine Prüfung — hätten sie zwei, könnten sie auseinanderlaufen, und dann verriete
 * die Vervollständigung, was es alles gibt.
 */
public final class CommandPermissions {

    private CommandPermissions() {}

    /**
     * Ob dieser Absender diesen Knoten benutzen darf.
     *
     * <p>Ein Knoten ohne Recht steht jedem offen. Das ist kein Schlupfloch, sondern die Regel für
     * die Wurzel und für die Spielerkommandos: {@code /char} hat sein Recht bei
     * {@code default: true}, und die Wurzel {@code /rpg} hat gar keines, weil ihre Kinder eigene
     * haben.
     */
    public static boolean allows(CommandSender sender, RpgCommand command) {
        return command.permissionOrNone()
                .map(sender::hasPermission)
                .orElse(true);
    }
}
