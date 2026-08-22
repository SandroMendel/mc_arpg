package rpg.core.ability.effect;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import rpg.core.ability.EffectSpec;
import rpg.core.stats.ModifierSet;
import rpg.core.stats.SourceId;
import rpg.core.stats.SourceKind;
import rpg.core.stats.StatEngine;
import rpg.core.stats.StatModifier;

/**
 * The warrior's Rage: a counter from 0 to 100 that rises with damage and falls with quiet (FR-016b).
 *
 * <p><b>It looks like a third resource next to health and mana and is not one.</b> It is not stored,
 * it does not survive a logout, and its value follows from the last reading plus elapsed time - so it
 * costs no table and, more importantly, no task. A counter that ticked down every second would be a
 * recurring job per player, which is the one thing Constitution II rules out.
 *
 * <p>The contribution it makes is refreshed on every damage event, which is the only moment it
 * matters anyway: nobody can observe the value of rage except through what it does to a hit.
 */
public final class MeterEffect implements AbilityEffect {

    /** What is kept per holder: the reading, and when it was taken. */
    private record Reading(double value, Instant at) {}

    private final StatEngine stats;
    private final Clock clock;
    private final Map<UUID, Reading> readings = new ConcurrentHashMap<>();

    public MeterEffect(StatEngine stats, Clock clock) {
        this.stats = Objects.requireNonNull(stats, "stats");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public void apply(EffectContext context) {
        EffectSpec spec = context.spec();
        if (spec.buildPerHit() == null || spec.attribute() == null) {
            return;
        }
        UUID holderId = context.casterId();
        Instant now = clock.instant();

        double raised =
                Math.min(EffectSpec.METER_MAXIMUM, valueAt(holderId, spec, now) + spec.buildPerHit());
        readings.put(holderId, new Reading(raised, now));

        // The scaling is the counter's share of its maximum times the effect's value at this rank -
        // so a rank-up raises what full rage is worth, not how fast it fills.
        double share = raised / EffectSpec.METER_MAXIMUM;
        stats.apply(
                holderId,
                new ModifierSet(
                        sourceOf(context.ability().id(), spec),
                        List.of(
                                StatModifier.flat(
                                        spec.attribute(), context.value() * share))));
    }

    /**
     * The counter as of now - built, then decayed by whatever quiet has passed.
     *
     * <p>Public because B13 draws it, and because this is the whole trick: no state changes between
     * two hits, and the value is still right when asked.
     */
    public double valueAt(UUID holderId, EffectSpec spec, Instant now) {
        Reading reading = readings.get(holderId);
        if (reading == null) {
            return 0.0;
        }
        Duration idle = Duration.between(reading.at(), now).minus(spec.idleBefore());
        if (idle.isNegative() || idle.isZero()) {
            // Still inside the grace window: nothing has decayed yet.
            return reading.value();
        }
        double lost = spec.decayPerSecond() * (idle.toMillis() / 1000.0);
        return Math.max(0.0, reading.value() - lost);
    }

    /** Drops a holder's counter. Logout, death and character switch all start it again at zero. */
    public void forget(UUID holderId) {
        readings.remove(holderId);
    }

    /** How many counters are held. For leak tests. */
    public int trackedCount() {
        return readings.size();
    }

    private static SourceId sourceOf(String abilityId, EffectSpec spec) {
        return SourceId.of(SourceKind.BUFF, "meter:" + abilityId + ":" + spec.attribute().key());
    }
}
