package rpg.platform.ui;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import rpg.core.message.MessageKey;
import rpg.core.ui.BossBarOccasion;
import rpg.core.ui.HudSurface;
import rpg.core.ui.SidebarLines;

/**
 * Die Naht aus ADR-005 und Constitution III.4: <b>wohin ein Wert geschrieben wird</b>.
 *
 * <p>Ein pack-fähiger Renderer tritt später an die Stelle des heutigen, ohne dass B04, B05 oder B08
 * sich ändern (FR-020, FR-022, SC-004). Das ist der einzige Grund, aus dem diese Schnittstelle
 * existiert — sie hat heute genau eine Umsetzung, und das ist in Ordnung.
 *
 * <h2>Vier Zusagen</h2>
 *
 * <ol>
 *   <li><b>Kein Aufruf bricht einen Aufrufer ab.</b> Ein Spieler, der gerade gegangen ist, eine
 *       abgeschaltete Fläche, ein Fehler beim Senden — alles davon ist ein normaler Ausgang und
 *       keine Ausnahme. Diese Methoden liegen in Pfaden, die <em>jedes</em> Ereignis berühren;
 *       dieselbe Regel, die {@code MobKinds} und {@code Hordes} in B10 aufstellen.
 *   <li><b>Nie ein Text, immer ein Schlüssel</b> (FR-014). Die Signatur lässt gar nichts anderes
 *       zu, und das ist der Punkt: ein {@code String}-Parameter wäre die Einladung, die der
 *       Wächter aus FR-015 später wieder einsammeln müsste.
 *   <li><b>{@link #clearBar} ist nicht dasselbe wie {@code bar(..., 0.0)}.</b> Ein leerer Balken
 *       steht noch da; eine geräumte Bossbar ist weg. Zwei verschiedene Dinge brauchen zwei
 *       Methoden, sonst muss jeder Aufrufer eine Konvention kennen.
 *   <li><b>{@link #bar} entscheidet die Rangfolge nicht selbst.</b> Der Aufrufer meldet den
 *       <em>Anlass</em>; welcher gewinnt, entscheidet {@code BossBarPriority} an einer Stelle
 *       (FR-004). Sonst kennt jeder Aufrufer die Rangfolge ein bisschen anders.
 * </ol>
 *
 * <h2>Was ausdrücklich nicht hinter dieser Naht liegt</h2>
 *
 * <p>Die <b>Skill-Leiste</b>. {@code AbilityHotbar} aus B08 bleibt, wo sie ist (FR-024). Das ist
 * eine Abweichung von Constitution III.4 und als ADR festgehalten (FR-024a) — ihr Preis ist, dass
 * ein pack-fähiger Client die Hotbar später nachziehen müsste.
 *
 * <h2>Warum diese Schnittstelle in {@code rpg-platform} liegt</h2>
 *
 * <p>Sie führt keinen einzigen Paper-Typ und <em>könnte</em> nach {@code rpg-core} — hätte dort
 * aber weder Aufrufer noch Umsetzung in derselben Schicht. Sie steht deshalb bei ihrer einzigen
 * Umsetzung. Sollte je ein Kernmodul zeichnen wollen, ist der Umzug nach unten eine Verschiebung
 * ohne Signaturänderung, also die Richtung, in der Constitution III.2 ihn erlaubt.
 * {@link ItemRenderer} <b>kann</b> dagegen gar nicht tiefer liegen: er gibt einen Bukkit-Typ
 * zurück.
 */
public interface HudRenderer {

    /**
     * Schreibt einen Inhalt auf eine der drei Flächen.
     *
     * @param playerId der <b>Spieler</b>, nicht sein Charakter — was auf dem Bildschirm landet,
     *     hängt am Konto; welcher Charakter die Zahlen liefert, hat der Aufrufer schon entschieden
     * @param key der Schlüssel des Textes; nie der Text selbst (FR-014)
     * @param values die Platzhalter, die {@code Messages} einsetzt
     */
    void show(UUID playerId, HudSurface surface, MessageKey key, Map<String, String> values);

    /**
     * Schreibt <b>mehrere</b> Zeilen auf eine Fläche — die Sidebar.
     *
     * <p><b>Diese Methode stand nicht im ersten Vertragsentwurf</b> (contracts/hud-api.md §1), und
     * das war eine Lücke: {@link #show} nimmt <em>einen</em> Schlüssel, die Sidebar trägt aber vier
     * Zeilen (Level, Erfahrung, Coins, Zone). Die Alternativen waren schlechter:
     *
     * <ul>
     *   <li><b>Viermal {@code show} rufen</b> hieße, dass der Renderer die Zeilen zwischen den
     *       Aufrufen sammelt — also Zustand hält und eine Konvention braucht, wann er fertig ist.
     *       Genau die Sorte Absprache, die Zusage 3 des Vertrags an anderer Stelle vermeidet.
     *   <li><b>Die Zeilen im Schlüssel zusammenfassen</b> hieße, das Layout im Code zu haben statt
     *       in der Sprachdatei (FR-014).
     * </ul>
     *
     * <p>Ein Scoreboard wird ohnehin als Ganzes gesetzt und nicht zeilenweise — die Signatur folgt
     * damit dem, was Paper tut, statt dagegen zu arbeiten.
     *
     * @param titleKey der Schlüssel der Überschrift
     * @param lines die Zeilen von oben nach unten; jede ein Schlüssel mit Platzhaltern
     */
    void showLines(
            UUID playerId, HudSurface surface, MessageKey titleKey, List<SidebarLines.Line> lines);

    /**
     * Räumt eine Fläche.
     *
     * <p>Für einen Spieler ohne gewählten Charakter (FR-008) und für eine Fläche, die der Betreiber
     * abgeschaltet hat. <b>Nicht</b> dasselbe wie einen leeren Text zu senden: leer ist eine
     * Anzeige, geräumt ist keine.
     */
    void clear(UUID playerId, HudSurface surface);

    /**
     * Setzt die Bossbar auf einen Anlass.
     *
     * @param fraction Füllstand in {@code [0,1]}; eine Rechnung gegen die Uhr, keine Aufgabe
     */
    void bar(
            UUID playerId,
            BossBarOccasion occasion,
            MessageKey key,
            Map<String, String> values,
            double fraction);

    /**
     * Entfernt die Bossbar dieses Anlasses.
     *
     * <p>Siehe Zusage 3: {@code bar(..., 0.0)} lässt eine leere Leiste stehen, das hier nimmt sie
     * weg. Auch der Weg, auf dem eine Bossbar beim Abmelden verschwindet (FR-004c).
     */
    void clearBar(UUID playerId, BossBarOccasion occasion);
}
