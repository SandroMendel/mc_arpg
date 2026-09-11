package rpg.plugin.command.framework;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import rpg.core.message.MessageKey;
import rpg.core.message.Messages;

/**
 * Baut aus {@link RpgCommand} einen Brigadier-Baum und registriert ihn (T013).
 *
 * <h2>Registriert wird über den Lebenszyklus, nicht über {@code plugin.yml}</h2>
 *
 * <p>Am 2026-09-05 auf dem echten Server geklärt (research.md §1): eine Registrierung über den
 * {@code LifecycleEventManager} braucht <b>keinen</b> {@code plugin.yml}-Eintrag, und bei
 * Namensgleichheit wird die {@code plugin.yml}-Fassung <b>nie erreicht</b> — sie ist verdrahtet und
 * liegt trotzdem dahinter. Deshalb entfällt der {@code commands:}-Block je Kommando beim Umzug, und
 * das Recht wandert von dort an {@code requires}.
 *
 * <h2>Warum jedes Argument als {@code word()} durchgeht</h2>
 *
 * <p>Brigadier bringt eigene Typen mit — {@code integer()}, {@code bool()} und so fort. Sie werden
 * hier <b>nicht</b> benutzt, und das ist der Kern von FR-002: nähme ein Zahlenargument Brigadiers
 * {@code integer()}, prüfte Brigadier den Wert und der {@link ArgumentType} schlüge ihn vor — zwei
 * Quellen, die übereinstimmen müssen, ohne dass etwas sie dazu zwingt. Genau der Zustand, den
 * dieser Block abschafft. Also nimmt der Baum überall {@code word()} entgegen und reicht den rohen
 * Text an den einen Typ weiter, der prüft <em>und</em> vorschlägt.
 *
 * <p>Das kostet Brigadiers clientseitige Vorabprüfung. Sie ist der Preis dafür, dass eine
 * Vervollständigung nie etwas vorschlägt, was hinterher abgelehnt wird.
 */
public final class CommandTree {

    /**
     * Wohin eine abgelehnte Eingabe geht.
     *
     * <p>Eine Naht und keine Bequemlichkeit: {@link ArgumentRejected} trägt Schlüssel und
     * Platzhalter, nicht fertigen Text (FR-009). Wer daraus einen Satz macht, braucht die
     * {@code Messages} — die hat das Gerüst nicht und soll sie nicht haben. {@link CommandErrors}
     * hängt sich hier ein.
     */
    @FunctionalInterface
    public interface Rejections {
        void tell(CommandSender sender, ArgumentRejected rejected);
    }

    private final CommandErrors errors;
    private final RateLimits rateLimits;
    private final Messages messages;

    public CommandTree(CommandErrors errors, RateLimits rateLimits, Messages messages) {
        this.errors = Objects.requireNonNull(errors, "errors");
        this.rateLimits = Objects.requireNonNull(rateLimits, "rateLimits");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    /**
     * Die Beschreibung, die {@code /help} zeigt — <b>aufgelöst, nicht der Schlüssel</b>.
     *
     * <p>Gefunden am 2026-09-06 auf dem echten Server: {@code help trash} zeigte
     * {@code Description: command.trash.description}. Hier stand vorher {@code key.value()} mit dem
     * Vermerk, Brigadiers Beschreibung sei „Betreibertext im Log" — das war schlicht falsch. Sie
     * steht in der Serverhilfe, und die liest ein Mensch.
     *
     * <p>Ein roher Schlüssel vor Augen ist derselbe Fehler wie ein Zonenname, der als
     * {@code zone.greenfields.name} im Spiel auftaucht; B09 bricht dafür den Start ab. Hier bricht
     * nichts ab, weil die Startprüfung den Schlüssel bereits kennt — sie steht in
     * {@code CommandMessageKeys.all()}. Fehlt er trotzdem, ist der Schlüssel besser als eine leere
     * Zeile: eine Hilfe ohne Text sieht aus wie ein Kommando ohne Zweck.
     */
    private String describe(RpgCommand command) {
        MessageKey key = command.description();
        return messages.contains(key) ? messages.get(key) : key.value();
    }

    /**
     * Meldet die Kommandos beim Server an.
     *
     * <p>Muss aus {@code onEnable} gerufen werden — der Lebenszyklus nimmt den Handler danach nicht
     * mehr an.
     */
    public void register(JavaPlugin plugin, Collection<RpgCommand> commands) {
        Objects.requireNonNull(plugin, "plugin");
        List<RpgCommand> roots = List.copyOf(commands);
        plugin.getLifecycleManager()
                .registerEventHandler(
                        LifecycleEvents.COMMANDS,
                        event -> {
                            Commands registrar = event.registrar();
                            for (RpgCommand root : roots) {
                                registrar.register(
                                        build(root).build(),
                                        describe(root),
                                        List.of());
                            }
                        });
    }

    /** Der Baum eines Knotens, ohne ihn anzumelden — auch der Zugang für Tests. */
    LiteralArgumentBuilder<CommandSourceStack> build(RpgCommand command) {
        LiteralArgumentBuilder<CommandSourceStack> node = Commands.literal(command.name());

        // Die EINE Rechtepruefung (T019/T020, FR-003). Brigadiers requires entscheidet zugleich
        // ueber Ausfuehrbarkeit (FR-013) und Sichtbarkeit in der Vervollstaendigung (FR-014) -
        // zwei Zusagen, eine Pruefung. Haetten sie zwei, koennten sie auseinanderlaufen, und dann
        // verriete die Vervollstaendigung, was es alles gibt.
        node.requires(source -> CommandPermissions.allows(source.getSender(), command));

        for (RpgCommand child : command.children()) {
            node.then(build(child));
        }

        if (command.action() == null) {
            // Eine reine Verzweigung bekommt KEIN executes(). Ohne weiteres Wort ist sie
            // unvollstaendig, und Brigadier sagt das von selbst - besser als eine Ausfuehrung,
            // die raten muesste.
            return node;
        }

        // Ein Knoten mit Unterkommandos UND eigenen Formen (/coins). Die Kinder haengen schon
        // dran; jetzt kommen die eigenen Argumente und die Ausfuehrung dazu. Brigadier loest
        // Literale vor Argumenten auf, die Reihenfolge hier ist also ohne Belang.
        return withArguments(node, command);
    }

    /**
     * Hängt die Argumente als Kette an und legt die Ausführung an jede erreichbare Stelle.
     *
     * <p>„Jede erreichbare Stelle" heißt: hinter das letzte Pflichtargument und hinter jedes
     * optionale danach. Ohne das wäre ein optionales Argument keines — Brigadier führt einen Knoten
     * nur aus, wenn er selbst ein {@code executes} trägt.
     */
    private LiteralArgumentBuilder<CommandSourceStack> withArguments(
            LiteralArgumentBuilder<CommandSourceStack> root, RpgCommand command) {

        List<Argument<?>> arguments = command.arguments();
        if (arguments.isEmpty()) {
            root.executes(context -> runBare(command, context.getSource().getSender()));
            return root;
        }

        int lastRequired = lastRequiredIndex(arguments);

        List<RequiredArgumentBuilder<CommandSourceStack, String>> chain =
                new ArrayList<>(arguments.size());
        for (Argument<?> argument : arguments) {
            RequiredArgumentBuilder<CommandSourceStack, String> node =
                    Commands.argument(argument.name(), StringArgumentType.word());
            node.suggests(
                    (context, builder) -> {
                        for (String suggestion :
                                argument.type().suggest(builder.getRemaining())) {
                            builder.suggest(suggestion);
                        }
                        return builder.buildFuture();
                    });
            chain.add(node);
        }

        // Von hinten nach vorn verketten: jeder Knoten braucht seine Kinder schon fertig.
        for (int i = arguments.size() - 1; i >= 0; i--) {
            if (i >= lastRequired) {
                chain.get(i).executes(context -> execute(command, context));
            }
            if (i > 0) {
                chain.get(i - 1).then(chain.get(i));
            }
        }

        // Gibt es gar kein Pflichtargument, ist auch der nackte Aufruf gueltig.
        if (lastRequired < 0) {
            root.executes(context -> runBare(command, context.getSource().getSender()));
        }
        root.then(chain.get(0));
        return root;
    }

    private static int lastRequiredIndex(List<Argument<?>> arguments) {
        for (int i = arguments.size() - 1; i >= 0; i--) {
            if (arguments.get(i).required()) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Liest die getippten Werte durch ihren eigenen Typ und führt aus.
     *
     * <p>Was hier ankommt, hat Brigadier nur als Wort erkannt. Die Prüfung <em>ist</em> der Aufruf
     * von {@link ArgumentType#parse} — dieselbe Quelle, aus der die Vorschläge kamen.
     *
     * <p>Eine Ablehnung geht als <b>Meldung</b> an den Absender und nicht als
     * {@code CommandSyntaxException}: Brigadiers Syntaxfehler ist roher, unübersetzter Text mit
     * einem Zeiger auf die Eingabe, und FR-004 will das betroffene Argument und seinen Wertebereich
     * genannt haben.
     */
    private int execute(
            RpgCommand command,
            com.mojang.brigadier.context.CommandContext<CommandSourceStack> raw) {

        CommandSender sender = raw.getSource().getSender();

        // ZUERST der Absender, DANN die Argumente. Umgekehrt herum lief hier eine Weile die
        // falsche Reihenfolge, und der Server hat es gezeigt: `stats gibtsnichtXY` von der
        // Konsole antwortete „No player named gibtsnichtXY" statt „needs a player". Das ist zwar
        // wahr, aber nicht die Antwort - der Betreiber haette den Namen korrigiert und waere
        // beim naechsten Versuch am selben Punkt gescheitert.
        //
        // Und es ist Arbeit fuer jemanden, der ohnehin abgewiesen wird: den PLAYER-Typ aufzuloesen
        // heisst, den Namens-Cache zu befragen.
        if (!mayRun(command, sender)) {
            return 0;
        }

        Map<String, Object> values = new LinkedHashMap<>();
        for (Argument<?> argument : command.arguments()) {
            String typed = readWord(raw, argument.name());
            if (typed == null) {
                continue;
            }
            try {
                values.put(argument.name(), argument.type().parse(typed));
            } catch (ArgumentRejected rejected) {
                errors.tell(sender, rejected);
                return 0;
            }
        }
        command.action().accept(new CommandContext(sender, values));
        return 1;
    }

    /**
     * Was vor jeder Ausführung gilt — Recht, Absender, Sperrzeit (T019, T021, T024).
     *
     * <p>An <b>einer</b> Stelle und in dieser Reihenfolge. Sie ist nicht beliebig: erst das Recht,
     * denn wer nicht darf, soll nicht erfahren, dass er zu schnell war. Dann der Absender, weil eine
     * Sperrzeit für die Konsole ohnehin nicht gilt. Zuletzt die Sperre, die als einzige etwas
     * <em>vermerkt</em> — ein Vermerk für einen Aufruf, der danach am Recht scheitert, wäre eine
     * Sperre gegen nichts.
     *
     * <p>Paketsichtbar statt privat: das ist die Stelle, an der drei Zusagen zusammenlaufen
     * (FR-013, FR-008, FR-032), und ein Test soll auf sie zeigen können, ohne den ganzen
     * Brigadier-Baum durchlaufen zu müssen.
     *
     * @return {@code true}, wenn ausgeführt werden darf
     */
    boolean mayRun(RpgCommand command, CommandSender sender) {
        if (!CommandPermissions.allows(sender, command)) {
            // Brigadiers requires haette hier schon abgewiesen; wer den Zweig trotzdem
            // volltippt, bekommt eine Antwort statt Schweigen (FR-013).
            errors.denied(sender);
            return false;
        }
        if (command.requiresPlayer() && !(sender instanceof org.bukkit.entity.Player)) {
            errors.needsPlayer(sender);
            return false;
        }
        java.util.Optional<java.time.Duration> wait = rateLimits.check(sender, command);
        if (wait.isPresent()) {
            errors.rateLimited(sender, wait.get());
            return false;
        }
        return true;
    }

    /** Ein Argument, das nicht getippt wurde, ist im Brigadier-Kontext nicht vorhanden. */
    private static String readWord(
            com.mojang.brigadier.context.CommandContext<CommandSourceStack> raw, String name) {
        try {
            return StringArgumentType.getString(raw, name);
        } catch (IllegalArgumentException notGiven) {
            return null;
        }
    }

    /**
     * Der nackte Aufruf, ohne ein einziges getipptes Argument.
     *
     * <p>Eigener Weg neben {@link #execute}, damit {@link #mayRun} <b>genau einmal</b> läuft: es
     * vermerkt die Sperrzeit, und zweimal aufgerufen sperrte es sich selbst aus.
     */
    private int runBare(RpgCommand command, CommandSender sender) {
        if (!mayRun(command, sender)) {
            return 0;
        }
        command.action().accept(new CommandContext(sender, Map.of()));
        return 1;
    }
}
