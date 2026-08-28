package rpg.core.item;

/**
 * Eine Hilfe für die Quelltextprüfungen dieses Blocks.
 *
 * <p><b>Warum es sie gibt.</b> Mehrere Tests hier prüfen nicht ein Verhalten, sondern eine
 * <em>Abwesenheit</em>: kein Vorlagenbezeichner im Code, kein Wurf außerhalb der Stückzahl, keine
 * eingeplante Aufgabe. Solche Prüfungen sind wertvoll — und sie haben eine gemeinsame Falle, in die
 * beim Schreiben dieses Blocks <b>drei Mal</b> hintereinander getreten wurde:
 *
 * <p>Ein Kommentar, der erklärt, <em>warum</em> etwas nicht dasteht, enthält das gesuchte Wort. Der
 * Test schlägt an, der Autor trägt eine Ausnahme ein — und ab da prüft er weniger, als er behauptet.
 * Die Erklärung als Verstoß zu werten ist die häufigste Art, eine Quelltextprüfung wertlos zu
 * machen.
 *
 * <p>Deshalb steht das Entfernen der Kommentare an <b>einer</b> Stelle. Dasselbe tut
 * {@code ConfigOnlyMobTest} in B10; hier bekommt es einen Namen, weil es vier Aufrufer hat.
 */
final class SourceGuard {

    private SourceGuard() {}

    /** Quelltext ohne Block- und Zeilenkommentare. */
    static String codeOnly(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
    }
}
