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
        if (cancelledBecauseDisabled(handle, "a location-bound task")) {
            return handle;
        }
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
        if (cancelledBecauseDisabled(handle, "an entity-bound task")) {
            return handle;
        }
        Entity entity = resolve(entityRef.entityId());
        if (entity == null) {
            // Not an error, and not rare. Two ordinary situations land here on every server: a creature
            // that is still being added to the world - during CreatureSpawnEvent the entity is not yet
            // resolvable by uuid, and B05 equips mobs from exactly that event - and a player who is
            // already gone. As a warning this produced a line for every hostile mob that spawned.
            //
            // The cancelled handle is the signal, not the log line. A caller holding state that the
            // task was meant to settle has to check it; DefaultStatEngine does, and takes its pending
            // mark back rather than leaving the holder unable to ever recalculate again.
            logger.fine(
                    () ->
                            "[scheduler] no entity "
                                    + entityRef.entityId()
                                    + " to bind a task to - returning a cancelled handle");
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

    /**
     * The delayed sibling of {@link #runSyncOnEntity} (ADR-024).
     *
     * <p>Maps straight onto {@code EntityScheduler#runDelayed}, which Paper offers natively - the
     * abstraction is not emulating anything here, it is exposing what the platform already does.
     *
     * <p>A zero delay is routed to {@link #runSyncOnEntity}: Paper's entity scheduler rejects a delay
     * below one tick, and rounding it up silently would make {@code cast-time: 0} arrive a tick late
     * everywhere.
     */
    @Override
    public TaskHandle runSyncOnEntityDelayed(EntityRef entityRef, Duration delay, Runnable task) {
        Objects.requireNonNull(entityRef, "entityRef");
        Objects.requireNonNull(delay, "delay");
        Objects.requireNonNull(task, "task");
        if (delay.isNegative()) {
            throw new IllegalArgumentException("delay must not be negative, was " + delay);
        }
        if (delay.isZero()) {
            return runSyncOnEntity(entityRef, task);
        }

        PaperTaskHandle handle = new PaperTaskHandle(logger);
        if (cancelledBecauseDisabled(handle, "a delayed entity-bound task")) {
            return handle;
        }
        Entity entity = resolve(entityRef.entityId());
        if (entity == null) {
            logger.fine(
                    () ->
                            "[scheduler] no entity "
                                    + entityRef.entityId()
                                    + " to bind a delayed task to - returning a cancelled handle");
            handle.cancel();
            return handle;
        }
        handle.bind(
                entity.getScheduler()
                        .runDelayed(
                                plugin,
                                scheduled -> runUnlessCancelled(handle, task),
                                () ->
                                        logger.fine(
                                                "[scheduler] entity "
                                                        + entityRef.entityId()
                                                        + " was removed before its delayed task ran"),
                                toTicks(delay)));
        return handle;
    }

    /**
     * Paper counts entity-scheduler delays in ticks, not milliseconds.
     *
     * <p>Rounded to the nearest tick rather than down: a 75 ms global cooldown would otherwise become
     * one tick instead of two, and every cast time would come out systematically short.
     */
    private static long toTicks(Duration delay) {
        return Math.max(1L, Math.round(delay.toMillis() / 50.0));
    }

    /**
     * Runs work off the tick - and keeps running it while the plugin is being disabled.
     *
     * <p><b>The shutdown case is the one that matters.</b> Paper refuses to register a task for a
     * disabled plugin ({@code IllegalPluginAccessException}), and the plugin is already disabled by the
     * time the modules stop. That is exactly when the last writes happen: ending the open sessions and
     * B02's shutdown flush both come through here. Letting the refusal stand meant the final write of
     * every session was skipped, the exception escaped the flush, and the connection pools were never
     * closed either - a full session's progress lost on every stop with a player online.
     *
     * <p>So when there is no scheduler left to take the work, it runs on the calling thread. That still
     * honours what this method promises: module shutdown already runs on its own executor, not on the
     * tick. The alternative - refusing - trades a thread hop for data loss.
     */
    @Override
    public TaskHandle runAsync(Runnable task) {
        Objects.requireNonNull(task, "task");
        PaperTaskHandle handle = new PaperTaskHandle(logger);
        if (!plugin.isEnabled()) {
            logger.fine("[scheduler] plugin disabled - running async work on the calling thread");
            runUnlessCancelled(handle, task);
            return handle;
        }
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
        if (cancelledBecauseDisabled(handle, "a delayed task")) {
            // Unlike runAsync this is not run here: it is not due yet, and there will be no later to
            // run it in. The cancelled handle says so.
            return handle;
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
     * Cancels the handle if there is no plugin left to schedule against.
     *
     * <p>For the paths where running the work here and now would be wrong: a tick-bound task has no
     * tick to run on once the server is stopping, and a delayed one is not due. Paper would throw
     * {@code IllegalPluginAccessException}; a cancelled handle says the same thing without unwinding
     * the caller, and callers holding state check it (see {@link Scheduler#runSyncOnEntity}).
     *
     * @return whether the handle was cancelled, i.e. whether the caller should stop here
     */
    private boolean cancelledBecauseDisabled(PaperTaskHandle handle, String what) {
        if (plugin.isEnabled()) {
            return false;
        }
        logger.fine(() -> "[scheduler] plugin disabled - dropping " + what);
        handle.cancel();
        return true;
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

    /**
     * Finds the entity without breaking the thread rules.
     *
     * <p>{@code Server#getEntity} walks the chunk entity lists and is main-thread only - Paper's
     * {@code AsyncCatcher} flags it. That matters here because this scheduler exists precisely to be
     * called from anywhere: B02 completes its futures on an async pool, and the code that continues
     * there is exactly the code that wants to get back onto an entity's tick.
     *
     * <p>The player lookup goes through the player list instead and is safe from any thread, which
     * covers every holder that is a player. For anything else off the tick there is no safe lookup, so
     * the caller is handed the same cancelled handle as for an entity that is gone, and
     * {@link #runSyncOnEntity} says what that means.
     */
    private Entity resolve(UUID entityId) {
        Entity player = server.getPlayer(entityId);
        if (player != null) {
            return player;
        }
        return server.isPrimaryThread() ? server.getEntity(entityId) : null;
    }
}
