package rpg.core.module;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;

import rpg.core.config.AbstractConfigLoader;
import rpg.core.config.ConfigValidationException;
import rpg.core.event.DefaultEventBus;
import rpg.core.scheduler.EntityRef;
import rpg.core.scheduler.Scheduler;
import rpg.core.scheduler.TaskHandle;
import rpg.core.scheduler.WorldPosition;

/**
 * T041 / SC-001 / SC-007: measures the two time budgets B01 commits to.
 *
 * <p>What is measured here is the framework's own overhead - dependency resolution, ordering,
 * lifecycle handling and the shutdown supervision - across a module count well beyond the 17 blocks
 * the architecture plans for. The remaining budget is what B02-B17 may spend inside their own
 * {@code start()}; this test is what tells a future block whether it, and not the foundation, is the
 * reason a start became slow.
 */
class BootstrapPerformanceTest {

    /** SC-001: bootstrap to readiness for the first join. */
    private static final Duration BOOTSTRAP_BUDGET = Duration.ofSeconds(30);

    private static ModuleBootstrap newBootstrap(BootstrapState state) {
        Logger logger = Logger.getLogger(BootstrapPerformanceTest.class.getName());
        logger.setLevel(Level.OFF);
        return new ModuleBootstrap(
                new DefaultModuleRegistry(),
                new DefaultEventBus(logger),
                new NoopScheduler(),
                new NoopConfigLoader(),
                state,
                logger);
    }

    @Test
    void resolvingAndStartingAChainOfModulesIsFarInsideTheBootstrapBudget() {
        BootstrapState state = new BootstrapState();
        ModuleBootstrap bootstrap = newBootstrap(state);

        // 200 modules in a dependency chain - an order of magnitude more than the 17 planned blocks
        List<Module> modules = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            String id = String.format("module-%03d", i);
            List<String> dependencies =
                    i == 0 ? List.of() : List.of(String.format("module-%03d", i - 1));
            modules.add(new TrivialModule(id, dependencies));
        }
        modules.forEach(bootstrap::add);

        long startedAt = System.nanoTime();
        bootstrap.start();
        Duration took = Duration.ofNanos(System.nanoTime() - startedAt);

        assertThat(state.acceptsPlayers()).isTrue();
        assertThat(bootstrap.startedModuleIds()).hasSize(200);
        // The foundation must be a rounding error against the 30s budget, so essentially the whole
        // budget stays available to the blocks themselves.
        assertThat(took).isLessThan(Duration.ofSeconds(1));
        assertThat(took).isLessThan(BOOTSTRAP_BUDGET);
    }

    @Test
    void aWideDependencyGraphResolvesQuicklyToo() {
        BootstrapState state = new BootstrapState();
        ModuleBootstrap bootstrap = newBootstrap(state);

        bootstrap.add(new TrivialModule("root", List.of()));
        for (int i = 0; i < 200; i++) {
            bootstrap.add(new TrivialModule(String.format("leaf-%03d", i), List.of("root")));
        }

        long startedAt = System.nanoTime();
        bootstrap.start();
        Duration took = Duration.ofNanos(System.nanoTime() - startedAt);

        assertThat(bootstrap.startedModuleIds()).hasSize(201).first().isEqualTo("root");
        assertThat(took).isLessThan(Duration.ofSeconds(1));
    }

    @Test
    void aCleanShutdownOfManyModulesStaysWellInsideTheirCombinedBudget() {
        BootstrapState state = new BootstrapState();
        ModuleBootstrap bootstrap = newBootstrap(state);
        for (int i = 0; i < 50; i++) {
            bootstrap.add(new TrivialModule(String.format("module-%03d", i), List.of()));
        }
        bootstrap.start();

        long startedAt = System.nanoTime();
        bootstrap.shutdown();
        Duration took = Duration.ofNanos(System.nanoTime() - startedAt);

        // 50 well-behaved modules must not come anywhere near 50 x 10s
        assertThat(took).isLessThan(Duration.ofSeconds(5));
    }

    @Test
    void oneHangingModuleCostsExactlyItsOwnBudgetAndNothingMore() throws Exception {
        BootstrapState state = new BootstrapState();
        ModuleBootstrap bootstrap = newBootstrap(state);
        CountDownLatch released = new CountDownLatch(1);

        for (int i = 0; i < 5; i++) {
            bootstrap.add(new TrivialModule(String.format("fast-%d", i), List.of()));
        }
        bootstrap.add(
                new TrivialModule("hangs", List.of()) {
                    @Override
                    public void stop() throws Exception {
                        released.await();
                    }
                });
        bootstrap.start();

        long startedAt = System.nanoTime();
        bootstrap.shutdown();
        Duration took = Duration.ofNanos(System.nanoTime() - startedAt);

        // SC-007: the one bad module costs its 10s; the other five are not charged for it
        assertThat(took).isGreaterThanOrEqualTo(ModuleBootstrap.SHUTDOWN_TIMEOUT);
        assertThat(took).isLessThan(ModuleBootstrap.SHUTDOWN_TIMEOUT.plusSeconds(5));

        released.countDown();
    }

    /** Module doing nothing, so the measurement is of the framework rather than of module work. */
    private static class TrivialModule implements Module {

        private final String id;
        private final List<String> dependencies;

        TrivialModule(String id, List<String> dependencies) {
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
            // nothing on purpose
        }
    }

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

    private static final class NoopConfigLoader extends AbstractConfigLoader {

        @Override
        protected Map<String, Object> parse(Path source) throws ConfigValidationException {
            return Map.of();
        }
    }
}
