package rpg.platform.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import rpg.core.scheduler.EntityRef;
import rpg.core.scheduler.Scheduler;
import rpg.core.scheduler.TaskHandle;
import rpg.core.scheduler.WorldPosition;

/**
 * T028 / FR-007, FR-008: the scheduler offers location- and entity-bound synchronous work plus
 * off-tick async work, and {@code cancel()} reliably prevents a task that has not run yet.
 *
 * <p>Runs against MockBukkit, so the platform layer is verifiable without a real server too.
 */
class PaperSchedulerAdapterTest {

    private ServerMock server;
    private Plugin plugin;
    private Scheduler scheduler;
    private WorldMock world;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("SchedulerProbe");
        world = server.addSimpleWorld("probe-world");
        Logger logger = Logger.getLogger(PaperSchedulerAdapterTest.class.getName());
        logger.setLevel(Level.OFF);
        scheduler = new PaperSchedulerAdapter(plugin, server, logger);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void asyncWorkStillRunsWhileThePluginIsBeingDisabled() {
        // The bug this is here for was a full session's progress on every server stop. Paper refuses to
        // register a task for a disabled plugin, and the plugin is already disabled when the modules
        // stop - which is exactly when the open sessions are ended and B02 flushes for the last time.
        // The refusal skipped the final write, escaped the flush, and left the pools open too.
        AtomicInteger runs = new AtomicInteger();
        server.getPluginManager().disablePlugin(plugin);

        TaskHandle handle = scheduler.runAsync(runs::incrementAndGet);

        assertThat(runs)
                .as("on the calling thread, which during shutdown is already off the tick")
                .hasValue(1);
        assertThat(handle.isCancelled()).isFalse();
    }

    @Test
    void tickBoundWorkIsDroppedWhileThePluginIsBeingDisabled() {
        // The opposite decision, and for the opposite reason: there is no tick left to run on, and
        // running it here would be running tick work off the tick. The cancelled handle is the answer
        // callers check.
        AtomicInteger runs = new AtomicInteger();
        server.getPluginManager().disablePlugin(plugin);

        TaskHandle atLocation = scheduler.runSyncAtLocation(positionIn(world), runs::incrementAndGet);
        TaskHandle onEntity =
                scheduler.runSyncOnEntity(new EntityRef(UUID.randomUUID()), runs::incrementAndGet);
        TaskHandle delayed = scheduler.runAsyncDelayed(Duration.ofMinutes(1), runs::incrementAndGet);

        assertThat(runs).as("nothing ran").hasValue(0);
        assertThat(atLocation.isCancelled()).isTrue();
        assertThat(onEntity.isCancelled()).isTrue();
        assertThat(delayed.isCancelled()).as("a delayed task is not due, and there is no later").isTrue();
    }

    @Test
    void aLocationBoundTaskRunsOnTheTick() {
        AtomicInteger runs = new AtomicInteger();

        scheduler.runSyncAtLocation(positionIn(world), runs::incrementAndGet);
        server.getScheduler().performTicks(2);

        assertThat(runs.get()).isEqualTo(1);
    }

    @Test
    void cancellingALocationBoundTaskPreventsItFromRunning() {
        AtomicInteger runs = new AtomicInteger();

        TaskHandle handle = scheduler.runSyncAtLocation(positionIn(world), runs::incrementAndGet);
        handle.cancel();
        server.getScheduler().performTicks(5);

        assertThat(handle.isCancelled()).isTrue();
        assertThat(runs.get()).isZero();
    }

    @Test
    void cancellingTwiceIsANoOp() {
        TaskHandle handle = scheduler.runSyncAtLocation(positionIn(world), () -> {});

        handle.cancel();
        handle.cancel();

        assertThat(handle.isCancelled()).isTrue();
    }

    @Test
    void anEntityBoundTaskRunsOnTheTick() {
        Entity entity = world.spawn(world.getSpawnLocation(), org.bukkit.entity.Zombie.class);
        AtomicInteger runs = new AtomicInteger();

        scheduler.runSyncOnEntity(new EntityRef(entity.getUniqueId()), runs::incrementAndGet);
        server.getScheduler().performTicks(2);

        assertThat(runs.get()).isEqualTo(1);
    }

    @Test
    void cancellingAnEntityBoundTaskPreventsItFromRunning() {
        Entity entity = world.spawn(world.getSpawnLocation(), org.bukkit.entity.Zombie.class);
        AtomicInteger runs = new AtomicInteger();

        TaskHandle handle =
                scheduler.runSyncOnEntity(
                        new EntityRef(entity.getUniqueId()), runs::incrementAndGet);
        handle.cancel();
        server.getScheduler().performTicks(5);

        assertThat(runs.get()).isZero();
    }

    // --- ADR-024: das verzoegerte synchrone Einzelstueck ------------------------------------------
    //
    // Ohne diese Methode laesst sich eine Wirkzeit nicht ausdruecken: sie muss zu einem bestimmten
    // spaeteren Zeitpunkt IM Tick wirken. Geprueft wird deshalb dreierlei - sie laeuft nicht zu frueh,
    // sie laeuft ueberhaupt, und ein Abbruch verhindert sie.

    @Test
    void aDelayedEntityBoundTaskDoesNotRunBeforeItsTime() {
        Entity entity = world.spawn(world.getSpawnLocation(), org.bukkit.entity.Zombie.class);
        AtomicInteger runs = new AtomicInteger();

        scheduler.runSyncOnEntityDelayed(
                new EntityRef(entity.getUniqueId()), Duration.ofMillis(500), runs::incrementAndGet);
        server.getScheduler().performTicks(5);

        assertThat(runs.get()).as("500 ms sind zehn Ticks - nach fuenf ist nichts faellig").isZero();
    }

    @Test
    void aDelayedEntityBoundTaskRunsOnceItsTimeHasCome() {
        Entity entity = world.spawn(world.getSpawnLocation(), org.bukkit.entity.Zombie.class);
        AtomicInteger runs = new AtomicInteger();

        scheduler.runSyncOnEntityDelayed(
                new EntityRef(entity.getUniqueId()), Duration.ofMillis(500), runs::incrementAndGet);
        server.getScheduler().performTicks(12);

        assertThat(runs.get()).isEqualTo(1);
    }

    @Test
    void cancellingADelayedEntityBoundTaskPreventsItFromRunning() {
        Entity entity = world.spawn(world.getSpawnLocation(), org.bukkit.entity.Zombie.class);
        AtomicInteger runs = new AtomicInteger();

        TaskHandle handle =
                scheduler.runSyncOnEntityDelayed(
                        new EntityRef(entity.getUniqueId()),
                        Duration.ofMillis(500),
                        runs::incrementAndGet);
        handle.cancel();
        server.getScheduler().performTicks(20);

        assertThat(handle.isCancelled()).isTrue();
        assertThat(runs.get()).isZero();
    }

    @Test
    void aZeroDelayRunsOnTheNextTickInsteadOfBeingRoundedUp() {
        // Paper lehnt eine Verzoegerung unter einem Tick ab. Stillschweigend aufzurunden haette
        // jedes `cast-time: 0` ueberall einen Tick zu spaet ankommen lassen.
        Entity entity = world.spawn(world.getSpawnLocation(), org.bukkit.entity.Zombie.class);
        AtomicInteger runs = new AtomicInteger();

        scheduler.runSyncOnEntityDelayed(
                new EntityRef(entity.getUniqueId()), Duration.ZERO, runs::incrementAndGet);
        server.getScheduler().performTicks(2);

        assertThat(runs.get()).isEqualTo(1);
    }

    @Test
    void aDelayedTaskBoundToAnUnknownEntityIsDropped() {
        AtomicInteger runs = new AtomicInteger();

        TaskHandle handle =
                scheduler.runSyncOnEntityDelayed(
                        new EntityRef(UUID.randomUUID()),
                        Duration.ofMillis(500),
                        runs::incrementAndGet);
        server.getScheduler().performTicks(20);

        assertThat(handle.isCancelled())
                .as("dieselbe Antwort wie beim sofortigen: ein bereits abgebrochener Handle")
                .isTrue();
        assertThat(runs.get()).isZero();
    }

    @Test
    void aNegativeDelayIsRefused() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () ->
                                scheduler.runSyncOnEntityDelayed(
                                        new EntityRef(UUID.randomUUID()),
                                        Duration.ofSeconds(-1),
                                        () -> {}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negative");
    }

    @Test
    void anAsyncTaskRunsOffTheTick() {
        AtomicInteger runs = new AtomicInteger();

        scheduler.runAsync(runs::incrementAndGet);
        server.getScheduler().waitAsyncTasksFinished();

        assertThat(runs.get()).isEqualTo(1);
    }

    @Test
    void aTaskBoundToAnUnloadedWorldIsDroppedInsteadOfRunningUnbound() {
        AtomicInteger runs = new AtomicInteger();

        // a world id nothing is loaded for: rather than falling back to some global scheduler, the
        // adapter must drop the task (ADR-007 - unbound tick work must never happen)
        TaskHandle handle =
                scheduler.runSyncAtLocation(
                        new WorldPosition(UUID.randomUUID(), 0, 64, 0), runs::incrementAndGet);
        server.getScheduler().performTicks(5);

        assertThat(handle.isCancelled()).isTrue();
        assertThat(runs.get()).isZero();
    }

    @Test
    void aTaskBoundToAnUnknownEntityIsDropped() {
        AtomicInteger runs = new AtomicInteger();

        TaskHandle handle =
                scheduler.runSyncOnEntity(new EntityRef(UUID.randomUUID()), runs::incrementAndGet);
        server.getScheduler().performTicks(5);

        assertThat(handle.isCancelled()).isTrue();
        assertThat(runs.get()).isZero();
    }

    @Test
    void aFailingTaskIsContainedAndDoesNotKillTheScheduler() {
        AtomicInteger runs = new AtomicInteger();

        scheduler.runSyncAtLocation(
                positionIn(world),
                () -> {
                    throw new IllegalStateException("task is broken");
                });
        scheduler.runSyncAtLocation(positionIn(world), runs::incrementAndGet);
        server.getScheduler().performTicks(3);

        assertThat(runs.get()).isEqualTo(1);
    }

    // --- runAsyncDelayed (T008, ADR-010) ---

    @Test
    void aDelayedAsyncTaskDoesNotRunBeforeItsDelayElapsed() {
        AtomicInteger runs = new AtomicInteger();

        scheduler.runAsyncDelayed(Duration.ofSeconds(5), runs::incrementAndGet);
        server.getScheduler().performTicks(2); // far short of 5 seconds

        // Deliberately no waitAsyncTasksFinished() here: MockBukkit's implementation drains
        // pending async tasks regardless of their remaining delay, so calling it would run the
        // task and make this assertion meaningless. That is a limitation of the test double, not
        // of the adapter - the delay itself is verified against a real server, and the positive
        // case below shows the task does run once enough ticks have passed.
        assertThat(runs.get()).isZero();
    }

    @Test
    void aDelayedAsyncTaskRunsOnceItsDelayElapsed() {
        AtomicInteger runs = new AtomicInteger();

        scheduler.runAsyncDelayed(Duration.ofMillis(50), runs::incrementAndGet);
        server.getScheduler().performTicks(40); // 40 ticks = 2 seconds, well past 50 ms
        server.getScheduler().waitAsyncTasksFinished();

        assertThat(runs.get()).isEqualTo(1);
    }

    @Test
    void cancellingADelayedAsyncTaskPreventsItFromRunning() {
        AtomicInteger runs = new AtomicInteger();

        TaskHandle handle =
                scheduler.runAsyncDelayed(Duration.ofMillis(50), runs::incrementAndGet);
        handle.cancel();
        server.getScheduler().performTicks(40);
        server.getScheduler().waitAsyncTasksFinished();

        assertThat(handle.isCancelled()).isTrue();
        assertThat(runs.get()).isZero();
    }

    @Test
    void aZeroDelayRunsImmediatelyInsteadOfBeingRejected() {
        AtomicInteger runs = new AtomicInteger();

        // Paper's runDelayed rejects a zero delay; the adapter routes it to runNow rather than
        // silently rounding it up to a tick.
        scheduler.runAsyncDelayed(Duration.ZERO, runs::incrementAndGet);
        server.getScheduler().waitAsyncTasksFinished();

        assertThat(runs.get()).isEqualTo(1);
    }

    @Test
    void aNegativeDelayIsRejected() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> scheduler.runAsyncDelayed(Duration.ofSeconds(-1), () -> {}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aSelfReschedulingCycleRunsRepeatedlyWithoutARepeatingApi() {
        // This is the shape B02's autosave uses: a one-shot that re-arms itself. Proving it here
        // means the persistence layer needs no runRepeating and no thread pool of its own.
        AtomicInteger runs = new AtomicInteger();
        Runnable[] cycle = new Runnable[1];
        cycle[0] =
                () -> {
                    if (runs.incrementAndGet() < 3) {
                        scheduler.runAsyncDelayed(Duration.ofMillis(20), cycle[0]);
                    }
                };

        scheduler.runAsyncDelayed(Duration.ofMillis(20), cycle[0]);
        for (int i = 0; i < 5; i++) {
            server.getScheduler().performTicks(20);
            server.getScheduler().waitAsyncTasksFinished();
        }

        assertThat(runs.get()).isEqualTo(3);
    }

    private static WorldPosition positionIn(World world) {
        return new WorldPosition(world.getUID(), 0, 64, 0);
    }
}
