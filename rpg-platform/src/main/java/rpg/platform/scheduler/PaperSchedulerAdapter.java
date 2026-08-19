package rpg.platform.scheduler;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import rpg.core.scheduler.EntityRef;
import rpg.core.scheduler.Scheduler;
import rpg.core.scheduler.TaskHandle;
import rpg.core.scheduler.WorldPosition;

/**
 * The one and only place in this project that talks to a Paper scheduler (ADR-007, Constitution I.5,
 * SC-005).
 *
 * <p>Synchronous work goes to the region scheduler (bound to a position) or to the entity scheduler
 * (bound to an entity), never to {@code Bukkit.getScheduler()} or
 * {@code GlobalRegionScheduler}. That is what keeps the Folia migration path open: every tick task in
 * the codebase already declares which region owns it.
 *
 * <p>Async work goes to Paper's async scheduler and must never touch the Bukkit API
 * (Constitution I.1); results are handed back into the tick through one of the sync methods.
 */
public final class PaperSchedulerAdapter implements Scheduler {

    private final Plugin plugin;
    private final Server server;
    private final Logger logger;

    public PaperSchedulerAdapter(Plugin plugin, Server server, Logger logger) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.server = Objects.requireNonNull(server, "server");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public TaskHandle runSyncAtLocation(WorldPosition position, Runnable task) {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(task, "task");

        PaperTaskHandle handle = new PaperTaskHandle(logger);
        Location location = toLocation(position);
        if (location == null) {
            logger.warning(
                    "[scheduler] dropped a location-bound task: world "
                            + position.worldId()
                            + " is not loaded");
            handle.cancel();
            return handle;
        }
        handle.bind(
                server.getRegionScheduler()
                        .run(plugin, location, scheduled -> runUnlessCancelled(handle, task)));
        return handle;
    }

    @Override
    public TaskHandle runSyncOnEntity(EntityRef entityRef, Runnable task) {
        Objects.requireNonNull(entityRef, "entityRef");
        Objects.requireNonNull(task, "task");

        PaperTaskHandle handle = new PaperTaskHandle(logger);
        Entity entity = resolve(entityRef.entityId());
        if (entity == null) {
            logger.warning(
                    "[scheduler] dropped an entity-bound task: entity "
                            + entityRef.entityId()
                            + " no longer exists");
            handle.cancel();
            return handle;
        }
        handle.bind(
                entity.getScheduler()
                        .run(
                                plugin,
                                scheduled -> runUnlessCancelled(handle, task),
                                // retired: the entity disappeared before the task was due
                                () ->
                                        logger.fine(
                                                "[scheduler] entity "
                                                        + entityRef.entityId()
                                                        + " was removed before its task ran")));
        return handle;
    }

    @Override
    public TaskHandle runAsync(Runnable task) {
        Objects.requireNonNull(task, "task");
        PaperTaskHandle handle = new PaperTaskHandle(logger);
        handle.bind(
                server.getAsyncScheduler()
                        .runNow(plugin, scheduled -> runUnlessCancelled(handle, task)));
        return handle;
    }

    @Override
    public TaskHandle runAsyncDelayed(Duration delay, Runnable task) {
        Objects.requireNonNull(delay, "delay");
        Objects.requireNonNull(task, "task");
        if (delay.isNegative()) {
            throw new IllegalArgumentException("delay must not be negative, was " + delay);
        }

        PaperTaskHandle handle = new PaperTaskHandle(logger);
        // Paper rejects a zero delay on runDelayed, so route that through runNow instead of
        // silently rounding it up to one tick.
        if (delay.isZero()) {
            return runAsync(task);
        }
        handle.bind(
                server.getAsyncScheduler()
                        .runDelayed(
                                plugin,
                                scheduled -> runUnlessCancelled(handle, task),
                                delay.toMillis(),
                                TimeUnit.MILLISECONDS));
        return handle;
    }

    /**
     * Runs the body unless the handle was cancelled in the window between submission and execution,
     * and keeps a failing task from escaping into the scheduler thread.
     */
    private void runUnlessCancelled(PaperTaskHandle handle, Runnable task) {
        if (handle.isCancelled()) {
            return;
        }
        try {
            task.run();
        } catch (RuntimeException failure) {
            logger.log(Level.SEVERE, "[scheduler] a scheduled task failed and was contained", failure);
        }
    }

    private Location toLocation(WorldPosition position) {
        World world = server.getWorld(position.worldId());
        if (world == null) {
            return null;
        }
        return new Location(world, position.x(), position.y(), position.z());
    }

    private Entity resolve(UUID entityId) {
        return server.getEntity(entityId);
    }
}
