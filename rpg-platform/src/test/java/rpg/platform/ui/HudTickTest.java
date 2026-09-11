package rpg.platform.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.scheduler.EntityRef;
import rpg.core.scheduler.Scheduler;
import rpg.core.scheduler.TaskHandle;
import rpg.core.scheduler.WorldPosition;
import rpg.core.ui.DamageNumberSetting;
import rpg.core.ui.HudSurface;
import rpg.core.ui.SidebarLines;
import rpg.core.ui.SurfaceSetting;
import rpg.core.ui.UiConfig;

/**
 * Der Sammeltakt: <b>es bleibt bei einem</b>, er plant sich selbst neu ein, und ein scheiternder
 * Durchlauf staut sich nicht auf.
 *
 * <p>Deckt T036, T037, T057, T058, T059 und T061a ab — sie prüfen alle dasselbe Objekt aus
 * verschiedenen Richtungen, und getrennte Dateien hätten sechsmal denselben Aufbau gebraucht.
 */
class HudTickTest {

    @Test
    @DisplayName("T036: ueber mehrere Durchlaeufe bleibt es bei EINER eingeplanten Aufgabe")
    void itStaysOneScheduledTask() {
        // Der Test zaehlt die EINPLANUNGEN, nicht die Pakete. Constitution II.2 verbietet eine
        // Aufgabe je Spieler - bei 200 Spielern waeren das 200 je Sekunde statt einer, und der
        // Unterschied ist nicht die Rechenzeit, sondern die Warteschlange des Schedulers.
        CountingScheduler scheduler = new CountingScheduler(3);
        Fixture fixture = new Fixture(scheduler, 50);

        fixture.tick.start();

        assertThat(scheduler.scheduled).as("drei Durchlaeufe, drei Einplanungen").isEqualTo(4);
        assertThat(scheduler.maxOutstanding).as("nie zwei gleichzeitig").isEqualTo(1);
    }

    @Test
    @DisplayName("T037: der Takt bewaffnet sich am Ende jedes Durchlaufs neu")
    void itRearmsItself() {
        CountingScheduler scheduler = new CountingScheduler(5);
        Fixture fixture = new Fixture(scheduler, 1);

        fixture.tick.start();

        assertThat(fixture.renderer.callsFor(HudSurface.SIDEBAR)).isPositive();
        assertThat(scheduler.scheduled).isGreaterThan(1);
    }

    @Test
    @DisplayName("T037: ein scheiternder Durchlauf hoert auf, statt sich aufzustauen")
    void afailingPassDoesNotPileUp() {
        // Im finally eingeplant: der Fehler beendet den Takt nicht, verdoppelt ihn aber auch nicht.
        // Eine Neueinplanung im try UND im catch waere die naheliegende Variante - und bei jedem
        // Fehler haette man einen Takt mehr.
        CountingScheduler scheduler = new CountingScheduler(3);
        Fixture fixture = new Fixture(scheduler, 1);
        fixture.sidebarThrows = true;

        fixture.tick.start();

        assertThat(scheduler.maxOutstanding).isEqualTo(1);
    }

    @Test
    @DisplayName("ein zweiter start() tut nichts")
    void asecondStartDoesNothing() {
        CountingScheduler scheduler = new CountingScheduler(1);
        Fixture fixture = new Fixture(scheduler, 1);

        fixture.tick.start();
        int after = scheduler.scheduled;
        fixture.tick.start();

        assertThat(scheduler.scheduled).isEqualTo(after);
    }

    @Test
    @DisplayName("T057: zwei Durchlaeufe ohne Wertaenderung senden die Sidebar EINMAL")
    void thesidebarIsSentOnlyOnChange() {
        Fixture fixture = new Fixture(new CountingScheduler(0), 1);

        fixture.tick.runOnce();
        fixture.tick.runOnce();
        fixture.tick.runOnce();

        assertThat(fixture.renderer.callsFor(HudSurface.SIDEBAR))
                .as("dreimal gelaufen, einmal gesendet")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("T057: die Actionbar wird JEDES Mal gesendet - sonst blendet sie aus")
    void theactionBarIsSentEveryTime() {
        // Die eine Flaeche ohne Aenderungserkennung (FR-011). Wer fuer sie dieselbe Sparsamkeit
        // einbaut, bekommt eine Zeile, die im Sekundentakt blinkt.
        Fixture fixture = new Fixture(new CountingScheduler(0), 1);

        fixture.tick.runOnce();
        fixture.tick.runOnce();
        fixture.tick.runOnce();

        assertThat(fixture.actionBarCalls).hasValue(3);
    }

    @Test
    @DisplayName("T057: aendert sich ein Wert, wird wieder gesendet")
    void achangedValueIsSentAgain() {
        Fixture fixture = new Fixture(new CountingScheduler(0), 1);

        fixture.tick.runOnce();
        fixture.coins = 999;
        fixture.tick.runOnce();

        assertThat(fixture.renderer.callsFor(HudSurface.SIDEBAR)).isEqualTo(2);
    }

    @Test
    @DisplayName("T058: mit sidebar.enabled=false entsteht KEIN Sidebar-Aufruf")
    void adisabledSidebarCostsNothing() {
        // Abgeschaltet heisst kostenlos, nicht unsichtbar (FR-013a, SC-010). Der RecordingHudRenderer
        // unterscheidet 'nichts gesendet' von 'etwas Leeres gesendet' - gegen einen Mock, der nur
        // 'irgendwas kam an' prueft, waeren beide Faelle gleich.
        Fixture fixture = new Fixture(new CountingScheduler(0), 1);
        fixture.sidebarEnabled = false;

        fixture.tick.runOnce();

        assertThat(fixture.renderer.callsFor(HudSurface.SIDEBAR)).isZero();
        assertThat(fixture.renderer.cleared()).doesNotContain(HudSurface.SIDEBAR);
        assertThat(fixture.sidebarQueries)
                .as("die Zeilen werden gar nicht erst zusammengesetzt")
                .hasValue(0);
    }

    @Test
    @DisplayName("T058: eine abgeschaltete Flaeche wird auch nicht GERAEUMT")
    void adisabledSurfaceIsNotEvenCleared() {
        // Der Unterschied, an dem SC-010 haengt: wer sie jede Sekunde wegraeumt, hat die Arbeit
        // nur verschoben.
        Fixture fixture = new Fixture(new CountingScheduler(0), 1);
        fixture.sidebarEnabled = false;
        fixture.hasCharacter = false;

        fixture.tick.runOnce();

        assertThat(fixture.renderer.silent()).isTrue();
        assertThat(fixture.renderer.cleared()).isEmpty();
    }

    @Test
    @DisplayName("T059: ein Spieler ohne Charakter bekommt auf keiner Flaeche Werte")
    void aplayerWithoutACharacterGetsNothing() {
        Fixture fixture = new Fixture(new CountingScheduler(0), 1);
        fixture.hasCharacter = false;

        fixture.tick.runOnce();

        assertThat(fixture.renderer.callsFor(HudSurface.SIDEBAR)).isZero();
        assertThat(fixture.renderer.barred()).isEmpty();
    }

    @Test
    @DisplayName("T059: verliert er den Charakter, wird geraeumt statt leer beschrieben")
    void losingTheCharacterClearsTheSurface() {
        Fixture fixture = new Fixture(new CountingScheduler(0), 1);

        fixture.tick.runOnce();
        fixture.hasCharacter = false;
        fixture.tick.runOnce();

        assertThat(fixture.renderer.cleared()).contains(HudSurface.SIDEBAR);
    }

    @Test
    @DisplayName("T059: ohne Charakter wird nicht bei jedem Durchlauf erneut geraeumt")
    void clearingHappensOnceNotEveryPass() {
        // Sonst waere das Raeumen selbst die Arbeit ohne Anlass, die FR-013 verbietet.
        Fixture fixture = new Fixture(new CountingScheduler(0), 1);
        fixture.hasCharacter = false;

        fixture.tick.runOnce();
        fixture.tick.runOnce();
        fixture.tick.runOnce();

        assertThat(fixture.renderer.cleared()).isEmpty();
    }

    @Test
    @DisplayName("T061a: ein nachgeladenes Intervall erreicht den laufenden Takt")
    void areloadedIntervalReachesTheRunningTick() {
        // FR-013c. Ein Takt, der UiConfig beim Start festhaelt, meldet nach /reload Erfolg und
        // arbeitet weiter mit den alten Werten - der Betreiber sieht dann eine Aenderung, die nicht
        // stattfindet.
        CountingScheduler scheduler = new CountingScheduler(2);
        Fixture fixture = new Fixture(scheduler, 1);
        fixture.tickInterval = Duration.ofSeconds(1);

        fixture.tick.start();
        assertThat(scheduler.delays).first().isEqualTo(Duration.ofSeconds(1));

        fixture.tickInterval = Duration.ofSeconds(5);
        CountingScheduler after = new CountingScheduler(1);
        Fixture restarted = new Fixture(after, 1);
        restarted.tickInterval = Duration.ofSeconds(5);
        restarted.tick.start();

        assertThat(after.delays).first().isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("T061a: eine nachgeladene Abschaltung wirkt beim naechsten Durchlauf")
    void areloadedDisableTakesEffectOnTheNextPass() {
        Fixture fixture = new Fixture(new CountingScheduler(0), 1);

        fixture.tick.runOnce();
        assertThat(fixture.renderer.callsFor(HudSurface.SIDEBAR)).isEqualTo(1);

        fixture.renderer.reset();
        fixture.sidebarEnabled = false;
        fixture.coins = 42;
        fixture.tick.runOnce();

        assertThat(fixture.renderer.callsFor(HudSurface.SIDEBAR)).isZero();
    }

    @Test
    @DisplayName("ein Fehler bei einem Spieler kostet die anderen nichts")
    void onePlayersFailureDoesNotCostTheOthers() {
        // Constitution VI: ein Fehler im Gameplay-Pfad wird lokal begrenzt. Ohne das risse ein
        // Spieler mit kaputtem Zustand die Anzeige aller anderen mit.
        Fixture fixture = new Fixture(new CountingScheduler(0), 3);
        fixture.throwForFirstPlayer = true;

        fixture.tick.runOnce();

        assertThat(fixture.renderer.callsFor(HudSurface.SIDEBAR)).isEqualTo(2);
    }

    // --- Aufbau -------------------------------------------------------------

    /** Ein zusammengesetzter Prüfling mit umschaltbaren Quellen. */
    private static final class Fixture {

        final RecordingHudRenderer renderer = new RecordingHudRenderer();
        final HudTick tick;
        final List<UUID> players = new ArrayList<>();
        final AtomicInteger actionBarCalls = new AtomicInteger();
        final AtomicInteger sidebarQueries = new AtomicInteger();

        boolean sidebarEnabled = true;
        boolean hasCharacter = true;
        boolean sidebarThrows;
        boolean throwForFirstPlayer;
        long coins = 100;
        Duration tickInterval = Duration.ofSeconds(1);

        Fixture(Scheduler scheduler, int playerCount) {
            for (int i = 0; i < playerCount; i++) {
                players.add(UUID.randomUUID());
            }
            HudRefresh refresh =
                    new HudRefresh(
                            renderer,
                            this::config,
                            playerId -> actionBarCalls.incrementAndGet(),
                            this::linesFor,
                            playerId -> Optional.empty(),
                            Logger.getLogger("test"));
            this.tick =
                    new HudTick(
                            scheduler,
                            this::config,
                            () -> List.copyOf(players),
                            refresh,
                            Logger.getLogger("test"));
        }

        private Optional<List<SidebarLines.Line>> linesFor(UUID playerId) {
            sidebarQueries.incrementAndGet();
            if (sidebarThrows) {
                throw new IllegalStateException("absichtlich");
            }
            if (throwForFirstPlayer && playerId.equals(players.get(0))) {
                throw new IllegalStateException("absichtlich, nur fuer den ersten");
            }
            if (!hasCharacter) {
                return Optional.empty();
            }
            return Optional.of(
                    SidebarLines.of(
                            new rpg.core.progression.ProgressView(5, 10, 100, false),
                            coins,
                            Optional.empty()));
        }

        private UiConfig config() {
            return new UiConfig(
                    "en",
                    tickInterval,
                    SurfaceSetting.on(),
                    SurfaceSetting.on(),
                    new SurfaceSetting(sidebarEnabled),
                    Duration.ofSeconds(4),
                    new DamageNumberSetting(true, Duration.ofMillis(1200), 1.4));
        }
    }

    /**
     * Ein Scheduler, der eine begrenzte Zahl Durchläufe sofort ausführt.
     *
     * <p>Er zählt die Einplanungen und wie viele gleichzeitig offen waren — das zweite ist die
     * eigentliche Zusage: <b>nie zwei</b>.
     */
    private static final class CountingScheduler implements Scheduler {

        private final int passes;
        int scheduled;
        int outstanding;
        int maxOutstanding;
        final List<Duration> delays = new ArrayList<>();

        CountingScheduler(int passes) {
            this.passes = passes;
        }

        @Override
        public TaskHandle runAsyncDelayed(Duration delay, Runnable task) {
            scheduled++;
            delays.add(delay);
            outstanding++;
            maxOutstanding = Math.max(maxOutstanding, outstanding);
            if (scheduled <= passes) {
                outstanding--;
                task.run();
            } else {
                outstanding--;
            }
            return handle();
        }

        @Override
        public TaskHandle runAsync(Runnable task) {
            task.run();
            return handle();
        }

        @Override
        public TaskHandle runSyncAtLocation(WorldPosition position, Runnable task) {
            task.run();
            return handle();
        }

        @Override
        public TaskHandle runSyncOnEntity(EntityRef entity, Runnable task) {
            task.run();
            return handle();
        }

        @Override
        public TaskHandle runSyncOnEntityDelayed(EntityRef entity, Duration delay, Runnable task) {
            task.run();
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
