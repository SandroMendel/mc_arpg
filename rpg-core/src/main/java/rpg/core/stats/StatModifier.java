package rpg.core.stats;

import java.util.Objects;

/**
 * One contribution to one attribute (FR-005).
 *
 * <p>Deliberately carries no source id - that lives on the {@link ModifierSet}. Repeating it per
 * contribution would allow a set whose entries disagree about where they came from, which is a
 * state nothing should have to handle.
 *
 * <p>{@code NaN} and infinity are refused here rather than clamped later. A single infinite
 * contribution would otherwise poison every value derived from it, and the point at which that
 * becomes visible is far from the point where it was introduced.
 *
 * @param attribute which attribute this affects
 * @param operation how it enters the formula
 * @param value the amount; for {@code PERCENT} a fraction, so 0.2 means +20%
 */
public record StatModifier(Attribute attribute, ModifierOperation operation, double value) {

    public StatModifier {
        Objects.requireNonNull(attribute, "attribute");
        Objects.requireNonNull(operation, "operation");
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(
                    "modifier value for "
                            + attribute.key()
                            + " must be a finite number, but was "
                            + value);
        }
    }

    /** A flat contribution. */
    public static StatModifier flat(Attribute attribute, double value) {
        return new StatModifier(attribute, ModifierOperation.FLAT, value);
    }

    /** A percentage contribution, as a fraction: {@code percent(HEALTH, 0.2)} is +20%. */
    public static StatModifier percent(Attribute attribute, double value) {
        return new StatModifier(attribute, ModifierOperation.PERCENT, value);
    }
}
