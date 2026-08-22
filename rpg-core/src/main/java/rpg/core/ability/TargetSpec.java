package rpg.core.ability;

import java.util.Objects;

/**
 * How an ability finds its targets - mode, reach and the hard cap (FR-019, FR-020).
 *
 * @param mode one of the nine
 * @param range reach in blocks; greater than zero for every mode except {@link TargetMode#SELF}. For
 *     {@link TargetMode#GROUND_AREA} it is the maximum distance the anchor may be from the caster
 * @param angle required for {@link TargetMode#CONE}, in degrees, within {@code (0, 180]}
 * @param maxTargets the hard ceiling, <b>required</b> for every multi-target mode
 * @param hopRange required for {@link TargetMode#CHAIN} - the radius searched around the last target
 *     hit, not around the caster
 * @param areaRadius required for {@link TargetMode#GROUND_AREA} - the size of the anchored patch
 */
public record TargetSpec(
        TargetMode mode,
        double range,
        Double angle,
        int maxTargets,
        Double hopRange,
        Double areaRadius) {

    public TargetSpec {
        Objects.requireNonNull(mode, "mode");

        // V21
        if (mode.needsRange()) {
            if (!Double.isFinite(range) || range <= 0.0) {
                throw new IllegalArgumentException(
                        mode + ": range must be greater than zero, but was " + range);
            }
        }

        // V22
        if (mode == TargetMode.CONE) {
            if (angle == null || !Double.isFinite(angle) || angle <= 0.0 || angle > 180.0) {
                throw new IllegalArgumentException(
                        "CONE needs an angle within (0, 180], but was " + angle);
            }
        } else if (angle != null) {
            throw new IllegalArgumentException(mode + ": angle means nothing outside CONE");
        }

        // V23 - the important one. No default on purpose: a default would make a forgotten line
        // indistinguishable from a decision, which is the same reasoning B07 uses for demanding every
        // attribute field including the zeros.
        if (mode.multiTarget()) {
            if (maxTargets < 1) {
                throw new IllegalArgumentException(
                        mode
                                + ": max-targets is required and must be at least 1 - without a ceiling"
                                + " one ability in a horde blows the tick budget");
            }
        } else if (maxTargets != 1) {
            // V24
            throw new IllegalArgumentException(
                    mode + ": returns a single target, so max-targets must be 1, but was " + maxTargets);
        }

        if (mode == TargetMode.CHAIN) {
            if (hopRange == null || !Double.isFinite(hopRange) || hopRange <= 0.0) {
                throw new IllegalArgumentException(
                        "CHAIN needs a positive hop-range - it searches around the last target hit,"
                                + " not around the caster");
            }
        } else if (hopRange != null) {
            throw new IllegalArgumentException(mode + ": hop-range means nothing outside CHAIN");
        }

        if (mode == TargetMode.GROUND_AREA) {
            if (areaRadius == null || !Double.isFinite(areaRadius) || areaRadius <= 0.0) {
                throw new IllegalArgumentException("GROUND_AREA needs a positive area-radius");
            }
        } else if (areaRadius != null) {
            throw new IllegalArgumentException(
                    mode + ": area-radius means nothing outside GROUND_AREA");
        }
    }

    /** The single-target shorthand - {@code maxTargets} is 1 and everything optional is absent. */
    public static TargetSpec single(TargetMode mode, double range) {
        return new TargetSpec(mode, range, null, 1, null, null);
    }

    /** The caster, with no reach at all. */
    public static TargetSpec self() {
        return new TargetSpec(TargetMode.SELF, 0.0, null, 1, null, null);
    }

    /** An area around the caster, with its required ceiling. */
    public static TargetSpec radius(double range, int maxTargets) {
        return new TargetSpec(TargetMode.RADIUS, range, null, maxTargets, null, null);
    }
}
