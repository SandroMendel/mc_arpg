package rpg.plugin.command.framework;

import java.util.Objects;

/**
 * Ein Argument eines Kommandos — <b>eine Deklaration, kein Stück Zerlegecode</b> (FR-002).
 *
 * <p>Name, Typ, Pflicht. Mehr braucht es nicht, und mehr darf es nicht sein: alles Weitere über
 * diesen Wert — wie er gelesen wird, was vorgeschlagen wird, was erlaubt ist — steckt im
 * {@link ArgumentType} und damit an <b>einer</b> Stelle.
 *
 * <p>Der {@link #name()} ist kein Anzeigetext, sondern der Bezeichner, unter dem der Wert in der
 * Fehlermeldung genannt und aus dem {@link CommandContext} geholt wird. Er ist kurz und
 * kleingeschrieben ({@code player}, {@code amount}), weil er in {@code messages.yml} als Platzhalter
 * landet.
 *
 * @param name Bezeichner, erscheint in Fehlermeldung und Hilfe
 * @param type prüft <em>und</em> schlägt vor
 * @param required ob es weggelassen werden darf
 * @param <T> was beim Parsen herauskommt
 */
public record Argument<T>(String name, ArgumentType<T> type, boolean required) {

    public Argument {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        if (name.isBlank()) {
            throw new IllegalArgumentException("ein Argument ohne Namen kann keine Meldung nennen");
        }
    }

    /** Ein Pflichtargument. */
    public static <T> Argument<T> required(String name, ArgumentType<T> type) {
        return new Argument<>(name, type, true);
    }

    /** Ein Argument, das weggelassen werden darf. */
    public static <T> Argument<T> optional(String name, ArgumentType<T> type) {
        return new Argument<>(name, type, false);
    }
}
