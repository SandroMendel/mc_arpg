package rpg.plugin.command.framework;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import rpg.core.message.MessageKey;
import rpg.plugin.command.CommandMessageKeys;

/**
 * Ein Argument gilt nicht — <b>mit Schlüssel und Platzhaltern, nicht mit fertigem Text</b>.
 *
 * <p>Ein Argumenttyp weiß, <em>was</em> falsch ist; er weiß nicht, in welcher Sprache der Server
 * läuft. Deshalb trägt diese Ausnahme einen {@link MessageKey} und die Platzhalter, und der Text
 * entsteht erst dort, wo die {@code Messages} zur Hand sind. Ein Typ, der eine Zeichenkette wirft,
 * hätte Prinzip V an der einzigen Stelle gebrochen, an der es besonders weh tut: in einer Meldung,
 * die nur im Fehlerfall erscheint und deshalb niemandem auffällt.
 *
 * <p>Die Platzhalter folgen FR-004: <b>welches Argument</b> und <b>was dort erlaubt ist</b> — nie
 * die Nutzungszeile. Wer die schon vor sich hatte und trotzdem falsch getippt hat, liest sie kein
 * zweites Mal richtig.
 */
public final class ArgumentRejected extends Exception {

    private static final long serialVersionUID = 1L;

    @SuppressWarnings("serial") // MessageKey ist ein record aus rpg-core und serialisierbar
    private final transient MessageKey key;

    @SuppressWarnings("serial") // Map.copyOf liefert eine serialisierbare Implementierung
    private final transient Map<String, String> placeholders;

    private ArgumentRejected(MessageKey key, Map<String, String> placeholders) {
        super(key.value() + " " + placeholders);
        this.key = Objects.requireNonNull(key, "key");
        this.placeholders = Map.copyOf(placeholders);
    }

    /** Der Wert passt nicht zum Typ. */
    public static ArgumentRejected invalid(String argument, String value, String expected) {
        return new ArgumentRejected(
                CommandMessageKeys.ARGUMENT_INVALID,
                of("argument", argument, "value", value, "expected", expected));
    }

    /**
     * Der Wert liegt außerhalb des Bereichs.
     *
     * <p>Eigene Lage neben {@link #invalid}, weil <em>der Bereich</em> die Antwort ist: „61 ist
     * keine gültige Stufe" hilft niemandem, „Stufe geht von 1 bis 60" schon.
     */
    public static ArgumentRejected outOfRange(
            String argument, String value, String min, String max) {
        return new ArgumentRejected(
                CommandMessageKeys.ARGUMENT_OUT_OF_RANGE,
                of("argument", argument, "value", value, "min", min, "max", max));
    }

    /** Ein unbekannter Schlüssel aus einer Konfiguration — Vorlage, Art, Zone. */
    public static ArgumentRejected unknownKey(String key) {
        return new ArgumentRejected(CommandMessageKeys.UNKNOWN_KEY, of("key", key));
    }

    /** Kein Spieler dieses Namens. */
    public static ArgumentRejected unknownPlayer(String name) {
        return new ArgumentRejected(CommandMessageKeys.UNKNOWN_PLAYER, of("name", name));
    }

    /** Welcher Text gilt. */
    public MessageKey key() {
        return key;
    }

    /** Was darin eingesetzt wird. */
    public Map<String, String> placeholders() {
        return placeholders;
    }

    private static Map<String, String> of(String... pairs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return map;
    }
}
