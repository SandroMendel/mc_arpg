package rpg.plugin.performance;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import java.util.logging.Logger;

import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import rpg.core.config.ConfigHandle;
import rpg.core.config.ConfigValidationException;
import rpg.core.module.Module;
import rpg.core.module.ModuleContext;
import rpg.core.performance.AlertPolicy;
import rpg.core.performance.DefaultPerformanceRegistry;
import rpg.core.performance.PerformanceAlertTracker;
import rpg.core.performance.PerformanceRegistry;
import rpg.core.performance.PerformanceReport;
import rpg.core.performance.PerformanceSnapshot;
import rpg.platform.performance.PaperPerformanceListener;
import rpg.platform.performance.PaperPerformanceSource;

/** B15's lifecycle owner for the core registry and Paper tick adapter. */
public final class PerformanceModule implements Module {

    public static final String ID = "performance";

    private final JavaPlugin plugin;
    private final Logger logger;
    private final LongSupplier activePlayersSource;
    private final LongSupplier activeCustomMobsSource;

    private ConfigHandle<PerformanceConfig> configHandle;
    private DefaultPerformanceRegistry registry;
    private PaperPerformanceSource source;
    private PaperPerformanceListener listener;
    private PerformanceScopeWiring scopeWiring;
    private PerformanceAlertTracker alertTracker;
    private PerformanceReportCycle reportCycle;
    private PerformanceReportFileSink reportFileSink;
    private final AtomicLong activePlayers = new AtomicLong();
    private final AtomicLong activeCustomMobs = new AtomicLong();
    private final AtomicLong successfulReports = new AtomicLong();
    private final AtomicLong failedReports = new AtomicLong();
    private final AtomicLong exportErrors = new AtomicLong();
    private boolean listenerRegistered;

    public PerformanceModule(JavaPlugin plugin, Logger logger) {
        this(plugin, logger, () -> 0L, () -> 0L);
    }

    public PerformanceModule(
            JavaPlugin plugin,
            Logger logger,
            LongSupplier activePlayersSource,
            LongSupplier activeCustomMobsSource) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.activePlayersSource = Objects.requireNonNull(activePlayersSource, "activePlayersSource");
        this.activeCustomMobsSource =
                Objects.requireNonNull(activeCustomMobsSource, "activeCustomMobsSource");
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<String> dependencies() {
        return List.of();
    }

    @Override
    public void start(ModuleContext context) {
        configHandle = loadConfig(context);
        PerformanceConfig config = configHandle.get();
        registry =
                new DefaultPerformanceRegistry(
                        System::nanoTime, config.windowSamples(), config.measurementEnabled());
        source = new PaperPerformanceSource(registry);
        listener = new PaperPerformanceListener(source, this::capturePopulation);
        scopeWiring = new PerformanceScopeWiring(registry, config.budgets());
        scopeWiring.registerExpected();
        alertTracker =
                new PerformanceAlertTracker(
                        new AlertPolicy(config.warningRatio(), config.criticalAfter()));
        Path exportTarget = plugin.getDataFolder().toPath().resolve(config.exportPath());
        PerformanceLogReporter logReporter = new PerformanceLogReporter();
        reportFileSink =
                new PerformanceReportFileSink(
                        plugin.getDataFolder().toPath().resolve("performance/performance-report.log"),
                        logReporter,
                        logger::warning);
        reportCycle =
                new PerformanceReportCycle(
                        context.scheduler(),
                        config.reportInterval(),
                        this::createReport,
                        new PrometheusTextExporter(exportTarget),
                        reportFileSink::write,
                        logReporter,
                        logger::info,
                        config.exportEnabled(),
                        this::recordExportResult);
        context.registry().registerService(ID, PerformanceRegistry.class, registry);
        logger.info(
                "[performance] phase=START state=READY - tick measurement enabled with "
                        + config.windowSamples()
                        + " samples per source");
    }

    /** Registers the Paper adapter after the module bootstrap has reached READY. */
    public void registerListener() {
        if (listener == null) {
            throw new IllegalStateException("performance module has not started");
        }
        if (listenerRegistered) {
            return;
        }
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        listenerRegistered = true;
    }

    /** Removes the adapter before the module registry is shut down. */
    public void unregisterListener() {
        if (listenerRegistered && listener != null) {
            HandlerList.unregisterAll(listener);
        }
        listenerRegistered = false;
    }

    /** Starts the single server-wide off-tick report cycle after bootstrap is ready. */
    public void startReporting() {
        if (reportCycle == null) {
            throw new IllegalStateException("performance module has not started");
        }
        reportCycle.start();
    }

    public void stopReporting() {
        if (reportCycle != null) {
            reportCycle.stop();
        }
    }

    @Override
    public void stop() {
        stopReporting();
        unregisterListener();
        reportCycle = null;
        reportFileSink = null;
        scopeWiring = null;
        alertTracker = null;
        listener = null;
        source = null;
        registry = null;
        configHandle = null;
    }

    public DefaultPerformanceRegistry registry() {
        return registry;
    }

    public PerformanceConfig config() {
        if (configHandle == null) {
            throw new IllegalStateException("performance module has not started");
        }
        return configHandle.get();
    }

    public void setActivePlayers(long count) {
        activePlayers.set(nonNegativeCount(count, "activePlayers"));
    }

    public void setActiveCustomMobs(long count) {
        activeCustomMobs.set(nonNegativeCount(count, "activeCustomMobs"));
    }

    public PerformanceScopeWiring scopeWiring() {
        if (scopeWiring == null) {
            throw new IllegalStateException("performance module has not started");
        }
        return scopeWiring;
    }

    private PerformanceReport createReport() {
        PerformanceSnapshot snapshot = registry.snapshot();
        PerformanceConfig config = config();
        PerformanceAlertTracker.Result alerts = alertTracker.evaluate(snapshot);
        return PerformanceReport.fromSnapshot(
                Instant.now(),
                snapshot,
                config.targetTps(),
                config.targetMsptP95(),
                config.targetMsptP99(),
                activePlayers.get(),
                activeCustomMobs.get(),
                alerts.alertStates(),
                alerts.transitions(),
                successfulReports.get(),
                failedReports.get(),
                exportErrors.get(),
                alerts.missingSubsystems(),
                alerts.violationSinceNanos());
    }

    private static long nonNegativeCount(long count, String name) {
        if (count < 0L) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
        return count;
    }

    private void capturePopulation() {
        setActivePlayers(activePlayersSource.getAsLong());
        setActiveCustomMobs(activeCustomMobsSource.getAsLong());
    }

    private void recordExportResult(PrometheusTextExporter.ExportResult result) {
        if (result.success()) {
            successfulReports.incrementAndGet();
        } else {
            failedReports.incrementAndGet();
            exportErrors.incrementAndGet();
        }
    }

    private ConfigHandle<PerformanceConfig> loadConfig(ModuleContext context) {
        try {
            return context.configLoader()
                    .register(Path.of("performance.yml"), PerformanceConfigSchema.schema());
        } catch (ConfigValidationException invalid) {
            throw new IllegalStateException(
                    "performance configuration is invalid: " + invalid.getMessage(), invalid);
        }
    }
}
