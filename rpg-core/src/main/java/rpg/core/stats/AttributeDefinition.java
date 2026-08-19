package rpg.core.stats;

import java.util.Objects;

/**
 * Base value and bounds of one attribute (FR-002).
 *
 * <p>Every rule below is enforced in the constructor rather than trusted, and every message names
 * the attribute and the field. That is not politeness: this object is built from an operator-edited
 * file, and "invalid configuration" without a location means someone reads all eight attributes
 * looking for the one that is wrong.
 *
 * @param attribute which attribute this describes
 * @param base the value before any contribution
 * @param min lower bound of the final value
 * @param max upper bound of the final value - for {@code ABILITY_COOLDOWN} this is the hard cap
 *     from FR-013
 * @param modifierBand permitted relative deviation from {@code base}, or {@code 0} for unbounded.
 *     Used by attack and movement speed (FR-014), where the effective limit is a band around the
 *     vanilla base rather than an absolute ceiling.
 */
public record AttributeDefinition(
        Attribute attribute, double base, double min, double max, double modifierBand) {

    /** Lower bound for {@link Attribute#HEALTH}: a holder can never have a maximum of zero. */
    public static final double MIN_HEALTH_FLOOR = 1.0;

    public AttributeDefinition {
        Objects.requireNonNull(attribute, "attribute");

        requireFinite(attribute, "base", base);
        requireFinite(attribute, "min", min);
        requireFinite(attribute, "max", max);
        requireFinite(attribute, "modifier-band", modifierBand);

        if (min >= max) {
            throw new IllegalArgumentException(
                    "attribute '"
                            + attribute.key()
                            + "': min ("
                            + min
                            + ") must be less than max ("
                            + max
                            + ")");
        }
        if (base < min || base > max) {
            throw new IllegalArgumentException(
                    "attribute '"
                            + attribute.key()
                            + "': base ("
                            + base
                            + ") must lie within [min, max] = ["
                            + min
                            + ", "
                            + max
                            + "]");
        }
        if (modifierBand < 0.0) {
            throw new IllegalArgumentException(
                    "attribute '"
                            + attribute.key()
                            + "': modifier-band ("
                            + modifierBand
                            + ") must not be negative");
        }
        if (attribute == Attribute.HEALTH && min < MIN_HEALTH_FLOOR) {
            throw new IllegalArgumentException(
                    "attribute 'health': min ("
                            + min
                            + ") must be at least "
                            + MIN_HEALTH_FLOOR
                            + " - a holder cannot have a maximum health of zero");
        }
        if (attribute.kind() == AttributeKind.PERCENT && (min < -1.0 || max > 1.0)) {
            throw new IllegalArgumentException(
                    "attribute '"
                            + attribute.key()
                            + "' is a percent attribute: min ("
                            + min
                            + ") must be >= -1.0 and max ("
                            + max
                            + ") must be <= 1.0");
        }
        if (bandRequired(attribute) && modifierBand <= 0.0) {
            throw new IllegalArgumentException(
                    "attribute '"
                            + attribute.key()
                            + "': modifier-band is required and must be greater than zero -"
                            + " it is the effective limit for this attribute (FR-014)");
        }
        if (!bandRequired(attribute) && modifierBand != 0.0) {
            throw new IllegalArgumentException(
                    "attribute '"
                            + attribute.key()
                            + "': modifier-band ("
                            + modifierBand
                            + ") has no effect here and would silently be ignored - remove it."
                            + " Only attackSpeed and movementSpeed use a band");
        }
    }

    /** Whether {@code modifierBand} is meaningful for this attribute (FR-014). */
    public static boolean bandRequired(Attribute attribute) {
        return attribute == Attribute.ATTACK_SPEED || attribute == Attribute.MOVEMENT_SPEED;
    }

    /** Whether a band around the base value applies to this attribute. */
    public boolean hasBand() {
        return modifierBand > 0.0;
    }

    /**
     * Lower end of the band around a base value; only meaningful if {@link #hasBand()}.
     *
     * <p>Takes the base as a parameter rather than using {@link #base()}, because B06 and B07 will
     * contribute to the base through {@link BaseStatContributor}. The band has to move with the
     * base it belongs to - a band anchored to the configured value would tighten as a character
     * levels, which is not what "plus or minus 30 percent" means to anyone.
     */
    public double bandFloor(double effectiveBase) {
        return effectiveBase * (1.0 - modifierBand);
    }

    /** Upper end of the band around a base value; only meaningful if {@link #hasBand()}. */
    public double bandCeiling(double effectiveBase) {
        return effectiveBase * (1.0 + modifierBand);
    }

    private static void requireFinite(Attribute attribute, String field, double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(
                    "attribute '"
                            + attribute.key()
                            + "': "
                            + field
                            + " must be a finite number, but was "
                            + value);
        }
    }
}
