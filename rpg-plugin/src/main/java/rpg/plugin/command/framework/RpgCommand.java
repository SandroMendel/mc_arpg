package rpg.plugin.command.framework;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import rpg.core.message.MessageKey;

/**
 * Ein Kommando als <b>Baum</b>, nicht als Text (Vertrag Punkt 1).
 *
 * <p>Ein Knoten ist entweder eine <b>Verzweigung</b> — er hat Unterkommandos und keine eigene
 * Ausführung — oder ein <b>Blatt</b>: es hat Argumente und eine Ausführung. Nichts dazwischen.
 *
 * <h2>Warum die Regel hier steht und nicht in {@code CommandTree}</h2>
 *
 * <p>{@code tasks.md} T014 verlangt sie „in {@code CommandTree.java}". Sie steht stattdessen im
 * Kanonischen Konstruktor, und das ist keine Bequemlichkeit: eine Prüfung beim Bauen des
 * Brigadier-Baums fängt einen falschen Knoten erst, wenn jemand ihn zu registrieren versucht — hier
 * lässt er sich <b>gar nicht erst herstellen</b>. Der Zustand „halb ausgeführt", den T014 verhindern
 * will, ist damit nicht unwahrscheinlich, sondern unmöglich, und {@code CommandTree} muss die Regel
 * nicht kennen. Der Fehlschlag kommt beim Start, laut, mit dem Namen des Knotens.
 *
 * @param name der Name, unter dem es aufgerufen wird ({@code char}, {@code rpg}); bei
 *     Unterkommandos das eine Wort, nicht der Pfad
 * @param description Nachrichtenschlüssel für die Hilfe — <b>kein Text</b> (FR-009)
 * @param permission der Rechteknoten, ohne den es nicht sichtbar und nicht ausführbar ist
 *     ({@code null} heißt: jeder darf)
 * @param children Unterkommandos; leer bei einem Blatt
 * @param arguments geordnete Argumentliste; leer bei einer Verzweigung
 * @param requiresPlayer ob ein Spielerbezug nötig ist (steuert die Konsolenmeldung, FR-008)
 * @param rateLimit Sperrzeit je Absender, {@code null} wenn keine (FR-032)
 * @param action was das Blatt tut; {@code null} bei einer Verzweigung
 */
public record RpgCommand(
        String name,
        MessageKey description,
        String permission,
        List<RpgCommand> children,
        List<Argument<?>> arguments,
        boolean requiresPlayer,
        Duration rateLimit,
        Consumer<CommandContext> action) {

    public RpgCommand {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(description, "description");
        children = List.copyOf(children == null ? List.of() : children);
        arguments = List.copyOf(arguments == null ? List.of() : arguments);

        if (name.isBlank()) {
            throw new IllegalArgumentException("ein Kommando ohne Namen ist nicht aufrufbar");
        }

        // Die Regel aus T014, als Bauvorschrift statt als Verabredung.
        //
        // ABGESCHWAECHT AM 2026-09-06, und zwar bewusst: sie lautete zuerst „eine Verzweigung hat
        // KEINE eigene Ausfuehrung und keine Argumente". Dann kam /coins. Es hat beides -
        // Unterkommandos (set|add|remove) UND eigene Formen (/coins, /coins <spieler>) -, und
        // FR-005 verlangt, dass alle drei unveraendert weiterlaufen. Die Regel war also strenger
        // als der Vertrag (der nur „als Baum deklariert" fordert) und strenger als Brigadier, das
        // diese Form beherrscht: bei der Aufloesung gewinnen Literale vor Argumenten, das ist
        // festgelegt und nicht zufaellig.
        //
        // WAS BLEIBT, ist die Zusage dahinter: es darf keinen Knoten geben, der weder ausfuehren
        // noch weiterverzweigen kann - also kein Blatt ohne Ausfuehrung.
        //
        // WAS MAN WISSEN MUSS: hat ein Knoten Unterkommandos UND ein erstes Argument, verdeckt
        // jedes Unterkommando einen gleichlautenden Wert. Ein Spieler namens "set" ist ueber
        // /coins <spieler> nicht erreichbar. Das ist keine neue Luecke - die alte
        // `switch (args[0])`-Fassung hatte sie genauso -, aber sie ist jetzt aufgeschrieben.
        boolean branch = !children.isEmpty();
        if (!branch && action == null) {
            throw new IllegalArgumentException(
                    "'" + name + "' ist ein Blatt ohne Ausfuehrung - es koennte nur schweigen");
        }
        if (branch && action == null && !arguments.isEmpty()) {
            throw new IllegalArgumentException(
                    "'"
                            + name
                            + "' hat Argumente, aber keine Ausfuehrung, die sie liest - die"
                            + " Argumente waeren nicht erreichbar");
        }

        // Ein Pflichtargument hinter einem optionalen ist nicht erreichbar: wer das optionale
        // weglaesst, hat das Pflichtargument an dessen Stelle getippt, und niemand kann die zwei
        // Faelle auseinanderhalten.
        boolean sawOptional = false;
        for (Argument<?> argument : arguments) {
            if (argument.required() && sawOptional) {
                throw new IllegalArgumentException(
                        "'"
                                + name
                                + "': Pflichtargument '"
                                + argument.name()
                                + "' steht hinter einem optionalen und ist damit nicht"
                                + " erreichbar");
            }
            sawOptional |= !argument.required();
        }
    }

    /** Ob dieser Knoten eine Verzweigung ist. */
    public boolean isBranch() {
        return !children.isEmpty();
    }

    /** Die Sperrzeit, falls es eine gibt. */
    public Optional<Duration> rateLimitOrNone() {
        return Optional.ofNullable(rateLimit);
    }

    /** Das Recht, falls es eines braucht. */
    public Optional<String> permissionOrNone() {
        return Optional.ofNullable(permission);
    }

    /** Eine Verzweigung: Unterkommandos, sonst nichts. */
    public static RpgCommand branch(
            String name, MessageKey description, String permission, List<RpgCommand> children) {
        return new RpgCommand(
                name, description, permission, children, List.of(), false, null, null);
    }

    /** Ein Blatt, das jeder von überall aufrufen darf. */
    public static RpgCommand leaf(
            String name,
            MessageKey description,
            String permission,
            List<Argument<?>> arguments,
            Consumer<CommandContext> action) {
        return new RpgCommand(
                name, description, permission, List.of(), arguments, false, null, action);
    }

    /** Ein Blatt, das einen Spieler braucht (FR-008). */
    public static RpgCommand playerLeaf(
            String name,
            MessageKey description,
            String permission,
            List<Argument<?>> arguments,
            Consumer<CommandContext> action) {
        return new RpgCommand(
                name, description, permission, List.of(), arguments, true, null, action);
    }

    /**
     * Ein Knoten mit Unterkommandos <b>und</b> eigenen Formen — der Fall {@code /coins}.
     *
     * <p>Eine eigene Fabrik und nicht die Kombination aus {@link #branch} und {@link #leaf}, damit
     * diese Form <b>ausgesprochen</b> werden muss. Sie ist die Ausnahme: {@code /coins},
     * {@code /coins <spieler>} und {@code /coins set …} sind drei Formen desselben Wortes, weil
     * B08b sie so ausgeliefert hat und FR-005 sie unverändert lässt.
     *
     * <p><b>Unterkommandos verdecken gleichlautende Werte.</b> Ein Spieler namens {@code set} ist
     * über {@code /coins <spieler>} nicht erreichbar — Brigadier löst Literale zuerst auf. Dieselbe
     * Lücke hatte die alte {@code switch (args[0])}-Fassung; neu ist nur, dass sie hier steht.
     */
    public static RpgCommand branchWithOwnForms(
            String name,
            MessageKey description,
            String permission,
            List<RpgCommand> children,
            List<Argument<?>> arguments,
            boolean requiresPlayer,
            Consumer<CommandContext> action) {
        return new RpgCommand(
                name,
                description,
                permission,
                children,
                arguments,
                requiresPlayer,
                null,
                Objects.requireNonNull(action, "action"));
    }

    /** Dasselbe Blatt mit einer Sperrzeit — für alles, was die Datenbank befragt (FR-032). */
    public RpgCommand throttled(Duration window) {
        return new RpgCommand(
                name, description, permission, children, arguments, requiresPlayer, window, action);
    }
}
