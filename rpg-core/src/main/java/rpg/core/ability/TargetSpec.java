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
 * @param height optional, and only for the two area modes: with it the area is a <b>cylinder</b> of
 *     this half-height instead of a sphere.
 *     <p>The difference is not cosmetic. A sphere's sideways reach shrinks with every block of
 *     height: a whirl of 4.5 reaches 4.5 blocks at your feet and 3.3 at a mob standing three blocks
 *     up. On a hillside that reads as "sometimes it hits and sometimes it does not", and no player
 *     can predict it. A cylinder says something you can remember - everything around you, at your
 *     level and a bit above and below.
 */
public record TargetSpec(
        TargetMode mode,
        double range,
        Double angle,
        int maxTargets,
        Double hopRange,
        Double areaRadius,
        Double height) {

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

        // V23 - the important one, and the one a default quietly defeats. Zero means "not given": the
        // binder passes it through instead of substituting 1, because a substituted 1 would satisfy
        // this check and make a forgotten line indistinguishable from a decision. Same reasoning B07
        // uses for demanding every attribute field including the zeros.
        if (mode.multiTarget()) {
            if (maxTargets < 1) {
                throw new IllegalArgumentException(
                        mode
                                + ": max-targets is required and must be at least 1 - without a ceiling"
                                + " one ability in a horde blows the tick budget");
            }
        } else if (maxTargets == 0) {
            maxTargets = 1;
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

        if (height != null) {
            if (!Double.isFinite(height) || height <= 0.0) {
                throw new IllegalArgumentException(
                        mode + ": height must be greater than zero, but was " + height);
            }
            if (mode != TargetMode.RADIUS && mode != TargetMode.GROUND_AREA) {
                // A cone and a line already carry a shape of their own; a height on top of one would
                // be a second answer to the same question.
                throw new IllegalArgumentException(
                        mode + ": height means nothing outside RADIUS and GROUND_AREA");
            }
        }
    }

    /** The single-target shorthand - {@code maxTargets} is 1 and everything optional is absent. */
    public static TargetSpec single(TargetMode mode, double range) {
        return new TargetSpec(mode, range, null, 1, null, null, null);
    }

    /** The caster, with no reach at all. */
    public static TargetSpec self() {
        return new TargetSpec(TargetMode.SELF, 0.0, null, 1, null, null, null);
    }

    /** An area around the caster, with its required ceiling. */
    public static TargetSpec radius(double range, int maxTargets) {
        return new TargetSpec(TargetMode.RADIUS, range, null, maxTargets, null, null, null);
    }
}
