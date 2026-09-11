package rpg.plugin.command.framework;

import java.util.List;

/**
 * Ein Argumenttyp — <b>prüft und schlägt vor, beides aus derselben Quelle</b> (FR-002).
 *
 * <h2>Warum das ein Typ ist und nicht zwei Methoden an zwei Stellen</h2>
 *
 * <p>Der heutige Zustand ist der Grund für diese Schnittstelle: {@code StatisticsCommand} prüft
 * einen Zeitraum in {@code periodOf(raw)} und schlägt ihn in {@code onTabComplete} über eine
 * <em>zweite</em> Schleife über dieselbe Aufzählung vor. Solange beide dasselbe tun, fällt nichts
 * auf; sobald eine von beiden angefasst wird, schlägt die Vervollständigung Werte vor, die die
 * Prüfung ablehnt. Fünfmal gibt es dieses Paar im Projekt.
 *
 * <p>Hier kann das nicht passieren: {@link #parse} und {@link #suggest} sind zwei Methoden
 * <b>desselben Objekts</b>, und ein Kommando bekommt nur dieses eine. Eine Vorschlagsliste, die von
 * der Prüfung abweicht, ist damit strukturell ausgeschlossen und nicht bloß unwahrscheinlich.
 *
 * <p><b>Abweichung vom Datenmodell, bewusst:</b> {@code data-model.md} führt an {@code Argument}
 * ein eigenes Feld {@code suggestions} — „dieselbe Quelle, die auch prüft". Genau deshalb ist es
 * hier <em>kein</em> eigenes Feld: zwei Felder, die dasselbe sein müssen, sind zwei Felder, die
 * auseinanderlaufen können. Die Zusage steht in der Bauform statt in einer Verabredung.
 *
 * @param <T> was beim Parsen herauskommt
 */
public interface ArgumentType<T> {

    /**
     * Liest den getippten Text.
     *
     * @param raw genau ein Wort, wie der Absender es getippt hat; nie {@code null}, nie leer
     * @return der gelesene Wert
     * @throws ArgumentRejected wenn der Wert nicht gilt — mit Schlüssel und Platzhaltern für die
     *     Meldung, damit der Aufrufer keinen Text erfinden muss (FR-004, FR-009)
     */
    T parse(String raw) throws ArgumentRejected;

    /**
     * Was hier stehen könnte, gefiltert am schon Getippten (FR-033).
     *
     * <p>Gibt eine <b>begrenzte</b> Liste zurück. Eine vollständige Liste über tausend Namen zu
     * senden, kostet ein Paket je Tastendruck und hilft niemandem.
     *
     * @param partial das bisher Getippte, möglicherweise leer
     */
    List<String> suggest(String partial);

    /**
     * Der erlaubte Wertebereich, als <b>Werte</b> — {@code "1-60"}, {@code "day|week|season"}.
     *
     * <p>FR-004 verlangt, dass eine Fehlermeldung den Bereich nennt. Das hier ist die Angabe dafür
     * und <b>kein Satz</b>: der Satz kommt aus {@code messages.yml}, hier steht nur, was eingesetzt
     * wird. Prinzip V bleibt gewahrt.
     */
    String expected();

    /** Wie viele Vorschläge höchstens gesendet werden (FR-033). */
    int SUGGESTION_LIMIT = 50;
}
