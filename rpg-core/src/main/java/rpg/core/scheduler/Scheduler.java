package rpg.core.scheduler;

/**
 * The only way work is scheduled in this project. Replaces every direct use of the global
 * Bukkit scheduler (ADR-007, Constitution I.5).
 *
 * <p><strong>There is deliberately no method taking a synchronous task without a
 * {@link WorldPosition} or {@link EntityRef} binding.</strong> That is a hard type-system boundary
 * enforcing FR-008, not a runtime check and not a naming convention: unbound tick work simply cannot
 * be expressed through this interface, which is what keeps the Folia migration path open.
 *
 * <p>Sync and async are likewise distinguished in the type system (FR-007) - by separate methods
 * with different parameter types, never by a boolean flag.
 *
 * <p><em>Recurring</em> tasks are intentionally absent: per Constitution II.2 time-based values are
 * evaluated lazily from timestamps instead of being driven by periodic scheduling. A <em>delayed</em>
 * one-shot ({@link #runAsyncDelayed}) is a different thing and is offered - see its documentation
 * for why that distinction matters.
 *
 * <p>{@code rpg-core} knows no Paper types; the platform adapter maps {@link WorldPosition} and
 * {@link EntityRef} onto the Paper region/entity schedulers.
 */
public interface Scheduler {

    /**
     * Runs {@code task} on the server tick that owns {@code position}.
     *
     * <p>Safe to touch the Paper/Bukkit API from inside {@code task}.
     */
    TaskHandle runSyncAtLocation(WorldPosition position, Runnable task);

    /**
     * Runs {@code task} on the server tick that currently owns {@code entity}.
     *
     * <p>If the entity no longer exists when the task is due, the task is dropped.
     *
     * <p><b>It may also never be placed at all.</b> If the entity cannot be resolved <em>now</em>, an
     * already-cancelled handle comes back and {@code task} will not run. That is an ordinary outcome,
     * not a fault: a creature is not resolvable by uuid while it is still being added to the world, and
     * a player who has just left is not resolvable either.
     *
     * <p>So a caller that set state the task was meant to settle - a dirty mark, a pending flag - has
     * to check {@link TaskHandle#isCancelled()} and undo it. Leaving it set means the state is never
     * settled and, worse, never can be: the next attempt sees the flag already set and schedules
     * nothing.
     */
    TaskHandle runSyncOnEntity(EntityRef entity, Runnable task);

    /**
     * Runs {@code task} off the server tick.
     *
     * <p>Async work is not location-bound on purpose: it must never touch the Paper/Bukkit API
     * (Constitution I.1). Results are handed back into the tick through one of the sync methods
     * above, never through shared mutable state.
     */
    TaskHandle runAsync(Runnable task);

    /**
     * Runs {@code task} off the server tick once, after {@code delay} has elapsed.
     *
     * <p>Added for B02 (ADR-010): the persistence autosave needs a time-driven trigger, and the two
     * alternatives were worse. Evaluating purely lazily loses data - if the last change stays the
     * last, nothing ever re-checks and it sits unwritten indefinitely. Giving B02 its own thread
     * pool would create a second, hidden source of concurrency next to this abstraction, which is
     * exactly what Constitution I exists to prevent.
     *
     * <p><strong>This is a one-shot, not a repeating task.</strong> A caller that wants a cycle
     * reschedules itself at the end of each run. That is a deliberate difference, not a
     * technicality: it keeps every interval decision visible at the call site, and it means a
     * failing cycle stops instead of silently piling up. Constitution II.2 forbids recurring tasks
     * <em>per player or per entity</em>; a single server-wide system task is not that.
     *
     * @param delay how long to wait before running; must not be negative
     */
    TaskHandle runAsyncDelayed(java.time.Duration delay, Runnable task);
}
