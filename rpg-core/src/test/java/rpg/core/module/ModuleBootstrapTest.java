package rpg.core.module;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import rpg.core.config.AbstractConfigLoader;
import rpg.core.config.ConfigValidationException;
import rpg.core.event.DefaultEventBus;
import rpg.core.scheduler.EntityRef;
import rpg.core.scheduler.Scheduler;
import rpg.core.scheduler.TaskHandle;
import rpg.core.scheduler.WorldPosition;

/**
 * Covers the bootstrap rules of User Story 1 without a running server: fail-fast (FR-013), the
 * per-module 10 second shutdown budget (FR-012, SC-007, quickstart section 5) and the join block
 * (FR-013).
 */
class ModuleBootstrapTest {

    private DefaultModuleRegistry registry;
    private BootstrapState state;
    private ModuleBootstrap bootstrap;
    private final List<String> startLog = new ArrayList<>();
    private final List<String> stopLog = new ArrayList<>();

    @BeforeEach
    void setUp() {
        Logger logger = Logger.getLogger(ModuleBootstrapTest.class.getName());
        logger.setLevel(Level.OFF); // the assertions are about behaviour, not about log noise
        registry = new DefaultModuleRegistry();
        state = new BootstrapState();
        bootstrap =
                new ModuleBootstrap(
                        registry,
                        new DefaultEventBus(logger),
                        new NoopScheduler(),
                        new NoopConfigLoader(),
                        state,
                        logger);
    }

    @Test
    void modulesStartInDependencyOrderAndTheServerThenAcceptsPlayers() {
        bootstrap.add(new RecordingModule("combat", List.of("stat-engine")));
        bootstrap.add(new RecordingModule("stat-engine", List.of()));

        assertThat(state.acceptsPlayers()).isFalse();

        bootstrap.start();

        assertThat(startLog).containsExactly("stat-engine", "combat");
        assertThat(state.phase()).isEqualTo(BootstrapState.Phase.READY);
        assertThat(state.acceptsPlayers()).isTrue();
        assertThat(bootstrap.lifecycleStates())
                .containsEntry("combat", Module.LifecycleState.ACTIVE)
                .containsEntry("stat-engine", Module.LifecycleState.ACTIVE);
    }

    @Test
    void aFailingModuleAbortsTheBootstrapAndPlayersStayLockedOut() {
        bootstrap.add(new RecordingModule("healthy", List.of()));
        bootstrap.add(
                new RecordingModule("broken", List.of("healthy")) {
                    @Override
                    public void start(ModuleContext context) {
                        throw new IllegalStateException("missing database credentials");
                    }
                });

        assertThatThrownBy(bootstrap::start)
                .isInstanceOf(ModuleStartupException.class)
                .hasMessageContaining("broken")
                .hasMessageContaining("missing database credentials");

        assertThat(state.phase()).isEqualTo(BootstrapState.Phase.FAILED);
        assertThat(state.acceptsPlayers()).isFalse();
        assertThat(bootstrap.lifecycleStates())
                .containsEntry("broken", Module.LifecycleState.FAILED);
        // the module that had already started must be stopped again - no half-initialised server
        assertThat(stopLog).containsExactly("healthy");
    }

    @Test
    void modulesStopInReverseStartOrder() {
        bootstrap.add(new RecordingModule("a", List.of()));
        bootstrap.add(new RecordingModule("b", List.of("a")));
        bootstrap.add(new RecordingModule("c", List.of("b")));

        bootstrap.start();
        bootstrap.shutdown();

        assertThat(startLog).containsExactly("a", "b", "c");
        assertThat(stopLog).containsExactly("c", "b", "a");
    }

    @Test
    void aHangingModuleIsAbandonedAfterTheTimeoutAndDoesNotBlockTheRest() throws Exception {
        CountDownLatch released = new CountDownLatch(1);
        bootstrap.add(new RecordingModule("fast-one", List.of()));
        bootstrap.add(
                new RecordingModule("hangs-forever", List.of("fast-one")) {
                    @Override
                    public void stop() throws Exception {
                        stopLog.add(id());
                        released.await(); // never released within the test
                    }
                });

        bootstrap.start();

        long startedAt = System.nanoTime();
        bootstrap.shutdown();
        Duration took = Duration.ofNanos(System.nanoTime() - startedAt);

        // the hanging module is abandoned after its budget; the module behind it still gets stopped
        assertThat(took).isGreaterThanOrEqualTo(ModuleBootstrap.SHUTDOWN_TIMEOUT);
        assertThat(took).isLessThan(ModuleBootstrap.SHUTDOWN_TIMEOUT.plusSeconds(5));
        assertThat(stopLog).containsExactly("hangs-forever", "fast-one");
        assertThat(bootstrap.lifecycleStates())
                .containsEntry("hangs-forever", Module.LifecycleState.STOPPED)
                .containsEntry("fast-one", Module.LifecycleState.STOPPED);

        released.countDown();
    }

    @Test
    void aModuleThrowingOnStopDoesNotStopTheShutdown() {
        bootstrap.add(new RecordingModule("quiet", List.of()));
        bootstrap.add(
                new RecordingModule("noisy", List.of("quiet")) {
                    @Override
                    public void stop() {
                        stopLog.add(id());
                        throw new IllegalStateException("failed to flush");
                    }
                });

        bootstrap.start();
        bootstrap.shutdown();

        assertThat(stopLog).containsExactly("noisy", "quiet");
    }

    @Test
    void aDuplicateModuleIdIsRejectedWhenTheSecondModuleIsAdded() {
        bootstrap.add(new RecordingModule("stat-engine", List.of()));

        assertThatThrownBy(() -> bootstrap.add(new RecordingModule("stat-engine", List.of())))
                .isInstanceOf(DuplicateModuleIdException.class)
                .hasMessageContaining("stat-engine");
    }

    @Test
    void aCyclicGraphFailsTheBootstrapAndPlayersStayLockedOut() {
        bootstrap.add(new RecordingModule("a", List.of("b")));
        bootstrap.add(new RecordingModule("b", List.of("a")));

        assertThatThrownBy(bootstrap::start).isInstanceOf(CyclicDependencyException.class);

        assertThat(state.phase()).isEqualTo(BootstrapState.Phase.FAILED);
        assertThat(state.acceptsPlayers()).isFalse();
    }

    @Test
    void servicesOfAStoppedModuleAreNoLongerResolvable() {
        bootstrap.add(
                new RecordingModule("provider", List.of()) {
                    @Override
                    public void start(ModuleContext context) {
                        startLog.add(id());
                        context.registry()
                                .registerService(id(), CharSequence.class, "provided-value");
                    }
                });

        bootstrap.start();
        assertThat(registry.getService(CharSequence.class)).isEqualTo("provided-value");

        bootstrap.shutdown();
        assertThat(registry.findService(CharSequence.class)).isEmpty();
    }

    /** Module that records when it is started and stopped. */
    private class RecordingModule implements Module {

        private final String id;
        private final List<String> dependencies;

        RecordingModule(String id, List<String> dependencies) {
            this.id = id;
            this.dependencies = dependencies;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public List<String> dependencies() {
            return dependencies;
        }

        @Override
        public void start(ModuleContext context) throws Exception {
            startLog.add(id);
        }

        @Override
        public void stop() throws Exception {
            stopLog.add(id);
        }
    }

    /** The bootstrap only passes the scheduler through; no test here schedules anything. */
    private static final class NoopScheduler implements Scheduler {

        @Override
        public TaskHandle runSyncAtLocation(WorldPosition position, Runnable task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TaskHandle runSyncOnEntity(EntityRef entity, Runnable task) {
            throw new UnsupportedOperationException();
        }

        /** ADR-024: verzoegert, aber im Test genauso behandelt wie sofort. */
        @Override
        public TaskHandle runSyncOnEntityDelayed(EntityRef entity, Duration delay, Runnable task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TaskHandle runAsync(Runnable task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TaskHandle runAsyncDelayed(Duration delay, Runnable task) {
            throw new UnsupportedOperationException();
        }
    }

    /** Config loader that serves an empty document for every source. */
    private static final class NoopConfigLoader extends AbstractConfigLoader {

        @Override
        protected Map<String, Object> parse(Path source) throws ConfigValidationException {
            return Map.of();
        }
    }

    /** Guards against the hanging-module test silently degrading into a fast pass. */
    @Test
    void theShutdownBudgetIsTheTenSecondsTheSpecRequires() {
        assertThat(ModuleBootstrap.SHUTDOWN_TIMEOUT).isEqualTo(Duration.ofSeconds(10));
        assertThat(TimeUnit.SECONDS.toMillis(10))
                .isEqualTo(ModuleBootstrap.SHUTDOWN_TIMEOUT.toMillis());
    }
}
