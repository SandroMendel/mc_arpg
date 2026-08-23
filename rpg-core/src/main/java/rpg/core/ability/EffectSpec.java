package rpg.core.ability;

import java.time.Duration;
import java.util.Objects;
import java.util.Set;

import rpg.core.combat.DamageOrigin;
import rpg.core.combat.DamageType;
import rpg.core.stats.Attribute;

/**
 * One building block inside an ability - a primitive plus its parameters (FR-011).
 *
 * <p>Immutable and shared: eighteen abilities hold a handful of these each, and every player reads
 * the same objects.
 *
 * @param type which primitive
 * @param amount the value at rank 1. For {@link EffectType#DAMAGE} a <b>factor</b> on the damage
 *     attribute, not an absolute number (FR-013)
 * @param perRank what each further rank adds; never negative - a rank-up takes nothing away
 * @param duration how long it lasts, or {@code null} for an instant effect
 * @param interval set means the effect applies <b>repeatedly</b> over its duration instead of once
 *     (FR-010a)
 * @param maxStacks how often it may stack; only meaningful with an interval (FR-010c)
 * @param stackCap ceiling on the combined effect per interval across all stacks
 * @param attribute required for {@code BUFF}, {@code DEBUFF} and {@code METER}
 * @param damageType required for {@code DAMAGE}; on {@code SHIELD}, {@code EVADE} and {@code
 *     MITIGATE} it is an optional <b>filter</b> instead, and {@code null} there means "every kind"
 * @param origins the second filter on those same three: <b>where</b> the hit came from, empty
 *     meaning "from anywhere". A damage type says a fireball and a lightning bolt are alike; an
 *     origin says a sword swing and a cast fireball are not, which is the only way to write "answers
 *     auto-attacks" without naming abilities one by one
 * @param statusEffect required for {@code STATUS_EFFECT} - a vanilla effect name
 * @param buildPerHit required for {@code METER} - how far one hit raises the counter
 * @param idleBefore required for {@code METER} - how long without damage before it starts falling
 * @param decayPerSecond required for {@code METER}
 * @param asFraction for {@code HEAL} and {@code MANA_RESTORE}: the amount is a share of the maximum
 *     rather than an absolute value. Second Life is written as "a share of maximum health", and 0.35
 *     health would be a rounding error where 35 percent of a warrior's 1997 is a rescue
 */
public record EffectSpec(
        EffectType type,
        double amount,
        double perRank,
        Duration duration,
        Duration interval,
        int maxStacks,
        Double stackCap,
        Attribute attribute,
        DamageType damageType,
        Set<DamageOrigin> origins,
        String statusEffect,
        Double buildPerHit,
        Duration idleBefore,
        Double decayPerSecond,
        boolean asFraction) {

    /** The counter runs from 0 to this - the warrior's Rage is spoken of in percent. */
    public static final double METER_MAXIMUM = 100.0;

    public EffectSpec {
        Objects.requireNonNull(type, "type");
        if (!Double.isFinite(amount)) {
            throw new IllegalArgumentException(type + ": amount must be finite, but was " + amount);
        }
        // V15: a rank-up never takes anything away - the same rule B07 draws for its growth rates.
        if (!Double.isFinite(perRank) || perRank < 0.0) {
            throw new IllegalArgumentException(
                    type + ": per-rank must not be negative, but was " + perRank);
        }
        if (maxStacks < 1) {
            throw new IllegalArgumentException(
                    type + ": max-stacks must be at least 1, but was " + maxStacks);
        }
        // Copied, not adopted - the same rule the ability's own lists follow. A caller that keeps its
        // set and adds to it afterwards must not be able to widen a filter that is already in play.
        origins = origins == null ? Set.of() : Set.copyOf(origins);
        requireNonNegative(type, "duration", duration);
        requireNonNegative(type, "interval", interval);
        requireNonNegative(type, "idle-before", idleBefore);
        validateInterval(type, duration, interval, maxStacks, stackCap);
        validateParameters(type, attribute, damageType, origins, statusEffect);
        validateMitigation(type, amount, perRank);
        validateMeter(type, attribute, buildPerHit, idleBefore, decayPerSecond);
        if (asFraction && type != EffectType.HEAL && type != EffectType.MANA_RESTORE) {
            throw new IllegalArgumentException(
                    type + ": as-fraction only means something on HEAL and MANA_RESTORE");
        }
    }

    /** V37 to V40 - what an interval means and what stacking requires. */
    private static void validateInterval(
            EffectType type, Duration duration, Duration interval, int maxStacks, Double stackCap) {
        if (interval != null) {
            if (duration == null || duration.isZero()) {
                throw new IllegalArgumentException(
                        type + ": an interval needs a duration - without one it would never end");
            }
            if (interval.isZero()) {
                throw new IllegalArgumentException(type + ": interval must be greater than zero");
            }
            if (interval.compareTo(duration) > 0) {
                throw new IllegalArgumentException(
                        type
                                + ": interval "
                                + interval
                                + " exceeds duration "
                                + duration
                                + " - the effect would never apply once");
            }
        } else if (maxStacks > 1) {
            throw new IllegalArgumentException(
                    type + ": max-stacks above 1 needs an interval - a one-shot applies twice, it does"
                            + " not stack");
        }
        if (maxStacks > 1 && stackCap == null) {
            throw new IllegalArgumentException(
                    type
                            + ": stacking needs a stack-cap - without a ceiling the poisoned blade grows"
                            + " without bound at enough hits");
        }
        if (stackCap != null && (!Double.isFinite(stackCap) || stackCap <= 0.0)) {
            throw new IllegalArgumentException(type + ": stack-cap must be positive");
        }
    }

    /**
     * A mitigation is a share, and a share above 1 is not a stronger one - it is a heal that arrives
     * as damage.
     *
     * <p>Checked at rank 1 and at the growth rate rather than at the top rank, because the spec does
     * not know the ability's {@code max-rank}. Five ranks of two percent cannot overshoot; the
     * primitive clamps what does anyway, so this catches the typo and the clamp catches the rest.
     */
    private static void validateMitigation(EffectType type, double amount, double perRank) {
        if (type != EffectType.MITIGATE) {
            return;
        }
        if (amount <= 0.0 || amount > 1.0) {
            throw new IllegalArgumentException(
                    type + ": amount is a share between 0 and 1, but was " + amount);
        }
        if (perRank > 1.0) {
            throw new IllegalArgumentException(
                    type + ": per-rank is a share between 0 and 1, but was " + perRank);
        }
    }

    /** V16 to V18 and V41 - which parameter belongs to which primitive. */
    private static void validateParameters(
            EffectType type,
            Attribute attribute,
            DamageType damageType,
            Set<DamageOrigin> origins,
            String statusEffect) {
        switch (type) {
            case BUFF, DEBUFF, METER -> require(attribute != null, type, "an attribute");
            case DAMAGE -> require(damageType != null, type, "a damage-type");
            case STATUS_EFFECT ->
                    require(
                            statusEffect != null && !statusEffect.isBlank(),
                            type,
                            "a status-effect name");
            default -> {
                // Nothing required. The filter on SHIELD, EVADE and MITIGATE is optional by design:
                // absent means "every kind of damage", which is the mage's Magic Shield.
            }
        }
        // V43: an origin filter on anything but the three filterable primitives is a silent lie -
        // the dispatcher never reads it, so the ability would answer everything while the file says
        // otherwise. Refusing at load is the only place this can still be seen.
        if (!origins.isEmpty() && !isFilterable(type)) {
            throw new IllegalArgumentException(
                    type
                            + ": origins is a filter on SHIELD, EVADE and MITIGATE - it means nothing"
                            + " here");
        }
        if (damageType != null && type != EffectType.DAMAGE && !isFilterable(type)) {
            throw new IllegalArgumentException(
                    type
                            + ": damage-type is a required value on DAMAGE and an optional filter on"
                            + " SHIELD, EVADE and MITIGATE - it means nothing here");
        }
    }

    /** V42 - a counter without build-up or without decay is not one. */
    private static void validateMeter(
            EffectType type,
            Attribute attribute,
            Double buildPerHit,
            Duration idleBefore,
            Double decayPerSecond) {
        if (type != EffectType.METER) {
            return;
        }
        require(attribute != null, type, "an attribute to scale");
        require(buildPerHit != null && buildPerHit > 0.0, type, "a positive build-per-hit");
        require(idleBefore != null, type, "an idle-before");
        require(decayPerSecond != null && decayPerSecond > 0.0, type, "a positive decay-per-second");
    }

    private static boolean isFilterable(EffectType type) {
        return type == EffectType.SHIELD
                || type == EffectType.EVADE
                || type == EffectType.MITIGATE;
    }

    /**
     * Whether this effect answers a hit of that kind and from there.
     *
     * <p>An absent filter says yes - both of them, independently. Magic Life sets origins and leaves
     * the damage type open, so it answers a sword and a skeleton's arrow alike and ignores what a
     * caster threw.
     */
    public boolean matches(DamageType damageType, DamageOrigin origin) {
        // On everything else a damage type is a required VALUE, not a filter - the poisoned blade
        // deals POISON, and reading that as "only answers poison" would silence it against anything
        // that is not already poison.
        if (!isFilterable(type)) {
            return true;
        }
        if (this.damageType != null && this.damageType != damageType) {
            return false;
        }
        return origins.isEmpty() || (origin != null && origins.contains(origin));
    }

    private static void require(boolean condition, EffectType type, String what) {
        if (!condition) {
            throw new IllegalArgumentException(type + " needs " + what);
        }
    }

    private static void requireNonNegative(EffectType type, String field, Duration value) {
        if (value != null && value.isNegative()) {
            throw new IllegalArgumentException(type + ": " + field + " must not be negative");
        }
    }

    /**
     * The value at {@code rank} - one multiplication on read, not a second set of definitions
     * (FR-063).
     */
    public double valueAtRank(int rank) {
        if (rank < 1) {
            throw new IllegalArgumentException("rank must be at least 1, but was " + rank);
        }
        return amount + perRank * (rank - 1);
    }

    /** Whether this effect applies repeatedly over its duration rather than once (FR-010a). */
    public boolean isPeriodic() {
        return interval != null;
    }

    /** How often a periodic effect applies over its whole duration. */
    public int applications() {
        return isPeriodic() ? (int) (duration.toMillis() / interval.toMillis()) : 1;
    }
}
