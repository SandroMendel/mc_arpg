package rpg.platform.classes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import rpg.core.scheduler.EntityRef;
import rpg.core.scheduler.Scheduler;
import rpg.core.scheduler.TaskHandle;
import rpg.core.scheduler.WorldPosition;

/**
 * Die Auswahl läuft ab: Warnung nach einer Minute, Trennung nach zwei.
 *
 * <p>Ohne das hält ein Spieler im Menü unbegrenzt eine Sitzung, den geladenen Zustand und einen Platz
 * auf dem Server - ohne je die Welt zu betreten und ohne ansprechbar zu sein.
 */
class SelectionTimeoutTest {

    private ServerMock server;
    private FakeScheduler scheduler;
    private SelectionTimeout timeout;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        server.addSimpleWorld("world");
        scheduler = new FakeScheduler();
        timeout = new SelectionTimeout(server, scheduler, PlatformClassFixture.messages());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("das Öffnen plant zwei Aufgaben: eine Warnung und eine Trennung")
    void openingSchedulesBoth() {
        PlayerMock player = server.addPlayer();

        timeout.start(player);

        assertThat(scheduler.delays)
                .containsExactly(SelectionTimeout.WARN_AFTER, SelectionTimeout.LIMIT);
        assertThat(timeout.isRunning(player.getUniqueId())).isTrue();
    }

    @Test
    @DisplayName("die Warnung kommt vor dem Ende, nicht zusammen mit ihm")
    void theWarningComesFirst() {
        assertThat(SelectionTimeout.WARN_AFTER)
                .as("eine Warnung nach Ablauf wäre keine")
                .isLessThan(SelectionTimeout.LIMIT);
    }

    @Test
    @DisplayName("erneutes Öffnen verlängert die Frist nicht (FR-033)")
    void reopeningDoesNotExtendTheLimit() {
        // Das Menü geht bei jedem Schließversuch wieder auf. Würde die Uhr dabei neu starten, wäre die
        // Frist durch Drücken von Escape beliebig hinauszuschieben.
        PlayerMock player = server.addPlayer();

        timeout.start(player);
        timeout.start(player);
        timeout.start(player);

        assertThat(scheduler.delays).hasSize(2);
    }

    @Test
    @DisplayName("eine getroffene Wahl bricht beide Aufgaben ab")
    void choosingCancelsBoth() {
        PlayerMock player = server.addPlayer();
        timeout.start(player);

        timeout.cancel(player.getUniqueId());

        assertThat(timeout.isRunning(player.getUniqueId())).isFalse();
        assertThat(scheduler.handles).allSatisfy(handle -> assertThat(handle.isCancelled()).isTrue());
    }

    @Test
    @DisplayName("nach dem Abbruch passiert nichts mehr, auch wenn die Aufgabe noch anläuft")
    void aCancelledTimeoutDoesNothingWhenItFires() {
        // Eine bereits an den Tick übergebene Aufgabe läuft trotz Abbruch noch. Sie darf niemanden
        // trennen, der längst gewählt hat.
        PlayerMock player = server.addPlayer();
        timeout.start(player);
        timeout.cancel(player.getUniqueId());

        scheduler.runAll();

        assertThat(player.isOnline()).isTrue();
    }

    @Test
    @DisplayName("nach Ablauf wird getrennt - mit Grund, nicht wortlos")
    void theLimitDisconnects() {
        PlayerMock player = server.addPlayer();
        timeout.start(player);

        scheduler.runAll();

        assertThat(player.isOnline()).isFalse();
        assertThat(timeout.isRunning(player.getUniqueId()))
                .as("die Uhr räumt sich selbst auf")
                .isFalse();
    }

    @Test
    @DisplayName("ein Spieler, der von selbst gegangen ist, lässt die Uhr ins Leere laufen")
    void anAbsentPlayerIsNotKicked() {
        PlayerMock player = server.addPlayer();
        UUID playerId = player.getUniqueId();
        timeout.start(player);
        player.disconnect();

        assertThatCode(() -> scheduler.runAll())
                .as("kein Wurf, wenn der Spieler nicht mehr aufzulösen ist")
                .doesNotThrowAnyException();
        assertThat(server.getPlayer(playerId)).isNull();
    }

    /** Merkt sich Verzögerungen und Aufgaben, statt sie sofort auszuführen. */
    private static final class FakeScheduler implements Scheduler {

        private final List<Duration> delays = new ArrayList<>();
        private final List<Runnable> tasks = new ArrayList<>();
        private final List<TaskHandle> handles = new ArrayList<>();

        /** Erst die verzögerte Aufgabe, dann der Sprung auf den Tick - wie in Wirklichkeit. */
        void runAll() {
            List<Runnable> pending = new ArrayList<>(tasks);
            tasks.clear();
            pending.forEach(Runnable::run);
            List<Runnable> hopped = new ArrayList<>(tasks);
            tasks.clear();
            hopped.forEach(Runnable::run);
        }

        @Override
        public TaskHandle runSyncAtLocation(WorldPosition position, Runnable task) {
            throw new UnsupportedOperationException("die Uhr benutzt das nicht");
        }

        @Override
        public TaskHandle runSyncOnEntity(EntityRef entity, Runnable task) {
            tasks.add(task);
            return record();
        }

        @Override
        public TaskHandle runAsync(Runnable task) {
            throw new UnsupportedOperationException("die Uhr benutzt das nicht");
        }

        @Override
        public TaskHandle runAsyncDelayed(Duration delay, Runnable task) {
            delays.add(delay);
            tasks.add(task);
            return record();
        }

        private TaskHandle record() {
            TaskHandle handle =
                    new TaskHandle() {
                        private boolean cancelled;

                        @Override
                        public void cancel() {
                            cancelled = true;
                        }

                        @Override
                        public boolean isCancelled() {
                            return cancelled;
                        }
                    };
            handles.add(handle);
            return handle;
        }
    }
}
