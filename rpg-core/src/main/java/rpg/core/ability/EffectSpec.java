package rpg.core.ability;

import java.time.Duration;
import java.util.Objects;

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
 * @param damageType required for {@code DAMAGE}; on {@code SHIELD} and {@code EVADE} it is an
 *     optional <b>filter</b> instead, and {@code null} there means "every kind"
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
        requireNonNegative(type, "duration", duration);
        requireNonNegative(type, "interval", interval);
        requireNonNegative(type, "idle-before", idleBefore);
        validateInterval(type, duration, interval, maxStacks, stackCap);
        validateParameters(type, attribute, damageType, statusEffect);
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

    /** V16 to V18 and V41 - which parameter belongs to which primitive. */
    private static void validateParameters(
            EffectType type, Attribute attribute, DamageType damageType, String statusEffect) {
        switch (type) {
            case BUFF, DEBUFF, METER -> require(attribute != null, type, "an attribute");
            case DAMAGE -> require(damageType != null, type, "a damage-type");
            case STATUS_EFFECT ->
                    require(
                            statusEffect != null && !statusEffect.isBlank(),
                            type,
                            "a status-effect name");
            default -> {
                // Nothing required. The filter on SHIELD and EVADE is optional by design: absent
                // means "every kind of damage", which is the mage's Magic Shield.
            }
        }
        if (damageType != null && type != EffectType.DAMAGE && !isFilterable(type)) {
            throw new IllegalArgumentException(
                    type
                            + ": damage-type is a required value on DAMAGE and an optional filter on"
                            + " SHIELD and EVADE - it means nothing here");
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
        return type == EffectType.SHIELD || type == EffectType.EVADE;
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
