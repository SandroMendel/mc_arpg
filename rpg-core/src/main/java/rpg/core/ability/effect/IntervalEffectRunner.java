package rpg.core.ability.effect;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import rpg.core.ability.Ability;
import rpg.core.ability.EffectSpec;
import rpg.core.ability.TargetResolver;
import rpg.core.scheduler.WorldPosition;
import rpg.core.stats.StatSnapshot;

/**
 * Every effect that applies repeatedly, driven by <b>one</b> sweep (FR-010b).
 *
 * <p><b>This is the whole reason damage over time was refused once and accepted the second time.</b>
 * The mechanic was never the problem; the shape was. One evaluation per affected target is a
 * recurring task per entity, and at 800 mobs that is 800 of them - exactly what Constitution II
 * forbids. One server-wide sweep over the open instances costs the same whether one poison is
 * running or two hundred, and with none running it is an empty list scan.
 *
 * <p>Nothing here is scheduled by this class either: the plugin drives it at the interval it chooses,
 * the same way B05's damage-window sweep is driven from outside.
 *
 * <p><b>Stacking is capped twice on purpose</b> (FR-010c): by count, so a third poison does not add a
 * fourth, and by total effect per interval, so three stacks of a strong poison cannot outdo what the
 * ability was balanced for. Applying a stack at the maximum refreshes the duration rather than adding
 * to the damage - the reward for hitting again is that it lasts, not that it doubles.
 */
public final class IntervalEffectRunner {

    /**
     * Where a due application actually runs.
     *
     * <p><b>The sweep decides WHAT is due; this decides WHERE it happens</b>, and the two are not the
     * same thread. Deciding is arithmetic over a map and belongs off the tick. Applying is not: a
     * damage application walks the whole combat pipeline, which publishes a death, which reaches
     * listeners that look up entities - and {@code Server#getEntity} walks the chunk structure, which
     * Paper's AsyncCatcher refuses off the owning thread.
     *
     * <p>That was not a theory. A single poison ticking on a dying mob produced a stack trace per
     * tick from three different listeners - experience distribution, the coin drop, the backstab
     * check - each one a different symptom of the same missing hop.
     *
     * <p>Bound to the <b>caster</b>, not the target: a caster is a player, and a player is resolvable
     * from any thread, while a mob is not (see {@code PaperSchedulerAdapter.resolve}). Their targets
     * are within an ability's range of them, so on a region-threaded server they are in the caster's
     * region anyway.
     *
     * <p>One task per caster per sweep, not one per instance: two hundred poisons from one player are
     * still one hop. Constitution II rules out a recurring task per effect, and this is not one - it
     * is the same entity-bound single-shot ADR-024 already uses for cast times.
     */
    @FunctionalInterface
    public interface OnTick {
        void run(UUID casterId, Runnable task);

        /** Runs it right here. The default, and what every test uses. */
        static OnTick inline() {
            return (casterId, task) -> task.run();
        }
    }

    private volatile OnTick onTick = OnTick.inline();

    /** Installs the hop. At startup, not during play. */
    public void setOnTick(OnTick onTick) {
        this.onTick = Objects.requireNonNull(onTick, "onTick");
    }

    /**
     * One running instance: what applies, to whom <b>or where</b>, how often and until when.
     *
     * <p>Exactly one of {@code targetId} and {@code anchor} is set. A poison travels inside the
     * creature it is in; a storm stays on the ground it was called down onto and asks every tick who
     * is standing there <em>now</em>. The second kind cannot be expressed by remembering creatures,
     * and trying to was the bug: whoever walked out of Lightning Storm kept burning and whoever
     * walked in stayed dry.
     */
    private record Instance(
            Ability ability,
            EffectSpec spec,
            UUID casterId,
            UUID targetId,
            WorldPosition anchor,
            int rank,
            StatSnapshot snapshot,
            int stacks,
            Instant nextAt,
            Instant endsAt) {

        Instance advanced(Instant now) {
            return new Instance(
                    ability,
                    spec,
                    casterId,
                    targetId,
                    anchor,
                    rank,
                    snapshot,
                    stacks,
                    now.plus(spec.interval()),
                    endsAt);
        }

        Instance stacked(int newStacks, Instant newEnd) {
            return new Instance(
                    ability,
                    spec,
                    casterId,
                    targetId,
                    anchor,
                    rank,
                    snapshot,
                    newStacks,
                    nextAt,
                    newEnd);
        }
    }

    /** Keyed so a second application of the same effect on the same target stacks rather than adds. */
    private record Key(String abilityId, EffectSpec spec, UUID targetId) {}

    private final Map<Key, Instance> instances = new ConcurrentHashMap<>();
    private final EffectDispatcher dispatcher;
    private final Clock clock;

    /** Only an anchored instance ever asks it; a poison knows its target already. */
    private volatile TargetResolver targets = TargetResolver.none();

    public IntervalEffectRunner(EffectDispatcher dispatcher, Clock clock) {
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Starts an interval effect, or stacks onto one already running on that target. */
    public void start(
            Ability ability,
            EffectSpec spec,
            UUID casterId,
            UUID targetId,
            int rank,
            StatSnapshot snapshot) {
        if (!spec.isPeriodic()) {
            throw new IllegalArgumentException(ability.id() + ": " + spec.type() + " has no interval");
        }
        Instant now = clock.instant();
        Key key = new Key(ability.id(), spec, targetId);
        Instance existing = instances.get(key);
        Instant endsAt = now.plus(spec.duration());

        if (existing == null) {
            instances.put(
                    key,
                    new Instance(
                            ability,
                            spec,
                            casterId,
                            targetId,
                            null,
                            rank,
                            snapshot,
                            1,
                            now.plus(spec.interval()),
                            endsAt));
            return;
        }
        // At the maximum the duration is refreshed and the effect is not - hitting again keeps it
        // going, it does not make it stronger (FR-010c).
        int stacks = Math.min(spec.maxStacks(), existing.stacks() + 1);
        instances.put(key, existing.stacked(stacks, endsAt));
    }

    /**
     * Starts a periodic effect on a <b>place</b> rather than on a creature (FR-019b).
     *
     * <p>One instance for the whole area, not one per creature that happened to be standing in it -
     * so the storm keeps raining where it was called down, hits whoever walks in, and stops hitting
     * whoever walks out. Keyed by caster instead of by target, because there is no target yet: a
     * second cast by the same mage refreshes his storm rather than starting a parallel one.
     *
     * <p><b>No stacking.</b> Two storms on one spot would be two instances if two mages cast them,
     * and one refreshed instance if the same mage did - and neither case wants a doubled number.
     */
    public void startArea(
            Ability ability,
            EffectSpec spec,
            UUID casterId,
            WorldPosition anchor,
            int rank,
            StatSnapshot snapshot) {
        if (!spec.isPeriodic()) {
            throw new IllegalArgumentException(ability.id() + ": " + spec.type() + " has no interval");
        }
        Objects.requireNonNull(anchor, "anchor");
        Instant now = clock.instant();
        instances.put(
                new Key(ability.id(), spec, casterId),
                new Instance(
                        ability,
                        spec,
                        casterId,
                        null,
                        anchor,
                        rank,
                        snapshot,
                        1,
                        now.plus(spec.interval()),
                        now.plus(spec.duration())));
    }

    /** Installs the lookup an anchored instance needs. At startup, not during play. */
    public void setTargets(TargetResolver targets) {
        this.targets = Objects.requireNonNull(targets, "targets");
    }

    /**
     * Applies everything that is due and drops what has expired.
     *
     * <p>Driven from outside, like {@code publishExpiredDamageWindows} in B05: this class owns no task
     * (Constitution I).
     *
     * @return how many applications were made, for the log and for a test
     */
    public int sweep() {
        Instant now = clock.instant();
        // Collected first, applied after: the bookkeeping below is arithmetic and stays here, the
        // applications go to the caster's tick through OnTick. Grouped by caster so that a player
        // with several effects running costs one hop, not one per effect.
        Map<UUID, List<Instance>> due = new java.util.LinkedHashMap<>();
        int applied = 0;
        for (Iterator<Map.Entry<Key, Instance>> it = instances.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Key, Instance> entry = it.next();
            Instance instance = entry.getValue();

            // Due first, expiry second, and the order is the off-by-one. A six-second poison ticking
            // once a second applies six times, and the sixth falls exactly on the end - checking
            // expiry first swallowed it, and the effect quietly did five sixths of what its
            // configuration says.
            if (!instance.nextAt().isAfter(now)) {
                due.computeIfAbsent(instance.casterId(), id -> new ArrayList<>()).add(instance);
                applied++;
                entry.setValue(instance.advanced(now));
            }
            if (!instance.endsAt().isAfter(now)) {
                it.remove();
            }
        }
        for (Map.Entry<UUID, List<Instance>> entry : due.entrySet()) {
            List<Instance> batch = entry.getValue();
            onTick.run(entry.getKey(), () -> batch.forEach(this::apply));
        }
        // How many were HANDED OVER, not how many landed: once the hop exists, the answer to
        // "did it land" belongs to the tick that runs it, and no caller here can wait for that.
        return applied;
    }

    /** Drops everything on or from a holder - on death, logout and character switch. */
    public void forget(UUID holderId) {
        instances.entrySet()
                .removeIf(
                        entry ->
                                entry.getValue().targetId().equals(holderId)
                                        || entry.getValue().casterId().equals(holderId));
    }

    /** How many instances are running. For leak tests and for SC-002. */
    public int runningCount() {
        return instances.size();
    }

    private void apply(Instance instance) {
        EffectSpec spec = instance.spec();
        double perStack = spec.valueAtRank(instance.rank());
        double total = perStack * instance.stacks();
        if (spec.stackCap() != null) {
            total = Math.min(total, spec.stackCap());
        }

        // A one-shot copy of the spec carrying the stacked amount: the primitive applies a value and
        // should not have to know that stacking exists.
        EffectSpec resolved =
                new EffectSpec(
                        spec.type(),
                        total,
                        0.0,
                        null,
                        null,
                        1,
                        null,
                        spec.attribute(),
                        spec.damageType(),
                        spec.origins(),
                        spec.statusEffect(),
                        spec.buildPerHit(),
                        spec.idleBefore(),
                        spec.decayPerSecond(),
                        spec.asFraction(),
                        spec.phase());

        // The one place where the two kinds part. A creature-bound instance knows its target and has
        // known it since the cast; an anchored one asks the ground, every single time, because that
        // is the whole meaning of standing in a storm.
        List<UUID> target =
                instance.anchor() == null
                        ? List.of(instance.targetId())
                        : targets.resolveAt(
                                instance.casterId(),
                                instance.anchor(),
                                instance.ability().target());
        if (target.isEmpty()) {
            // Nobody in the area this tick. Ordinary, and not a reason to stop raining.
            return;
        }
        dispatcher.runOne(
                instance.ability(), resolved, instance.casterId(), target, 1, instance.snapshot());
    }
}
