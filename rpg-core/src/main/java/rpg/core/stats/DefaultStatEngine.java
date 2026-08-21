package rpg.core.stats;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import rpg.core.event.EventBus;
import rpg.core.scheduler.EntityRef;
import rpg.core.scheduler.Scheduler;
import rpg.core.session.SessionNotReadyException;
import rpg.core.session.SessionRegistry;

/**
 * The stat engine (FR-018 to FR-024, FR-035 to FR-040).
 *
 * <h2>How "exactly one recalculation per tick" works</h2>
 *
 * <p>A change sets a flag on the affected holder. Whoever sets it - and only they, via a
 * compare-and-set - schedules one task bound to that holder's entity. Every further change before
 * the task runs finds the flag already set and schedules nothing. Six equipment slots therefore
 * produce one recalculation, and so does a whole equipment set arriving at login.
 *
 * <p>The obvious alternative, a server-wide sweep at the end of each tick, was rejected: it needs a
 * global repeating task, which Principle I forbids outright and ADR-007 wants avoided to keep the
 * Folia path open, and it runs in every tick whether or not anything changed, which Principle II
 * forbids. With per-holder tasks, a tick in which nothing changed costs nothing, because no task
 * exists to run.
 *
 * <p>The cost is that a result lands at the start of the next tick rather than instantly. FR-021
 * already grants that - in-flight actions use the values from when they started - and
 * {@link #recalculateNow} covers the one case where it is not acceptable: releasing a player after
 * login (FR-019b).
 */
public final class DefaultStatEngine implements StatEngine {

    private final Map<UUID, StatHolder> holders = new ConcurrentHashMap<>();
    private final List<BaseStatContributor> contributors = new CopyOnWriteArrayList<>();

    private final Scheduler scheduler;
    private final EventBus eventBus;
    private final Logger logger;

    /**
     * Optional: without it, no holder is subject to the session readiness rule. Left absent in unit
     * tests, present on a real server (FR-037).
     */
    private final SessionRegistry sessions;

    private volatile StatConfig config;
    private volatile VanillaAttributeBridge bridge;

    /** Called with the character id whenever its persisted resources changed (FR-028). */
    private volatile Consumer<UUID> resourceWriteMark = id -> {};

    public DefaultStatEngine(
            StatConfig config,
            Scheduler scheduler,
            EventBus eventBus,
            SessionRegistry sessions,
            Logger logger) {
        this.config = java.util.Objects.requireNonNull(config, "config");
        this.scheduler = java.util.Objects.requireNonNull(scheduler, "scheduler");
        this.eventBus = java.util.Objects.requireNonNull(eventBus, "eventBus");
        this.sessions = sessions;
        this.logger = java.util.Objects.requireNonNull(logger, "logger");
    }

    // ---------------------------------------------------------------- reading

    @Override
    public StatSnapshot snapshot(UUID holderId) {
        StatHolder holder = require(holderId);
        requireSessionReady(holder);
        StatSnapshot snapshot = holder.snapshot();
        if (snapshot == null) {
            // Only reachable for a holder created but never calculated. The load path calls
            // recalculateNow before release, so a player cannot get here (FR-019b).
            return recalculate(holder);
        }
        return snapshot;
    }

    @Override
    public Optional<StatSnapshot> findSnapshot(UUID holderId) {
        StatHolder holder = holders.get(holderId);
        return holder == null ? Optional.empty() : Optional.ofNullable(holder.snapshot());
    }

    @Override
    public double value(UUID holderId, Attribute attribute) {
        return snapshot(holderId).get(attribute);
    }

    @Override
    public List<AttributeContribution> contributions(UUID holderId, Attribute attribute) {
        StatHolder holder = require(holderId);
        requireSessionReady(holder);
        List<AttributeContribution> result = new ArrayList<>();
        for (ModifierSet set : holder.sourcesForQuery()) {
            for (StatModifier modifier : set.modifiers()) {
                if (modifier.attribute() == attribute) {
                    result.add(
                            new AttributeContribution(
                                    set.source(), modifier.operation(), modifier.value()));
                }
            }
        }
        return List.copyOf(result);
    }

    // ----------------------------------------------------------- contributing

    @Override
    public void apply(UUID holderId, ModifierSet set) {
        java.util.Objects.requireNonNull(set, "set");
        StatHolder holder = require(holderId);
        requireSessionReady(holder);
        if (holder.put(set)) {
            markForRecalculation(holder);
        }
    }

    @Override
    public void applyAll(UUID holderId, Collection<ModifierSet> sets) {
        java.util.Objects.requireNonNull(sets, "sets");
        StatHolder holder = require(holderId);
        requireSessionReady(holder);
        boolean changed = false;
        for (ModifierSet set : sets) {
            changed |= holder.put(set);
        }
        if (changed) {
            markForRecalculation(holder);
        }
    }

    @Override
    public void remove(UUID holderId, SourceId source) {
        java.util.Objects.requireNonNull(source, "source");
        StatHolder holder = holders.get(holderId);
        if (holder == null) {
            return;
        }
        requireSessionReady(holder);
        if (holder.remove(source)) {
            markForRecalculation(holder);
        }
    }

    @Override
    public void removeKind(UUID holderId, SourceKind kind) {
        java.util.Objects.requireNonNull(kind, "kind");
        StatHolder holder = holders.get(holderId);
        if (holder == null) {
            return;
        }
        requireSessionReady(holder);
        if (holder.removeKind(kind)) {
            markForRecalculation(holder);
        }
    }

    // ----------------------------------------------------------------- holders

    @Override
    public UUID createForCharacter(UUID playerId, UUID characterId, ResourcePool initial) {
        java.util.Objects.requireNonNull(playerId, "playerId");
        java.util.Objects.requireNonNull(characterId, "characterId");
        java.util.Objects.requireNonNull(initial, "initial");
        holders.put(playerId, new StatHolder(playerId, characterId, initial));
        return playerId;
    }

    @Override
    public UUID createForEntity(UUID entityId) {
        java.util.Objects.requireNonNull(entityId, "entityId");
        // A mob has no persisted resources; it starts full and is never written anywhere.
        StatHolder holder = new StatHolder(entityId, null, new ResourcePool(0.0, 0.0));
        holders.put(entityId, holder);
        StatSnapshot first = recalculate(holder);
        holder.setResources(
                ResourcePool.full(first.get(Attribute.HEALTH), first.get(Attribute.MANA)));
        return entityId;
    }

    @Override
    public Optional<UUID> characterIdOf(UUID holderId) {
        StatHolder holder = holders.get(holderId);
        return holder == null ? Optional.empty() : holder.characterId();
    }

    @Override
    public void remove(UUID holderId) {
        StatHolder holder = holders.remove(holderId);
        if (holder == null) {
            return;
        }
        holder.markRemoved();
        holder.clearSources();
        holder.setSnapshot(null);
        holder.clearPending();
    }

    @Override
    public StatSnapshot recalculateNow(UUID holderId) {
        return recalculate(require(holderId));
    }

    @Override
    public int holderCount() {
        return holders.size();
    }

    // --------------------------------------------------------------- resources

    @Override
    public ResourceView resources(UUID holderId) {
        StatHolder holder = require(holderId);
        requireSessionReady(holder);
        return view(holder, currentSnapshot(holder));
    }

    @Override
    public double changeHealth(UUID holderId, double delta) {
        return changeResource(holderId, ResourceKind.HEALTH, delta);
    }

    @Override
    public double changeMana(UUID holderId, double delta) {
        return changeResource(holderId, ResourceKind.MANA, delta);
    }

    private double changeResource(UUID holderId, ResourceKind kind, double delta) {
        if (!Double.isFinite(delta)) {
            throw new IllegalArgumentException("delta must be a finite number, but was " + delta);
        }
        StatHolder holder = require(holderId);
        requireSessionReady(holder);
        StatSnapshot snapshot = currentSnapshot(holder);

        ResourcePool before = holder.resources();
        double max =
                kind == ResourceKind.HEALTH
                        ? snapshot.get(Attribute.HEALTH)
                        : snapshot.get(Attribute.MANA);
        double previous =
                kind == ResourceKind.HEALTH ? before.currentHealth() : before.currentMana();

        ResourcePool after =
                kind == ResourceKind.HEALTH
                        ? before.withHealth(previous + delta, max)
                        : before.withMana(previous + delta, max);
        double current =
                kind == ResourceKind.HEALTH ? after.currentHealth() : after.currentMana();

        if (current == previous) {
            // Spending mana you do not have is not an event.
            return current;
        }
        holder.setResources(after);
        publishResourceChange(holder, kind, previous, current, max, ChangeCause.DELTA);
        if (kind == ResourceKind.HEALTH) {
            mirrorHealth(holder, after, snapshot);
        }
        return current;
    }

    /** Sets both resources outright - used by the load path (FR-027, FR-028). */
    @Override
    public void restoreResources(UUID holderId, ResourcePool pool) {
        StatHolder holder = require(holderId);
        StatSnapshot snapshot = currentSnapshot(holder);
        ResourcePool clamped =
                pool.clampedTo(snapshot.get(Attribute.HEALTH), snapshot.get(Attribute.MANA));
        holder.setResources(clamped);
        publishResourceChange(
                holder,
                ResourceKind.HEALTH,
                Double.NaN,
                clamped.currentHealth(),
                snapshot.get(Attribute.HEALTH),
                ChangeCause.INITIALISED);
        publishResourceChange(
                holder,
                ResourceKind.MANA,
                Double.NaN,
                clamped.currentMana(),
                snapshot.get(Attribute.MANA),
                ChangeCause.INITIALISED);
        mirrorHealth(holder, clamped, snapshot);
    }

    /** The raw pool of a holder, for the write path. */
    public Optional<ResourcePool> resourcePool(UUID holderId) {
        StatHolder holder = holders.get(holderId);
        return holder == null ? Optional.empty() : Optional.of(holder.resources());
    }

    /** Installs the hook that marks a character's resources for writing (FR-028). */
    public void setResourceWriteMark(Consumer<UUID> mark) {
        this.resourceWriteMark = java.util.Objects.requireNonNull(mark, "mark");
    }

    // ------------------------------------------------------------- registration

    @Override
    public void registerBaseStatContributor(BaseStatContributor contributor) {
        contributors.add(java.util.Objects.requireNonNull(contributor, "contributor"));
    }

    @Override
    public boolean unregisterBaseStatContributor(String id) {
        java.util.Objects.requireNonNull(id, "id");
        return contributors.removeIf(contributor -> contributor.id().equals(id));
    }

    /**
     * The ids of everything supplying base values right now.
     *
     * <p>Exists so a bootstrap test can assert which suppliers a fully wired server ends up with -
     * {@code DefaultSessionLifecycle.attachmentIds()} is here for the same reason. The case that needs
     * it: B07 replaces B06's level growth rather than adding to it, and if the removal ever stopped
     * working, every character would simply be too strong. Nothing would throw and no unit test in
     * either block would notice.
     */
    public List<String> baseContributorIds() {
        return contributors.stream().map(BaseStatContributor::id).toList();
    }

    @Override
    public void registerVanillaBridge(VanillaAttributeBridge bridge) {
        this.bridge = bridge;
    }

    /**
     * Replaces the configuration and marks every holder (FR-003, Principle V).
     *
     * <p>This is the one place that walks all holders. It runs on an operator's explicit reload,
     * never during gameplay - which is what keeps it clear of FR-018.
     */
    public void reload(StatConfig newConfig) {
        this.config = java.util.Objects.requireNonNull(newConfig, "newConfig");
        for (StatHolder holder : holders.values()) {
            markForRecalculation(holder);
        }
    }

    // -------------------------------------------------------------- internals

    private StatHolder require(UUID holderId) {
        StatHolder holder = holders.get(holderId);
        if (holder == null) {
            throw new NoSuchElementException("no stat holder for " + holderId);
        }
        return holder;
    }

    /**
     * Enforces B03's readiness rule for player holders (FR-037).
     *
     * <p>Only for holders with a character. A mob has no session and must not fail on one - the
     * check would otherwise make B10 impossible for a reason that has nothing to do with B10.
     */
    private void requireSessionReady(StatHolder holder) {
        if (sessions == null || !holder.isCharacter()) {
            return;
        }
        if (!sessions.isReady(holder.holderId())) {
            throw new SessionNotReadyException(
                    holder.holderId(),
                    sessions.find(holder.holderId()).map(s -> s.state()).orElse(null));
        }
    }

    private StatSnapshot currentSnapshot(StatHolder holder) {
        StatSnapshot snapshot = holder.snapshot();
        return snapshot != null ? snapshot : recalculate(holder);
    }

    /**
     * Marks a holder and, if this is the first mark, schedules the one task that will do the work.
     *
     * <p>A schedule that could not be placed takes the mark back with it. Without that the mark stays
     * set forever, every later {@code markPending} loses its compare-and-set, and the holder never
     * recalculates again - its modifiers sit in the sources and never reach a snapshot. Nothing throws
     * and nothing is logged at the point where it matters, which is what made this worth a comment: the
     * scheduler refuses whenever the entity is not resolvable, and that is an ordinary situation - a mob
     * still being added to the world, a player already gone.
     */
    private void markForRecalculation(StatHolder holder) {
        if (holder.isRemoved() || !holder.markPending()) {
            return;
        }
        rpg.core.scheduler.TaskHandle handle =
                scheduler.runSyncOnEntity(
                        new EntityRef(holder.holderId()),
                        () -> {
                            if (holder.isRemoved()) {
                                // The holder went away between the mark and this task. Nothing to do,
                                // and nothing to clean up - the mark went with it.
                                return;
                            }
                            recalculate(holder);
                        });
        if (handle.isCancelled()) {
            // Nothing will run, so the holder is left dirty rather than pending - the next mark gets to
            // try again. Recalculating here instead would run on whatever thread this is, which is the
            // one thing the entity-bound scheduler exists to prevent.
            holder.clearPending();
        }
    }

    private StatSnapshot recalculate(StatHolder holder) {
        holder.clearPending();

        StatSnapshot previous = holder.snapshot();
        double[] baseBonus = collectBaseContributions(holder);
        StatSnapshot current =
                StatCalculator.compute(
                        config, holder.sourcesSnapshot(), baseBonus, holder.nextRevision());
        holder.setSnapshot(current);

        ResourcePool before = holder.resources();
        ResourcePool clamped =
                before.clampedTo(current.get(Attribute.HEALTH), current.get(Attribute.MANA));
        if (clamped != before) {
            holder.setResources(clamped);
            if (clamped.currentHealth() != before.currentHealth()) {
                publishResourceChange(
                        holder,
                        ResourceKind.HEALTH,
                        before.currentHealth(),
                        clamped.currentHealth(),
                        current.get(Attribute.HEALTH),
                        ChangeCause.CLAMPED_BY_MAX);
            }
            if (clamped.currentMana() != before.currentMana()) {
                publishResourceChange(
                        holder,
                        ResourceKind.MANA,
                        before.currentMana(),
                        clamped.currentMana(),
                        current.get(Attribute.MANA),
                        ChangeCause.CLAMPED_BY_MAX);
            }
        }

        // Mirror first, then publish: a subscriber must never see a value whose display has not
        // caught up yet (contracts/events.md).
        mirror(holder, clamped, current, previous);
        eventBus.publish(
                new StatsRecalculatedEvent(
                        holder.holderId(), holder.characterId().orElse(null), previous, current));
        return current;
    }

    /**
     * Asks every contributor, with a fault barrier around each (FR-038).
     *
     * <p>Same shape as B01's module fault barrier, for the same reason: one misbehaving supplier
     * must cost one holder's contribution, not the whole calculation and not other holders.
     */
    private double[] collectBaseContributions(StatHolder holder) {
        if (contributors.isEmpty()) {
            return null;
        }
        double[] bonus = new double[Attribute.count()];
        BaseStatSink sink =
                (attribute, amount) -> {
                    java.util.Objects.requireNonNull(attribute, "attribute");
                    if (!Double.isFinite(amount)) {
                        throw new IllegalArgumentException(
                                "base contribution for "
                                        + attribute.key()
                                        + " must be finite, but was "
                                        + amount);
                    }
                    bonus[attribute.ordinal()] += amount;
                };
        for (BaseStatContributor contributor : contributors) {
            try {
                contributor.contribute(holder, sink);
            } catch (RuntimeException e) {
                logger.log(
                        Level.WARNING,
                        "[stats] base stat contributor '"
                                + contributor.id()
                                + "' failed for holder "
                                + holder.holderId()
                                + "; continuing without its contribution",
                        e);
            }
        }
        return bonus;
    }

    private void mirror(
            StatHolder holder, ResourcePool pool, StatSnapshot current, StatSnapshot previous) {
        VanillaAttributeBridge target = bridge;
        if (target == null) {
            return;
        }
        // Health only once it is a real value. Between creating a holder and restoring its resources
        // the pool is the zero placeholder, and mirroring that is setHealth(0) - it kills the player.
        // Attack and movement speed are safe either way: they come from the snapshot, not the pool.
        if (holder.resourcesKnown()) {
            target.mirrorHealth(
                    holder.holderId(), pool.currentHealth(), current.get(Attribute.HEALTH));
        }
        if (previous == null
                || previous.get(Attribute.ATTACK_SPEED) != current.get(Attribute.ATTACK_SPEED)) {
            target.mirrorAttackSpeed(holder.holderId(), current.get(Attribute.ATTACK_SPEED));
        }
        if (previous == null
                || previous.get(Attribute.MOVEMENT_SPEED) != current.get(Attribute.MOVEMENT_SPEED)) {
            target.mirrorMovementSpeed(holder.holderId(), current.get(Attribute.MOVEMENT_SPEED));
        }
    }

    private void mirrorHealth(StatHolder holder, ResourcePool pool, StatSnapshot snapshot) {
        VanillaAttributeBridge target = bridge;
        if (target != null) {
            target.mirrorHealth(
                    holder.holderId(), pool.currentHealth(), snapshot.get(Attribute.HEALTH));
        }
    }

    private void publishResourceChange(
            StatHolder holder,
            ResourceKind kind,
            double previous,
            double current,
            double max,
            ChangeCause cause) {
        holder.characterId().ifPresent(resourceWriteMark);
        eventBus.publish(
                new ResourceChangedEvent(
                        holder.holderId(),
                        holder.characterId().orElse(null),
                        kind,
                        previous,
                        current,
                        max,
                        cause));
    }

    private ResourceView view(StatHolder holder, StatSnapshot snapshot) {
        ResourcePool pool = holder.resources();
        return new ResourceView(
                pool.currentHealth(),
                snapshot.get(Attribute.HEALTH),
                pool.currentMana(),
                snapshot.get(Attribute.MANA));
    }
}
