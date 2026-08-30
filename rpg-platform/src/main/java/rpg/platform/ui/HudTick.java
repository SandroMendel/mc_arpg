package rpg.platform.ui;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import rpg.core.scheduler.Scheduler;
import rpg.core.ui.UiConfig;

/**
 * <b>Der eine</b> Sammeltakt (FR-010, FR-012, contracts/hud-api.md §3).
 *
 * <h2>Es ist die Erweiterung eines vorhandenen Takts, kein zweiter daneben</h2>
 *
 * <p>{@code StatusActionBar.startRefresh} war eine Sekunde lang, aus genau dem Grund, den FR-011
 * nennt, und plante sich nach ADR-007 selbst neu ein. B13 erweitert <b>diesen einen</b> zum
 * HUD-Takt; {@code startRefresh} entfällt dafür. Dieselbe Entscheidung wie B11
 * ({@code ConsumableBuffs} auf dem Fähigkeiten-Sweep) und B12 ({@code PlaytimeAccrual} auf dem
 * Inventar-Sweep).
 *
 * <h2>Ein Durchlauf über alle Spieler, nicht einer je Spieler</h2>
 *
 * <p>Constitution II.2 verbietet eine wiederkehrende Aufgabe je Spieler oder je Entität. Bei 200
 * Spielern wären das 200 Aufgaben je Sekunde statt einer — und der Unterschied ist nicht die
 * Rechenzeit, sondern die Warteschlange des Schedulers.
 *
 * <h2>Er plant sich selbst neu ein</h2>
 *
 * <p>Der {@link Scheduler} hat keine wiederkehrende Aufgabe, und zwar mit Absicht (ADR-007): eine
 * ortsgebundene Einplanung hält den Folia-Pfad offen. Der Takt hört von selbst auf, wenn das Plugin
 * abgeschaltet wird — der Scheduler liefert dann einen abgebrochenen Handle und führt den Rumpf nie
 * aus, der den nächsten Durchlauf einplanen würde.
 *
 * <p><b>Und ein scheiternder Durchlauf staut sich nicht auf.</b> Die Neueinplanung steht im
 * {@code finally}: ein Fehler beendet den Takt nicht, aber er verdoppelt ihn auch nicht.
 *
 * <h2>Die Regel, an der dieser Takt still scheitern könnte</h2>
 *
 * <p>Er läuft <b>asynchron</b>. Aus einem asynchronen Kontext liefert
 * {@code Scheduler.runSyncOnEntity} für alles außer einem <b>Spieler</b> einen bereits
 * abgebrochenen Handle — ohne Fehler, ohne Log, ohne dass ein Test rot wird. Für Spieler ist er
 * richtig; für alles andere gehört {@code runSyncAtLocation} dorthin, und wer eine Entität erst
 * <em>im</em> Tick auflöst, ist auf der sicheren Seite.
 *
 * <p>Das ist die Falle aus T112 (B10): <b>der Fehler bleibt grün und zeichnet nur manchmal.</b> Für
 * die Actionbar ist sie folgenlos, weil dort ein Spieler das Ziel ist; für die Schadensanzeigen
 * wäre sie genau der Fehler, den niemand findet.
 */
public final class HudTick {

    private final Scheduler scheduler;
    private final Supplier<UiConfig> config;
    private final Supplier<List<UUID>> players;
    private final HudRefresh refresh;
    private final Logger logger;

    private volatile boolean running;

    public HudTick(
            Scheduler scheduler,
            Supplier<UiConfig> config,
            Supplier<List<UUID>> players,
            HudRefresh refresh,
            Logger logger) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.config = Objects.requireNonNull(config, "config");
        this.players = Objects.requireNonNull(players, "players");
        this.refresh = Objects.requireNonNull(refresh, "refresh");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * Startet den Takt.
     *
     * <p><b>Ein zweiter Aufruf tut nichts.</b> Zwei laufende Takte wären zwei Durchläufe je
     * Sekunde, und welcher zuletzt sendet, hinge an der Reihenfolge — genau die Sorte Fehler, die
     * {@code HudTickIsOneTest} sucht.
     */
    public void start() {
        if (running) {
            logger.warning("[ui] hud tick already running - ignoring the second start");
            return;
        }
        running = true;
        scheduleNext();
    }

    /** Hält den Takt an. Der laufende Durchlauf endet noch, danach wird nichts mehr eingeplant. */
    public void stop() {
        running = false;
    }

    /** Ob gerade ein Takt läuft. */
    public boolean isRunning() {
        return running;
    }

    /**
     * Ein Durchlauf: alle Spieler, einmal.
     *
     * <p>Sichtbar für den Test, damit er einen Durchlauf auslösen kann, ohne eine Sekunde zu warten
     * — eine wartende Prüfung wäre langsam und flackerte.
     */
    public void runOnce() {
        for (UUID playerId : players.get()) {
            // refresh faengt selbst je Flaeche; hier steht kein zweiter Faenger, sonst verdeckte er
            // die Stelle, an der es schiefging.
            refresh.refresh(playerId);
        }
    }

    private void scheduleNext() {
        if (!running) {
            return;
        }
        // Das Intervall kommt je Durchlauf aus der aktuellen Konfiguration: nachgeladen wirkt es
        // beim naechsten Mal (FR-013c). Ein einmal gelesenes Intervall waere nach /reload das alte,
        // und der Betreiber saehe eine Umstellung, die nicht stattfindet.
        scheduler.runAsyncDelayed(
                config.get().tick(),
                () -> {
                    try {
                        runOnce();
                    } catch (RuntimeException failure) {
                        logger.log(Level.WARNING, "[ui] hud tick pass failed", failure);
                    } finally {
                        // Im finally: ein gescheiterter Durchlauf beendet den Takt nicht - aber er
                        // plant ihn auch nur einmal neu ein, statt sich aufzustauen.
                        scheduleNext();
                    }
                });
    }
}
