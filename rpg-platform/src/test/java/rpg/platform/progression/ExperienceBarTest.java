package rpg.platform.progression;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import rpg.core.event.DefaultEventBus;
import rpg.core.event.EventBus;
import rpg.core.progression.ProgressChangedEvent;
import rpg.core.scheduler.EntityRef;
import rpg.core.scheduler.Scheduler;
import rpg.core.scheduler.TaskHandle;
import rpg.core.scheduler.WorldPosition;

/**
 * Die Erfahrungsleiste zeigt B06s Level und Fortschritt - und speichert nichts.
 *
 * <p>Die Leiste gehört in Minecraft dem Spieler, ein Spieler hat aber bis zu drei Charaktere. Statt sie
 * ein zweites Mal zu speichern, wird sie aus dem gezeigt, was B06 ohnehin je Charakter hält - genau wie
 * die Herzen aus dem gezeigt werden, was B04 hält (ADR-003).
 */
class ExperienceBarTest {

    private static final Logger QUIET = Logger.getLogger("experience-bar-test");

    private ServerMock server;
    private DirectScheduler scheduler;
    private ExperienceBar bar;

    @BeforeEach
    void setUp() {
        QUIET.setLevel(Level.OFF);
        server = MockBukkit.mock();
        server.addSimpleWorld("world");
        scheduler = new DirectScheduler();
        bar = new ExperienceBar(server, scheduler, QUIET);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("Level und Anteil landen auf der Leiste")
    void levelAndFractionAreShown() {
        PlayerMock player = server.addPlayer();

        bar.show(player.getUniqueId(), 17, 0.25);

        assertThat(player.getLevel()).isEqualTo(17);
        assertThat(player.getExp()).isEqualTo(0.25f);
    }

    @Test
    @DisplayName("das Zurücksetzen leert auch die Gesamterfahrung, sonst kommt die Leiste zurück")
    void resetClearsTheTotalToo() {
        PlayerMock player = server.addPlayer();
        player.setLevel(30);
        player.setExp(0.9f);
        player.setTotalExperience(1000);

        bar.reset(player);

        assertThat(player.getLevel()).isZero();
        assertThat(player.getExp()).isZero();
        assertThat(player.getTotalExperience()).isZero();
    }

    @Test
    @DisplayName("jeder Fortschritt aus B06 erreicht die Leiste, ohne dass jemand sie ruft")
    void progressFromTheBusReachesTheBar() {
        PlayerMock player = server.addPlayer();
        EventBus eventBus = new DefaultEventBus(QUIET);
        bar.subscribeTo(eventBus);

        eventBus.publish(
                new ProgressChangedEvent(UUID.randomUUID(), player.getUniqueId(), 40L, 5, 40L, 80L));

        assertThat(player.getLevel()).isEqualTo(5);
        assertThat(player.getExp()).isEqualTo(0.5f);
    }

    @Test
    @DisplayName("auf Maximallevel ist die Leiste voll, nicht leer")
    void atTheMaximumTheBarIsFull() {
        PlayerMock player = server.addPlayer();
        EventBus eventBus = new DefaultEventBus(QUIET);
        bar.subscribeTo(eventBus);

        // xpForNextLevel = 0 heißt "es gibt kein nächstes Level".
        eventBus.publish(
                new ProgressChangedEvent(UUID.randomUUID(), player.getUniqueId(), 0L, 60, 0L, 0L));

        assertThat(player.getLevel()).isEqualTo(60);
        assertThat(player.getExp()).isEqualTo(1.0f);
    }

    @Test
    @DisplayName("ein Anteil jenseits von eins wird geklemmt statt geworfen")
    void anOverfullFractionIsClamped() {
        PlayerMock player = server.addPlayer();

        bar.show(player.getUniqueId(), 3, 1.4);

        assertThat(player.getExp()).isEqualTo(1.0f);
    }

    @Test
    @DisplayName("für einen Spieler, der nicht da ist, passiert nichts")
    void anAbsentPlayerIsSkipped() {
        bar.show(UUID.randomUUID(), 9, 0.5);

        assertThat(scheduler.entityTasks).as("die Aufgabe lief, fand aber niemanden").isEqualTo(1);
    }

    /** Führt entitätsgebundene Aufgaben sofort aus - hier geht es um das Was, nicht das Wann. */
    private static final class DirectScheduler implements Scheduler {

        private int entityTasks;

        @Override
        public TaskHandle runSyncAtLocation(WorldPosition position, Runnable task) {
            throw new UnsupportedOperationException("die Leiste benutzt das nicht");
        }

        @Override
        public TaskHandle runSyncOnEntity(EntityRef entity, Runnable task) {
            entityTasks++;
            task.run();
            return handle();
        }

        @Override
        public TaskHandle runAsync(Runnable task) {
            throw new UnsupportedOperationException("die Leiste benutzt das nicht");
        }

        @Override
        public TaskHandle runAsyncDelayed(Duration delay, Runnable task) {
            throw new UnsupportedOperationException("die Leiste benutzt das nicht");
        }

        private static TaskHandle handle() {
            return new TaskHandle() {
                @Override
                public void cancel() {
                    // nichts abzubrechen
                }

                @Override
                public boolean isCancelled() {
                    return false;
                }
            };
        }
    }
}
