package rpg.core.stats;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Everything that has stats: a player character or a mob (FR-035).
 *
 * <p>There is exactly one difference between the two, and it is {@link #characterId()}: a holder
 * without one is never persisted and never subject to the session readiness rule. Everything else -
 * sources, formula, snapshot, bounds - is identical, which is the point. B10 gets mob stats without
 * a second stat system existing anywhere.
 *
 * <p>Sources live in a {@link TreeMap} keyed by {@link SourceId}, which is {@code Comparable}. That
 * gives a deterministic iteration order for free and is why FR-016 holds without sorting anything
 * on the recalculation path.
 *
 * <p>Mutation is guarded by the holder's own monitor. Contention is effectively nil - a holder is
 * touched by its own player's actions - and the alternative, lock-free bookkeeping over a source
 * map, would buy nothing measurable at the price of a much harder correctness argument.
 */
public final class StatHolder implements StatHolderView {

    private final UUID holderId;
    private final UUID characterId;

    private final TreeMap<SourceId, ModifierSet> sources = new TreeMap<>();

    /** Set by the first change in a tick; cleared when the scheduled recalculation runs (FR-019). */
    private final AtomicBoolean recalcPending = new AtomicBoolean();

    private final AtomicBoolean removed = new AtomicBoolean();

    private volatile StatSnapshot snapshot;
    private volatile ResourcePool resources;
    private long revisionCounter;

    StatHolder(UUID holderId, UUID characterId, ResourcePool initialResources) {
        this.holderId = holderId;
        this.characterId = characterId;
        this.resources = initialResources;
    }

    @Override
    public UUID holderId() {
        return holderId;
    }

    @Override
    public Optional<UUID> characterId() {
        return Optional.ofNullable(characterId);
    }

    @Override
    public Optional<StatSnapshot> previousSnapshot() {
        return Optional.ofNullable(snapshot);
    }

    /** Whether this holder belongs to a player character, and is therefore persisted. */
    public boolean isCharacter() {
        return characterId != null;
    }

    /** The last computed result, or {@code null} before the first calculation. */
    public StatSnapshot snapshot() {
        return snapshot;
    }

    /** The current health and mana. */
    public ResourcePool resources() {
        return resources;
    }

    void setResources(ResourcePool pool) {
        this.resources = pool;
    }

    /**
     * Adds or replaces one source (FR-008).
     *
     * @return {@code true} if anything actually changed - a caller that re-applies an identical set
     *     causes no recalculation
     */
    synchronized boolean put(ModifierSet set) {
        ModifierSet previous = sources.put(set.source(), set);
        return !set.equals(previous);
    }

    /**
     * Removes one source (FR-007).
     *
     * @return {@code true} if the source was present - removing an unknown source is a no-op and
     *     triggers no recalculation (FR-018)
     */
    synchronized boolean remove(SourceId source) {
        return sources.remove(source) != null;
    }

    /** Removes every source of one kind. */
    synchronized boolean removeKind(SourceKind kind) {
        return sources.keySet().removeIf(id -> id.kind() == kind);
    }

    /** A stable copy of the current sources, in summation order. */
    synchronized Collection<ModifierSet> sourcesSnapshot() {
        return List.copyOf(sources.values());
    }

    /** All sources, for contribution queries (FR-010). Same order, same copy semantics. */
    synchronized Collection<ModifierSet> sourcesForQuery() {
        return List.copyOf(sources.values());
    }

    synchronized void clearSources() {
        sources.clear();
    }

    synchronized long nextRevision() {
        return ++revisionCounter;
    }

    void setSnapshot(StatSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    /**
     * Marks this holder for recalculation.
     *
     * @return {@code true} if this call is the one that set the mark, and therefore the one that
     *     has to schedule the task. Every further change in the same tick gets {@code false} and
     *     schedules nothing - that is the whole of the bundling (FR-019).
     */
    boolean markPending() {
        return recalcPending.compareAndSet(false, true);
    }

    void clearPending() {
        recalcPending.set(false);
    }

    /** Whether a recalculation is still outstanding (FR-019b). */
    public boolean isRecalcPending() {
        return recalcPending.get();
    }

    /** Marks this holder gone; a scheduled task that finds this does nothing (FR-036). */
    boolean markRemoved() {
        return removed.compareAndSet(false, true);
    }

    public boolean isRemoved() {
        return removed.get();
    }

}
