package rpg.platform.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import rpg.core.message.MessageKey;
import rpg.core.ui.BossBarOccasion;
import rpg.core.ui.HudSurface;
import rpg.core.ui.SidebarLines;

/**
 * Ein {@link HudRenderer}, der Aufrufe sammelt statt zu senden.
 *
 * <h2>Warum er die Messlatte für FR-013 und FR-013a ist</h2>
 *
 * <p>Beide Anforderungen sagen dasselbe auf zwei Arten: <b>„nichts gesendet"</b> muss ein Test von
 * <b>„etwas Leeres gesendet"</b> unterscheiden können.
 *
 * <ul>
 *   <li>FR-013 — gesendet wird nur bei Änderung. Ein zweiter Durchlauf ohne Wertänderung darf für
 *       Sidebar und Bossbar <em>gar keinen</em> Aufruf erzeugen.
 *   <li>FR-013a — eine abgeschaltete Fläche erzeugt <b>keine Arbeit</b>. „Abgeschaltet heißt
 *       kostenlos, nicht unsichtbar": ein Renderer, der brav einen leeren Text bekäme, hätte die
 *       Zusage schon gebrochen, und niemand sähe es.
 * </ul>
 *
 * <p>Gegen einen Mock, der nur „irgendwas kam an" prüft, sind beide Fälle nicht unterscheidbar.
 * Deshalb zählt diese Klasse Aufrufe, statt sie zu bewerten — und deshalb wirft sie nie.
 *
 * <h2>Die eine Ausnahme: {@link #ACTION_BAR} wird auch ohne Änderung gesendet</h2>
 *
 * <p>Minecraft blendet die Actionbar nach etwa zwei Sekunden aus (FR-011). Ein Test, der für sie
 * dieselbe Sparsamkeit erwartet wie für die Sidebar, prüft das Gegenteil der Anforderung.
 * {@link #callsFor(HudSurface)} macht die Unterscheidung sichtbar, statt sie einem Kommentar zu
 * überlassen.
 */
final class RecordingHudRenderer implements HudRenderer {

    /** Ein aufgezeichneter Aufruf auf einer der drei Flächen. */
    record Shown(UUID playerId, HudSurface surface, MessageKey key, Map<String, String> values) {}

    /** Ein aufgezeichneter Bossbar-Aufruf. */
    record Barred(
            UUID playerId,
            BossBarOccasion occasion,
            MessageKey key,
            Map<String, String> values,
            double fraction) {}

    private final List<Shown> shown = new ArrayList<>();
    private final List<List<SidebarLines.Line>> linesShown = new ArrayList<>();
    private final List<Barred> barred = new ArrayList<>();
    private final List<HudSurface> cleared = new ArrayList<>();
    private final List<BossBarOccasion> barsCleared = new ArrayList<>();

    @Override
    public void show(
            UUID playerId, HudSurface surface, MessageKey key, Map<String, String> values) {
        shown.add(new Shown(playerId, surface, key, Map.copyOf(values)));
    }

    @Override
    public void showLines(
            UUID playerId, HudSurface surface, MessageKey titleKey, List<SidebarLines.Line> lines) {
        // Als EIN Aufruf gezaehlt, nicht als vier: ein Scoreboard wird als Ganzes gesetzt, und
        // FR-013 fragt, ob gesendet wurde - nicht, wie viele Zeilen dabei waren.
        shown.add(new Shown(playerId, surface, titleKey, Map.of()));
        linesShown.add(List.copyOf(lines));
    }

    @Override
    public void clear(UUID playerId, HudSurface surface) {
        cleared.add(surface);
    }

    @Override
    public void bar(
            UUID playerId,
            BossBarOccasion occasion,
            MessageKey key,
            Map<String, String> values,
            double fraction) {
        barred.add(new Barred(playerId, occasion, key, Map.copyOf(values), fraction));
    }

    @Override
    public void clearBar(UUID playerId, BossBarOccasion occasion) {
        barsCleared.add(occasion);
    }

    // --- Was ein Test fragt -------------------------------------------------

    /** Jeder Flächenaufruf, in der Reihenfolge des Eintreffens. */
    List<Shown> shown() {
        return List.copyOf(shown);
    }

    /** Jeder Bossbar-Aufruf, in der Reihenfolge des Eintreffens. */
    List<Barred> barred() {
        return List.copyOf(barred);
    }

    /**
     * Wie oft auf dieser Fläche gesendet wurde.
     *
     * <p><b>Null ist die interessante Antwort.</b> Für Sidebar und Bossbar heißt sie „nichts zu
     * tun, also nichts getan"; für {@link HudSurface#ACTION_BAR} wäre sie ein Fehler, weil die
     * Fläche sonst ausblendet.
     */
    long callsFor(HudSurface surface) {
        return shown.stream().filter(call -> call.surface() == surface).count();
    }

    /** Wie oft dieser Anlass auf die Bossbar gesetzt wurde. */
    long barCallsFor(BossBarOccasion occasion) {
        return barred.stream().filter(call -> call.occasion() == occasion).count();
    }

    /** Welche Flächen geräumt wurden — für FR-008 und die abgeschaltete Fläche. */
    List<HudSurface> cleared() {
        return List.copyOf(cleared);
    }

    /** Welche Bossbar-Anlässe geräumt wurden — für FR-004c, das Abmelden. */
    List<BossBarOccasion> barsCleared() {
        return List.copyOf(barsCleared);
    }

    /** Kein einziger Aufruf, auf keiner Fläche und auf keinem Balken. */
    boolean silent() {
        return shown.isEmpty() && barred.isEmpty();
    }

    /** Die Sidebar-Zeilen jedes {@code showLines}-Aufrufs, in ihrer Reihenfolge. */
    List<List<SidebarLines.Line>> linesShown() {
        return List.copyOf(linesShown);
    }

    /** Setzt die Aufzeichnung zurück — für den zweiten Durchlauf in einem Sparsamkeitstest. */
    void reset() {
        linesShown.clear();
        shown.clear();
        barred.clear();
        cleared.clear();
        barsCleared.clear();
    }
}
