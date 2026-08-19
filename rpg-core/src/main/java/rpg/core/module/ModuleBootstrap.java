package rpg.core.module;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

import rpg.core.config.ConfigLoader;
import rpg.core.event.EventBus;
import rpg.core.scheduler.Scheduler;

/**
 * Drives module start-up and shutdown.
 *
 * <p>Lives in {@code rpg-core} rather than in the plugin class so the rules it enforces - the
 * deterministic order (FR-001), fail-fast on a failed module (FR-013) and the per-module 10 second
 * shutdown budget (FR-012) - are unit-testable without a running server (Constitution VII.1). The
 * plugin's {@code onEnable}/{@code onDisable} do nothing but call {@link #start()} and
 * {@link #shutdown()}.
 */
public final class ModuleBootstrap {

    /** Per-module shutdown budget before the module is abandoned (FR-012, SC-007). */
    public static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(10);

    private final DefaultModuleRegistry registry;
    private final EventBus eventBus;
    private final Scheduler scheduler;
    private final ConfigLoader configLoader;
    private final ModuleLifecycleLogger lifecycleLogger;
    private final BootstrapState state;
    private final Logger logger;

    private final Map<String, Module> modules = new LinkedHashMap<>();
    private final Map<String, Module.LifecycleState> states = new LinkedHashMap<>();
    private final List<String> startedInOrder = new ArrayList<>();

    public ModuleBootstrap(
            DefaultModuleRegistry registry,
            EventBus eventBus,
            Scheduler scheduler,
            ConfigLoader configLoader,
            BootstrapState state,
            Logger logger) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.configLoader = Objects.requireNonNull(configLoader, "configLoader");
        this.state = Objects.requireNonNull(state, "state");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.lifecycleLogger = new ModuleLifecycleLogger(logger);
    }

    /**
     * Adds a module to the bootstrap.
     *
     * @throws DuplicateModuleIdException if another module already claimed that identifier (FR-001a)
     */
    public void add(Module module) {
        Objects.requireNonNull(module, "module");
        String id = module.id();
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException(
                    module.getClass().getName() + " returned a blank module id");
        }
        Module existing = modules.get(id);
        if (existing != null) {
            throw new DuplicateModuleIdException(
                    id, existing.getClass().getName(), module.getClass().getName());
        }
        modules.put(id, module);
        registry.registerModule(id, module.dependencies());
    }

    /**
     * Starts every registered module in dependency order.
     *
     * <p>Fail-fast: the first module that throws aborts the bootstrap, leaves the state
     * {@link BootstrapState.Phase#FAILED} and stops the modules already started, so the server never
     * ends up half-initialised (FR-013).
     *
     * @throws ModuleStartupException if any module failed to start
     */
    public void start() {
        state.markInProgress();
        long phaseStartedAt = System.nanoTime();

        List<String> order;
        try {
            order = registry.resolveStartOrder();
        } catch (RuntimeException resolutionFailure) {
            state.markFailed(resolutionFailure.getMessage());
            throw resolutionFailure;
        }
        lifecycleLogger.logStartOrder(order);

        for (String moduleId : order) {
            Module module = modules.get(moduleId);
            if (module == null) {
                // registered directly on the registry by another block, nothing to start here
                continue;
            }
            states.put(moduleId, Module.LifecycleState.INITIALIZING);
            long startedAt = System.nanoTime();
            try {
                module.start(
                        new DefaultModuleContext(
                                moduleId, registry, eventBus, scheduler, configLoader));
                states.put(moduleId, Module.LifecycleState.ACTIVE);
                startedInOrder.add(moduleId);
                lifecycleLogger.logSuccess(
                        ModuleLifecycleLogger.Phase.START,
                        moduleId,
                        Module.LifecycleState.ACTIVE,
                        elapsedSince(startedAt));
            } catch (Exception failure) {
                states.put(moduleId, Module.LifecycleState.FAILED);
                lifecycleLogger.logFailure(
                        ModuleLifecycleLogger.Phase.START,
                        moduleId,
                        Module.LifecycleState.FAILED,
                        elapsedSince(startedAt),
                        failure);
                state.markFailed("module '" + moduleId + "' failed to start: " + failure);
                shutdownStartedModules();
                throw new ModuleStartupException(moduleId, failure);
            }
        }

        state.markReady();
        lifecycleLogger.logPhaseSummary(
                ModuleLifecycleLogger.Phase.START, startedInOrder.size(), elapsedSince(phaseStartedAt));
    }

    /**
     * Stops every started module in reverse start order.
     *
     * <p>Each module gets {@link #SHUTDOWN_TIMEOUT}; a module that does not return within it is
     * abandoned on a daemon thread and logged as forcibly terminated, so the shutdown as a whole can
     * never block indefinitely (FR-012, SC-007).
     */
    public void shutdown() {
        state.markShuttingDown();
        long phaseStartedAt = System.nanoTime();
        int stopped = shutdownStartedModules();
        lifecycleLogger.logPhaseSummary(
                ModuleLifecycleLogger.Phase.SHUTDOWN, stopped, elapsedSince(phaseStartedAt));
    }

    private int shutdownStartedModules() {
        List<String> reverse = new ArrayList<>(startedInOrder);
        Collections.reverse(reverse);
        int stopped = 0;

        for (String moduleId : reverse) {
            Module module = modules.get(moduleId);
            states.put(moduleId, Module.LifecycleState.STOPPING);
            long startedAt = System.nanoTime();

            // A dedicated daemon executor per module: if the module hangs, we abandon its thread and
            // move on. The JVM will not be held open by it, and the next module is unaffected.
            ExecutorService executor = Executors.newSingleThreadExecutor(daemonThreadFactory(moduleId));
            try {
                Future<?> termination =
                        executor.submit(
                                () -> {
                                    module.stop();
                                    return null;
                                });
                termination.get(SHUTDOWN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
                states.put(moduleId, Module.LifecycleState.STOPPED);
                registry.deregisterServicesOf(moduleId);
                lifecycleLogger.logSuccess(
                        ModuleLifecycleLogger.Phase.SHUTDOWN,
                        moduleId,
                        Module.LifecycleState.STOPPED,
                        elapsedSince(startedAt));
                stopped++;
            } catch (TimeoutException timeout) {
                states.put(moduleId, Module.LifecycleState.STOPPED);
                registry.deregisterServicesOf(moduleId);
                lifecycleLogger.logWarning(
                        ModuleLifecycleLogger.Phase.SHUTDOWN,
                        moduleId,
                        Module.LifecycleState.STOPPED,
                        elapsedSince(startedAt),
                        "forced after exceeding the "
                                + SHUTDOWN_TIMEOUT.toSeconds()
                                + "s shutdown timeout");
                stopped++;
            } catch (ExecutionException executionFailure) {
                states.put(moduleId, Module.LifecycleState.STOPPED);
                registry.deregisterServicesOf(moduleId);
                lifecycleLogger.logFailure(
                        ModuleLifecycleLogger.Phase.SHUTDOWN,
                        moduleId,
                        Module.LifecycleState.STOPPED,
                        elapsedSince(startedAt),
                        executionFailure.getCause() == null
                                ? executionFailure
                                : executionFailure.getCause());
                stopped++;
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                logger.warning("[module] phase=SHUTDOWN interrupted while stopping " + moduleId);
                break;
            } finally {
                // shutdownNow interrupts a hung module; the thread is a daemon, so if it ignores the
                // interrupt it still cannot keep the JVM alive.
                executor.shutdownNow();
            }
        }
        startedInOrder.clear();
        return stopped;
    }

    /** Current lifecycle state per module; primarily for diagnostics and tests. */
    public Map<String, Module.LifecycleState> lifecycleStates() {
        return Map.copyOf(states);
    }

    /** The modules that reached {@link Module.LifecycleState#ACTIVE}, in start order. */
    public List<String> startedModuleIds() {
        return List.copyOf(startedInOrder);
    }

    private static ThreadFactory daemonThreadFactory(String moduleId) {
        return runnable -> {
            Thread thread = new Thread(runnable, "rpg-shutdown-" + moduleId);
            thread.setDaemon(true);
            return thread;
        };
    }

    private static Duration elapsedSince(long nanoTimestamp) {
        return Duration.ofNanos(System.nanoTime() - nanoTimestamp);
    }
}
