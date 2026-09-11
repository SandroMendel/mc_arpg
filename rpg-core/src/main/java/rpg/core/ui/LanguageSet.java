package rpg.core.ui;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Ein Sprachsatz und die Datei, aus der er kommt (FR-016 bis FR-019).
 *
 * <h2>Vollständig oder der Start bricht ab</h2>
 *
 * <p>FR-018. Geprüft wird nicht hier, sondern von {@code MessageKeyValidator.verifyAllPresent} —
 * und das ist der Punkt, an dem B13 <b>nichts Neues baut</b>: die Prüfung existiert seit B01, sie
 * meldet <em>alle</em> fehlenden Schlüssel auf einmal, und B13 ändert nur, <b>welche Datei</b>
 * gelesen wird (research.md R8).
 *
 * <p><b>Alle auf einmal ist der Unterschied zwischen machbar und aussichtslos.</b> Wer eine zweite
 * Sprache anlegt, hat zweihundert Schlüssel zu übersetzen; bei einer Meldung je Startversuch gäbe
 * er nach dem zwanzigsten auf.
 *
 * <h2>Der Dateiname folgt aus dem Kürzel</h2>
 *
 * <p>{@code en} → {@code messages.yml}, alles andere → {@code messages_<code>.yml}.
 *
 * <p><b>Die Ausnahme für Englisch ist Absicht.</b> Die ausgelieferte Datei heißt seit B01
 * {@code messages.yml}, und jeder Betreiber, der sie bearbeitet hat, hätte sie nach einer Umbenennung
 * plötzlich nicht mehr in Gebrauch — ohne Fehler, ohne Hinweis, nur mit englischen Vorgabetexten.
 * Eine Migration dafür wäre teurer als diese eine Zeile.
 *
 * <h2>Die Sprache wechselt beim Start, nicht je Spieler</h2>
 *
 * <p>Ein Vanilla-Client meldet seine Sprache zwar, aber eine Anzeige je Spielersprache hieße, jeden
 * Text mehrfach zu halten und bei jeder Ausgabe zu entscheiden, welcher gilt. Das ist Umfang für
 * einen späteren Block; B13 macht eine zweite Sprache <em>strukturell möglich</em>, mehr verspricht
 * es nicht.
 *
 * @param code das Kürzel aus {@code ui.yml}, kleingeschrieben
 */
public record LanguageSet(String code) {

    /** Das Kürzel der ausgelieferten Sprache. */
    public static final String DEFAULT_CODE = "en";

    /** Die Datei, die seit B01 ausgeliefert wird. */
    public static final String DEFAULT_FILE = "messages.yml";

    /**
     * Was als Kürzel durchgeht.
     *
     * <p>Eng gefasst, weil daraus ein <b>Dateiname</b> wird: ein Kürzel mit {@code ../} darin wäre
     * ein Pfad, und ein Pfad in einer Konfiguration ist ein Weg, Dateien außerhalb des Datenordners
     * zu lesen. Zwei bis fünf Kleinbuchstaben, wahlweise mit einem Bindestrich-Teil
     * ({@code de}, {@code pt-br}).
     */
    private static final Pattern VALID = Pattern.compile("[a-z]{2,5}(-[a-z0-9]{2,5})?");

    public LanguageSet {
        Objects.requireNonNull(code, "code");
        code = code.trim().toLowerCase(Locale.ROOT);
        if (!VALID.matcher(code).matches()) {
            throw new IllegalArgumentException(
                    "ui.yml: language ist '"
                            + code
                            + "' - erlaubt sind zwei bis fuenf Kleinbuchstaben, wahlweise mit einem"
                            + " Bindestrich-Teil (en, de, pt-br). Aus dem Kuerzel wird ein"
                            + " Dateiname, deshalb ist es eng gefasst");
        }
    }

    /** Der Sprachsatz, der ausgeliefert wird. */
    public static LanguageSet defaultSet() {
        return new LanguageSet(DEFAULT_CODE);
    }

    /**
     * Die Datei, aus der dieser Satz gelesen wird.
     *
     * <p>Für Englisch bleibt es {@link #DEFAULT_FILE} — siehe Klassenkommentar.
     */
    public String file() {
        return DEFAULT_CODE.equals(code) ? DEFAULT_FILE : "messages_" + code + ".yml";
    }

    /** Ob das der ausgelieferte Satz ist. */
    public boolean isDefault() {
        return DEFAULT_CODE.equals(code);
    }
}
