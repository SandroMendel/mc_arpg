package rpg.plugin.command;

import java.util.List;

import rpg.core.message.MessageKey;

/**
 * Die Texte, die das Kommandogerüst selbst erzeugt — B14 (FR-009).
 *
 * <p><b>Kein spielersichtbarer Text steht im Code.</b> Das gilt hier doppelt, weil das Gerüst seine
 * Meldungen für <em>fremde</em> Kommandos schreibt: eine fest verdrahtete Zeichenkette an dieser
 * Stelle wäre nicht eine unübersetzbare Meldung, sondern zwanzig.
 *
 * <h2>Warum die Schlüssel im Plugin wohnen und nicht in {@code rpg-core}</h2>
 *
 * <p>{@code MessageKeyValidator.verifyAllPresent} bekommt die Liste vom <b>Aufrufer</b> —
 * {@code RpgPlugin.loadMessages} sammelt sie ein. Ein Verzeichnis in {@code rpg-core} wäre für die
 * Prüfung kein Gewinn und für {@code rpg-core} eine Abhängigkeit auf einen Blockbelang, den es
 * nicht kennt. Die anderen Blöcke legen ihre Schlüssel dort ab, weil ihre <em>Regeln</em> dort
 * liegen; B14 hat in {@code rpg-core} nichts.
 *
 * <h2>Was hier NICHT hingehört</h2>
 *
 * <p>Die Texte der einzelnen Kommandos. {@code /trash} bringt seine eigenen mit
 * ({@code ItemMessageKeys}), {@code /coins} die von B08b, {@code /stats} die von B12 — und der
 * Umzug auf das Gerüst ändert daran nichts (FR-005: kein Spieler merkt etwas). Hier stehen
 * ausschließlich die Meldungen, die das Gerüst <em>an ihrer Stelle</em> erzeugt, bevor ein Kommando
 * überhaupt läuft.
 */
public final class CommandMessageKeys {

    private CommandMessageKeys() {}

    // --- Argumentfehler (FR-004) --------------------------------------------
    //
    // Vier Lagen, vier Schluessel. NICHT die Nutzungszeile wiederholen: wer sie schon vor sich
    // hatte und trotzdem falsch getippt hat, liest sie kein zweites Mal richtig. Die Meldung nennt
    // das betroffene Argument und was dort hingehoert.

    /** Ein Pflichtargument fehlt. Platzhalter: {@code argument}, {@code expected}. */
    public static final MessageKey ARGUMENT_MISSING =
            MessageKey.of("command.error.argument-missing");

    /** Der Wert passt nicht zum Typ. Platzhalter: {@code argument}, {@code value}, {@code expected}. */
    public static final MessageKey ARGUMENT_INVALID =
            MessageKey.of("command.error.argument-invalid");

    /**
     * Der Wert liegt außerhalb des erlaubten Bereichs. Platzhalter: {@code argument}, {@code value},
     * {@code min}, {@code max}.
     *
     * <p>Eigener Schlüssel neben {@link #ARGUMENT_INVALID}, weil <em>der Bereich</em> die Antwort
     * ist: „61 ist keine gültige Stufe" hilft niemandem, „Stufe geht von 1 bis 60" schon.
     */
    public static final MessageKey ARGUMENT_OUT_OF_RANGE =
            MessageKey.of("command.error.argument-out-of-range");

    /** Kein Spieler dieses Namens. Platzhalter: {@code name}. */
    public static final MessageKey UNKNOWN_PLAYER = MessageKey.of("command.error.unknown-player");

    /**
     * Ein Schlüssel aus einer Konfiguration ist unbekannt — Vorlage, Art, Zone. Platzhalter:
     * {@code key}.
     */
    public static final MessageKey UNKNOWN_KEY = MessageKey.of("command.error.unknown-key");

    // --- Absender und Recht --------------------------------------------------

    /**
     * Von der Konsole abgesetzt, aber das Kommando braucht einen Spieler (FR-008).
     *
     * <p>Eine <b>Meldung</b>, keine Ausnahme im Log. Der Unterschied ist der ganze Punkt von FR-008:
     * eine Ausnahme sieht aus wie ein Fehler des Servers, obwohl der Betreiber nur etwas getippt
     * hat, was von dort nicht geht.
     */
    public static final MessageKey NEEDS_PLAYER = MessageKey.of("command.error.needs-player");

    /**
     * Das Recht fehlt (FR-013).
     *
     * <p>Wird selten zu sehen sein: ohne Recht erscheint der Zweig gar nicht erst in der
     * Vervollständigung (FR-014). Wer ihn volltippt, bekommt trotzdem eine Antwort — Schweigen
     * liest sich wie ein kaputtes Kommando.
     */
    public static final MessageKey DENIED = MessageKey.of("command.error.denied");

    // --- Sperrzeit (FR-032) ---------------------------------------------------

    /**
     * Zu schnell hintereinander. Platzhalter: {@code seconds}.
     *
     * <p>FR-032 verlangt ausdrücklich, dass die Ablehnung sagt, <b>wann es wieder geht</b>. Eine
     * Sperrzeit ohne Restzeit ist von einem defekten Kommando nicht zu unterscheiden.
     */
    public static final MessageKey RATE_LIMITED = MessageKey.of("command.error.rate-limited");

    /**
     * Alle Schlüssel dieses Blocks.
     *
     * <p>{@code RpgPlugin.loadMessages} geht die Liste durch und verweigert den Start, wenn ein Text
     * fehlt — und meldet <em>alle</em> Lücken auf einmal. Ohne diese Methode fiele ein vergessener
     * Schlüssel erst dem Betreiber auf, und zwar als roher Schlüsselname in der Konsole.
     */
    public static List<MessageKey> all() {
        return List.of(
                ARGUMENT_MISSING,
                ARGUMENT_INVALID,
                ARGUMENT_OUT_OF_RANGE,
                UNKNOWN_PLAYER,
                UNKNOWN_KEY,
                NEEDS_PLAYER,
                DENIED,
                RATE_LIMITED);
    }
}
