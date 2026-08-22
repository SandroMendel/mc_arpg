package rpg.core.ability.effect;

import java.time.Clock;
import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import rpg.core.stats.ModifierSet;
import rpg.core.stats.SourceId;
import rpg.core.stats.SourceKind;
import rpg.core.stats.StatEngine;
import rpg.core.stats.StatModifier;

/**
 * A timed modifier on one attribute (FR-014).
 *
 * <p>Serves both {@code BUFF} on the caster and {@code DEBUFF} on a target - the two differ only in
 * who they land on, and the targeting has already decided that by the time this runs. One class, one
 * expiry rule.
 *
 * <p><b>Expiry runs off a timestamp, not a countdown.</b> {@link #expire()} is driven from the same
 * sweep as the interval effects, so a buff that ran out is removed the next time anything happens
 * rather than by a task of its own.
 *
 * <p>The source id carries the ability and the attribute, so re-applying the same buff replaces its
 * own contribution instead of stacking a second one - the reason B04 keys contributions by source at
 * all. Two of the same buff would otherwise double the value and nothing would look wrong.
 */
public final class BuffEffect implements AbilityEffect {

    private record Applied(UUID holderId, SourceId source, Instant until) {}

    private final StatEngine stats;
    private final Clock clock;
    private final Map<SourceId, Applied> active = new ConcurrentHashMap<>();

    public BuffEffect(StatEngine stats, Clock clock) {
        this.stats = Objects.requireNonNull(stats, "stats");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public void apply(EffectContext context) {
        if (context.spec().duration() == null || context.spec().attribute() == null) {
            return;
        }
        Instant until = clock.instant().plus(context.spec().duration());
        for (UUID target : context.targets()) {
            SourceId source = sourceOf(context, target);
            stats.apply(
                    target,
                    new ModifierSet(
                            source,
                            List.of(
                                    StatModifier.flat(
                                            context.spec().attribute(), context.value()))));
            active.put(source, new Applied(target, source, until));
        }
    }

    /**
     * Removes what has run out.
     *
     * <p>Driven from outside on the same cadence as the interval sweep. Nothing here schedules
     * anything, which is what keeps a hundred buffs from becoming a hundred tasks.
     *
     * @return how many were removed
     */
    public int expire() {
        Instant now = clock.instant();
        int removed = 0;
        for (Iterator<Map.Entry<SourceId, Applied>> it = active.entrySet().iterator();
                it.hasNext(); ) {
            Applied applied = it.next().getValue();
            if (applied.until().isAfter(now)) {
                continue;
            }
            stats.remove(applied.holderId(), applied.source());
            it.remove();
            removed++;
        }
        return removed;
    }

    /** Drops every buff on a holder - on death, logout and character switch. */
    public void forget(UUID holderId) {
        active.entrySet()
                .removeIf(
                        entry -> {
                            if (!entry.getValue().holderId().equals(holderId)) {
                                return false;
                            }
                            stats.remove(holderId, entry.getKey());
                            return true;
                        });
    }

    /** How many are active. For leak tests. */
    public int activeCount() {
        return active.size();
    }

    private static SourceId sourceOf(EffectContext context, UUID target) {
        return SourceId.of(
                SourceKind.BUFF,
                context.ability().id() + ":" + context.spec().attribute().key() + ":" + target);
    }
}
