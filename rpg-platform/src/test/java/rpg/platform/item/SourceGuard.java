package rpg.platform.item;

/**
 * Eine Hilfe für die Quelltextprüfungen dieses Blocks — die Plattformhälfte.
 *
 * <p>Wortgleich mit {@code SourceGuard} in {@code rpg-core}, und das ist kein Versehen: Testquellen
 * werden zwischen Modulen nicht geteilt, und eine Abhängigkeit auf den Testbestand eines anderen
 * Moduls in die Buildkonfiguration zu schreiben wäre ein Eingriff in den Bau des ganzen Projekts,
 * um zwei Zeilen zu sparen.
 *
 * <p><b>Warum es sie überhaupt gibt.</b> Mehrere Prüfungen hier belegen eine <em>Abwesenheit</em> —
 * keine zweite Fassung, kein Vanilla-Typname, keine eingeplante Aufgabe. Solche Prüfungen haben eine
 * gemeinsame Falle, in die beim Bau dieses Blocks mehrfach getreten wurde: ein Kommentar, der
 * erklärt, <em>warum</em> etwas nicht dasteht, enthält das gesuchte Wort. Der Test schlägt an, der
 * Autor trägt eine Ausnahme ein — und ab da prüft er weniger, als er behauptet.
 */
final class SourceGuard {

    private SourceGuard() {}

    /** Quelltext ohne Block- und Zeilenkommentare. */
    static String codeOnly(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
    }
}
