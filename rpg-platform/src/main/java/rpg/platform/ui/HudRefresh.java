package rpg.platform.ui;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import rpg.core.message.MessageKey;
import rpg.core.ui.BossBarOccasion;
import rpg.core.ui.HudSurface;
import rpg.core.ui.SidebarLines;
import rpg.core.ui.UiConfig;
import rpg.core.ui.UiMessageKeys;

/**
 * <b>Ein</b> Eingang: „zeichne diesen Spieler jetzt neu."
 *
 * <p>Benutzt von {@link HudTick} (einmal je Sekunde für alle) <em>und</em> von den
 * Ereignisabonnements (sofort für einen). <b>Kein zweiter Zeichenweg neben dem Takt</b> — derselbe
 * Aufruf, nur ein anderer Anlass. Zwei Wege hießen zwei Änderungserkennungen, und die zweite wäre
 * die, die niemand pflegt.
 *
 * <h2>Warum es die Ereignisse überhaupt gibt</h2>
 *
 * <p>FR-009 verlangt „bei jeder Wertänderung <b>unmittelbar</b>". Der Sammeltakt allein erfüllt das
 * nicht: „unmittelbar" und „bis zu eine Sekunde später" sind zwei verschiedene Zusagen. Ein
 * Aufstieg, der eine Sekunde braucht, bis er auf der Sidebar steht, sieht aus, als hätte der Server
 * ihn verschluckt.
 *
 * <h2>Die Coin-Zeile ist die eine Ausnahme</h2>
 *
 * <p>{@code rpg.core.currency} führt <b>keinen Ereignistyp</b> — nur {@code CoinLedger},
 * {@code LedgerEntry} und {@code BookingResult}. Es gibt also nichts, worauf man sich für Coins
 * anmelden könnte; die Zeile folgt dem Takt und steht bis zu eine Sekunde später (FR-009a).
 *
 * <p><b>Nachgerüstet wird nichts.</b> Ein Ereignis in B08b wäre der Eingriff in einen fremden
 * Block, den FR-024, FR-070 und FR-071 an drei anderen Stellen ablehnen. Wenn die Sekunde stört,
 * gehört das Ereignis nach B08b — mit eigenem Namen und eigener Aufgabe, nicht als Nebenwirkung von
 * B13.
 *
 * <h2>Gesendet wird nur bei Änderung — mit einer Ausnahme</h2>
 *
 * <p>FR-013: Sidebar und Bossbar werden nur beschrieben, wenn ihr Inhalt sich geändert hat. Die
 * <b>Actionbar nicht</b>: Minecraft blendet sie nach etwa zwei Sekunden aus, also erzwingt das
 * Ausblenden das erneute Senden (FR-011). Wer für sie dieselbe Sparsamkeit einbaut, bekommt eine
 * Zeile, die im Sekundentakt blinkt.
 *
 * <h2>Abgeschaltet heißt kostenlos</h2>
 *
 * <p>Die Prüfung steht <b>vor</b> der Berechnung, nicht dahinter (FR-013a, SC-010). Nach der
 * Berechnung zu verwerfen wäre unsichtbar, aber nicht umsonst — und bei 200 Spielern ist der
 * Unterschied genau die Arbeit, die SC-010 messbar verbietet.
 */
public final class HudRefresh {

    /**
     * Woher die Sidebar-Zeilen eines Spielers kommen.
     *
     * <p><b>Leer heißt „kein Charakter"</b> (FR-008) — nicht „keine Zeilen". Die Unterscheidung
     * trägt die Sichtbarkeitsregel, ohne dass hier eine zweite Nachfrage nötig wäre.
     */
    public interface SidebarSource {
        Optional<List<SidebarLines.Line>> linesFor(UUID playerId);
    }

    /** Was gerade auf der Bossbar stehen soll, wenn überhaupt etwas. */
    public interface BossBarSource {
        Optional<BossBarContent> contentFor(UUID playerId);
    }

    /**
     * Die Actionbar zeichnet sich selbst.
     *
     * <p>{@code StatusActionBar} entscheidet, welche der drei Zeilenformen gilt (mit Mana, ohne
     * Mana, mit Zähler) — diese Auswahl gehört zu B05/B06 und nicht hierher.
     */
    public interface ActionBarSource {
        void show(UUID playerId);
    }

    /** Ein Bossbar-Inhalt: der Anlass, der Text und der Füllstand. */
    public record BossBarContent(
            BossBarOccasion occasion, MessageKey key, Map<String, String> values, double fraction) {

        public BossBarContent {
            Objects.requireNonNull(occasion, "occasion");
            Objects.requireNonNull(key, "key");
            values = Map.copyOf(Objects.requireNonNull(values, "values"));
            if (fraction < 0.0 || fraction > 1.0 || Double.isNaN(fraction)) {
                throw new IllegalArgumentException(
                        "fraction ist " + fraction + " - erlaubt ist [0,1]");
            }
        }
    }

    private final HudRenderer renderer;
    private final Supplier<UiConfig> config;
    private final ActionBarSource actionBar;
    private final SidebarSource sidebar;
    private final BossBarSource bossBar;
    private final Logger logger;

    /**
     * Der zuletzt gesendete Inhalt je Spieler — die Grundlage von FR-013.
     *
     * <p>{@link ConcurrentHashMap}, weil der Takt asynchron läuft und die Ereignisse aus dem Tick
     * kommen. Das ist der einzige veränderliche Zustand dieses Blocks, und er hängt am Spieler
     * (Constitution I.6).
     */
    private final Map<UUID, List<SidebarLines.Line>> lastSidebar = new ConcurrentHashMap<>();

    private final Map<UUID, BossBarContent> lastBossBar = new ConcurrentHashMap<>();

    public HudRefresh(
            HudRenderer renderer,
            Supplier<UiConfig> config,
            ActionBarSource actionBar,
            SidebarSource sidebar,
            BossBarSource bossBar,
            Logger logger) {
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.config = Objects.requireNonNull(config, "config");
        this.actionBar = Objects.requireNonNull(actionBar, "actionBar");
        this.sidebar = Objects.requireNonNull(sidebar, "sidebar");
        this.bossBar = Objects.requireNonNull(bossBar, "bossBar");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * Zeichnet alle drei Flächen dieses Spielers neu, soweit nötig.
     *
     * <p><b>Wirft nie.</b> Ein Fehler beim Zeichnen darf keinen Tick kosten und keinen Aufrufer
     * mitreißen (Constitution VI); {@code StatusActionBar} fängt heute schon lokal, und das gilt
     * für jede neue Fläche.
     */
    public void refresh(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        // Je Durchlauf frisch: ein einmal gemerkter Wert ueberlebt das Nachladen, und der Betreiber
        // saehe eine Umstellung, die nicht stattfindet (FR-013c).
        UiConfig current = config.get();

        refreshActionBar(playerId, current);
        refreshSidebar(playerId, current);
        refreshBossBar(playerId, current);
    }

    /** Vergisst, was dieser Spieler zuletzt gesehen hat — beim Abmelden (FR-004c). */
    public void forget(UUID playerId) {
        lastSidebar.remove(playerId);
        lastBossBar.remove(playerId);
    }

    /** Wie viele Spieler gerade einen gemerkten Stand haben — für den Test gegen ein Leck. */
    int trackedPlayers() {
        return Math.max(lastSidebar.size(), lastBossBar.size());
    }

    private void refreshActionBar(UUID playerId, UiConfig current) {
        // Abgeschaltet heisst kostenlos: die Pruefung steht VOR dem Aufruf, nicht darin.
        if (!current.isEnabled(HudSurface.ACTION_BAR)) {
            return;
        }
        // KEINE Aenderungserkennung: Minecraft blendet die Actionbar nach etwa zwei Sekunden aus,
        // also ist erneutes Senden ohne Aenderung hier richtig und nicht verschwenderisch (FR-011).
        guard("action bar", playerId, () -> actionBar.show(playerId));
    }

    private void refreshSidebar(UUID playerId, UiConfig current) {
        if (!current.isEnabled(HudSurface.SIDEBAR)) {
            return;
        }
        guard(
                "sidebar",
                playerId,
                () -> {
                    Optional<List<SidebarLines.Line>> lines = sidebar.linesFor(playerId);
                    if (lines.isEmpty()) {
                        // Kein Charakter: raeumen statt leer beschreiben - und nur, wenn vorher
                        // etwas dastand, sonst waere das Raeumen selbst die Arbeit ohne Anlass.
                        if (lastSidebar.remove(playerId) != null) {
                            renderer.clear(playerId, HudSurface.SIDEBAR);
                        }
                        return;
                    }
                    List<SidebarLines.Line> next = lines.get();
                    if (next.equals(lastSidebar.get(playerId))) {
                        return;
                    }
                    lastSidebar.put(playerId, next);
                    renderer.showLines(
                            playerId, HudSurface.SIDEBAR, UiMessageKeys.SIDEBAR_TITLE, next);
                });
    }

    private void refreshBossBar(UUID playerId, UiConfig current) {
        if (!current.isEnabled(HudSurface.BOSS_BAR)) {
            return;
        }
        guard(
                "boss bar",
                playerId,
                () -> {
                    Optional<BossBarContent> content = bossBar.contentFor(playerId);
                    if (content.isEmpty()) {
                        BossBarContent previous = lastBossBar.remove(playerId);
                        if (previous != null) {
                            renderer.clearBar(playerId, previous.occasion());
                        }
                        return;
                    }
                    BossBarContent next = content.get();
                    if (next.equals(lastBossBar.get(playerId))) {
                        return;
                    }
                    BossBarContent previous = lastBossBar.put(playerId, next);
                    // Wechselt der ANLASS, muss der alte weg: es gibt genau eine Bossbar je Spieler
                    // (FR-004b), und zwei stapelten sich am oberen Bildrand.
                    if (previous != null && previous.occasion() != next.occasion()) {
                        renderer.clearBar(playerId, previous.occasion());
                    }
                    renderer.bar(
                            playerId, next.occasion(), next.key(), next.values(), next.fraction());
                });
    }

    /**
     * Ein Fehler beim Zeichnen kostet keinen Tick.
     *
     * <p>Er wird lokal begrenzt und protokolliert (Constitution VI). Ohne das risse ein einzelner
     * Spieler mit kaputtem Zustand den ganzen Durchlauf mit — und damit die Anzeige aller anderen.
     */
    private void guard(String what, UUID playerId, Runnable body) {
        try {
            body.run();
        } catch (RuntimeException failure) {
            logger.log(Level.WARNING, "[ui] " + what + " failed for " + playerId, failure);
        }
    }
}
