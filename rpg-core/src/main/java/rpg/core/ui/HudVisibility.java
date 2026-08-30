package rpg.core.ui;

import java.util.Objects;

/**
 * Wann eine Fläche für einen Halter überhaupt etwas zeigt (FR-008, FR-013a).
 *
 * <p>Zwei Bedingungen, und beide müssen gelten:
 *
 * <ol>
 *   <li><b>Der Spieler hat einen Charakter gewählt.</b> Zwischen Anmeldung und Charakterwahl gibt
 *       es keine Werte — <em>Nullen, die wie echte Werte aussehen, sind schlimmer als nichts</em>.
 *       Ein Spieler, der {@code Level: 0} und {@code Coins: 0} sieht, hält das für seinen Stand und
 *       nicht für einen Zwischenzustand.
 *   <li><b>Die Fläche ist eingeschaltet.</b> Und die Prüfung gehört <em>vor</em> die Berechnung,
 *       nicht dahinter: nach der Berechnung zu verwerfen wäre unsichtbar, aber nicht kostenlos
 *       (FR-013a, SC-010).
 * </ol>
 *
 * <p><b>Die Reihenfolge der beiden ist nicht beliebig.</b> Erst die Abschaltung, dann der
 * Charakter: die Abschaltung gilt serverweit und ist ein Feldzugriff, die Charakterfrage geht an
 * B03. Bei 200 Spielern mit abgeschalteter Sidebar spart das 200 Nachfragen je Sekunde, die
 * niemand liest.
 *
 * <p>Diese Klasse hat kein Feld und keinen Zustand. Sie ist die Regel, nicht ihr Ergebnis.
 */
public final class HudVisibility {

    private HudVisibility() {}

    /**
     * Ob auf dieser Fläche für diesen Halter etwas zu zeichnen ist.
     *
     * @param config die <b>aktuelle</b> Konfiguration — je Durchlauf frisch abgeholt (FR-013c)
     * @param hasCharacter ob der Spieler einen Charakter gewählt hat; kommt von B03
     */
    public static boolean shows(UiConfig config, HudSurface surface, boolean hasCharacter) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(surface, "surface");
        // Abschaltung zuerst: sie ist ein Feldzugriff, die Charakterfrage geht an einen fremden
        // Block. Bei abgeschalteter Flaeche entsteht so gar keine Nachfrage.
        return config.isEnabled(surface) && hasCharacter;
    }

    /**
     * Ob eine Fläche geräumt werden muss, statt beschrieben zu werden.
     *
     * <p><b>Geräumt ist nicht dasselbe wie leer beschrieben</b> (Vertrag {@code HudRenderer},
     * Zusage 3). Ein Spieler, der gerade den Charakter verloren hat, soll keine leere Sidebar
     * sehen, sondern gar keine.
     *
     * <p>Der Unterschied zu {@code !shows(...)}: eine <b>abgeschaltete</b> Fläche wird nicht
     * geräumt, sondern gar nicht erst angefasst. Wer sie räumte, hätte pro Spieler und Sekunde
     * genau die Arbeit erzeugt, die FR-013a verbietet — und ein Betreiber, der die Sidebar
     * abschaltet, will nicht, dass der Server sie jede Sekunde neu wegräumt.
     */
    public static boolean needsClearing(UiConfig config, HudSurface surface, boolean hasCharacter) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(surface, "surface");
        return config.isEnabled(surface) && !hasCharacter;
    }
}
