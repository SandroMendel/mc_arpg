package rpg.platform.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.progression.ProgressView;
import rpg.core.scheduler.EntityRef;
import rpg.core.scheduler.Scheduler;
import rpg.core.scheduler.TaskHandle;
import rpg.core.scheduler.WorldPosition;
import rpg.core.ui.DamageNumberSetting;
import rpg.core.ui.SidebarLines;
import rpg.core.ui.SurfaceSetting;
import rpg.core.ui.UiConfig;

/**
 * Eine <b>wiederholbare Messung ohne Volllast</b> für den Sammeltakt (T148, SC-001, Prinzip VII.4).
 *
 * <h2>Was sie misst</h2>
 *
 * <p>Die <b>eigene Rechenarbeit</b> eines Durchlaufs über 200 Spieler: Konfiguration lesen,
 * Sichtbarkeit prüfen, Zeilen bauen, mit dem letzten Stand vergleichen. Das ist der Teil, den B13
 * verantwortet und der bei jedem Takt anfällt.
 *
 * <h2>Was sie ausdrücklich NICHT misst (T149)</h2>
 *
 * <ul>
 *   <li><b>Paketkosten.</b> Was Paper aus einem {@code sendActionBar} macht, wie groß ein
 *       Scoreboard-Paket ist, wie oft es zusammengefasst wird — nichts davon ist hier sichtbar.
 *   <li><b>Netzwerk.</b> Die Zeit zwischen Server und Client kommt in keiner Zahl vor.
 *   <li><b>Den Tick des Servers.</b> Der Takt läuft <em>asynchron</em>; was er kostet, kostet er
 *       neben dem Tick und nicht darin. Die Sprünge in den Tick sind hier durch einen Doppelgänger
 *       ersetzt, der sofort ausführt.
 *   <li><b>Das Verhalten unter Last.</b> Was sich erst bei 150 Spielern und 800 Mobs zeigt, prüft
 *       <b>B15</b> (ADR-031) — und zwar im Zusammenspiel, wo es aussagekräftig ist.
 * </ul>
 *
 * <p><b>Eine Messung, deren Grenzen man kennt, ist mehr wert als eine, der man alles zutraut.</b>
 * Diese hier beantwortet genau eine Frage: bleibt die eigene Arbeit eines Durchlaufs in der
 * Größenordnung, die SC-001 nennt — oder ist irgendwo eine Schleife über alle Spieler <em>in</em>
 * der Schleife über alle Spieler gelandet?
 *
 * <h2>Warum keine harte Zeitschranke</h2>
 *
 * <p>Eine Assertion auf „unter 1 ms" wäre auf einem ausgelasteten CI-Rechner ein Flackern und auf
 * einem schnellen ein leeres Versprechen. Geprüft wird stattdessen, dass die Kosten <b>linear</b>
 * mit der Spielerzahl wachsen: zehnmal so viele Spieler dürfen nicht hundertmal so teuer sein. Das
 * ist die Eigenschaft, die kaputtgeht, wenn jemand versehentlich quadratisch wird — und sie ist
 * unabhängig von der Maschine.
 */
class HudTickCostBenchmark {

    private static final Logger QUIET = Logger.getLogger("hud-tick-benchmark");

    @Test
    @DisplayName("T148: ein Durchlauf ueber 200 Spieler bleibt eine Runde, keine 200")
    void onePassOverTwoHundredPlayersIsOnePass() {
        QUIET.setLevel(Level.OFF);
        Fixture fixture = new Fixture(200);

        fixture.tick.runOnce();

        // FR-012: keine eigene geplante Arbeit je Spieler. Der Doppelgaenger zaehlt jede
        // Einplanung - waere hier eine je Spieler, staenden 200 da.
        assertThat(fixture.scheduler.scheduled)
                .as("ein Durchlauf plant nichts je Spieler ein")
                .isZero();
        assertThat(fixture.renderer.callsFor(rpg.core.ui.HudSurface.SIDEBAR)).isEqualTo(200);
    }

    @Test
    @DisplayName("T148: die Kosten wachsen linear mit der Spielerzahl, nicht quadratisch")
    void thecostGrowsLinearly() {
        QUIET.setLevel(Level.OFF);

        long small = measure(20);
        long large = measure(200);

        // Zehnmal so viele Spieler. Bei linearem Wachstum liegt der Faktor um 10; bei
        // quadratischem bei 100. Die Schranke ist bewusst weit - gemessen wird die GROESSENORDNUNG,
        // nicht die Maschine.
        double factor = (double) large / Math.max(1, small);

        assertThat(factor)
                .as(
                        "20 Spieler: "
                                + small
                                + " ns, 200 Spieler: "
                                + large
                                + " ns - eine Schleife ueber alle Spieler INNERHALB der Schleife"
                                + " ueber alle Spieler laege hier bei ~100")
                .isLessThan(30.0);
    }

    @Test
    @DisplayName("T148: ein zweiter Durchlauf ohne Aenderung ist deutlich billiger")
    void asecondPassWithoutChangesIsCheaper() {
        QUIET.setLevel(Level.OFF);
        Fixture fixture = new Fixture(200);

        fixture.tick.runOnce();
        fixture.renderer.reset();
        fixture.tick.runOnce();

        // FR-013 in Zahlen: der zweite Durchlauf baut die Zeilen zwar noch, sendet aber nichts.
        // Genau das ist der Unterschied zwischen "unsichtbar" und "kostenlos" - und der Grund,
        // warum der Vergleich vor dem Senden steht und nicht danach.
        assertThat(fixture.renderer.callsFor(rpg.core.ui.HudSurface.SIDEBAR))
                .as("nichts hat sich geaendert, also wird nichts gesendet")
                .isZero();
    }

    @Test
    @DisplayName("T148: mit allen Flaechen abgeschaltet kostet ein Durchlauf fast nichts")
    void withEverythingDisabledAPassIsNearlyFree() {
        // SC-010 als Messung: abgeschaltet heisst KOSTENLOS. Die Zeilen werden gar nicht erst
        // zusammengesetzt - die Pruefung steht VOR der Berechnung.
        QUIET.setLevel(Level.OFF);
        Fixture on = new Fixture(200);
        Fixture off = new Fixture(200);
        off.allOff = true;

        on.tick.runOnce();
        off.tick.runOnce();

        assertThat(off.sidebarQueries).as("keine einzige Zeile gebaut").isZero();
        assertThat(on.sidebarQueries).isEqualTo(200);
    }

    /** Ein Durchlauf, gemessen — nach einem Aufwärmen, damit die JIT-Kompilierung nicht mitzählt. */
    private static long measure(int players) {
        Fixture fixture = new Fixture(players);
        for (int i = 0; i < 50; i++) {
            fixture.coins++;
            fixture.tick.runOnce();
        }

        long start = System.nanoTime();
        for (int i = 0; i < 100; i++) {
            fixture.coins++;
            fixture.tick.runOnce();
        }
        return (System.nanoTime() - start) / 100;
    }

    // --- Aufbau ---------------------------------------------------------------

    private static final class Fixture {

        final RecordingHudRenderer renderer = new RecordingHudRenderer();
        final CountingScheduler scheduler = new CountingScheduler();
        final HudTick tick;
        final List<UUID> players = new ArrayList<>();

        volatile boolean allOff;
        volatile long coins = 100;
        int sidebarQueries;

        Fixture(int playerCount) {
            for (int i = 0; i < playerCount; i++) {
                players.add(UUID.randomUUID());
            }
            HudRefresh refresh =
                    new HudRefresh(
                            renderer,
                            this::config,
                            playerId -> {},
                            this::linesFor,
                            playerId -> Optional.empty(),
                            QUIET);
            this.tick =
                    new HudTick(scheduler, this::config, () -> players, refresh, QUIET);
        }

        private Optional<List<SidebarLines.Line>> linesFor(UUID playerId) {
            sidebarQueries++;
            return Optional.of(
                    SidebarLines.of(new ProgressView(5, 10, 100, false), coins, Optional.empty()));
        }

        private UiConfig config() {
            SurfaceSetting setting = allOff ? SurfaceSetting.off() : SurfaceSetting.on();
            return new UiConfig(
                    "en",
                    Duration.ofSeconds(1),
                    setting,
                    setting,
                    setting,
                    Duration.ofSeconds(4),
                    new DamageNumberSetting(true, Duration.ofMillis(1200), 1.4));
        }
    }

    /** Zählt Einplanungen und führt nichts aus — {@code runOnce} braucht keinen Scheduler. */
    private static final class CountingScheduler implements Scheduler {

        int scheduled;

        @Override
        public TaskHandle runAsyncDelayed(Duration delay, Runnable task) {
            scheduled++;
            return handle();
        }

        @Override
        public TaskHandle runAsync(Runnable task) {
            scheduled++;
            return handle();
        }

        @Override
        public TaskHandle runSyncAtLocation(WorldPosition position, Runnable task) {
            scheduled++;
            return handle();
        }

        @Override
        public TaskHandle runSyncOnEntity(EntityRef entity, Runnable task) {
            scheduled++;
            return handle();
        }

        @Override
        public TaskHandle runSyncOnEntityDelayed(EntityRef entity, Duration delay, Runnable task) {
            scheduled++;
            return handle();
        }

        private static TaskHandle handle() {
            return new TaskHandle() {

                @Override
                public void cancel() {}

                @Override
                public boolean isCancelled() {
                    return false;
                }
            };
        }
    }
}
