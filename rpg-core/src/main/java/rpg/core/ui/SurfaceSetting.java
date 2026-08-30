package rpg.core.ui;

/**
 * Ob eine der drei Flächen überhaupt bespielt wird (FR-013a).
 *
 * <p><b>Abgeschaltet heißt kostenlos, nicht unsichtbar.</b> Das ist der ganze Inhalt dieser Klasse
 * und der Grund, aus dem sie eine eigene ist statt eines {@code boolean} im
 * {@link UiConfig}-Record: der Name trägt die Zusage an jede Aufrufstelle.
 *
 * <p>Ein Sammeltakt, der den Inhalt einer abgeschalteten Fläche berechnet und ihn danach verwirft,
 * erfüllt sie <b>nicht</b>. Er wäre unsichtbar, aber nicht umsonst — und bei 200 Spielern ist der
 * Unterschied genau die Arbeit, die SC-010 messbar verbietet. Die Prüfung gehört deshalb
 * <em>vor</em> die Berechnung, nicht dahinter.
 *
 * @param enabled ob die Fläche bespielt wird
 */
public record SurfaceSetting(boolean enabled) {

    /** Eine eingeschaltete Fläche — die Vorgabe für alle drei. */
    public static SurfaceSetting on() {
        return new SurfaceSetting(true);
    }

    /** Eine abgeschaltete Fläche. */
    public static SurfaceSetting off() {
        return new SurfaceSetting(false);
    }
}
