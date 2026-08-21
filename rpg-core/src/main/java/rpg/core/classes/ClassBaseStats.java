package rpg.core.classes;

import java.util.Objects;

import rpg.core.stats.Attribute;
import rpg.core.stats.BaseStatSink;

/**
 * The eight base values of a class - the naked character at level 1 (FR-001).
 *
 * <p>Deliberately a different type from {@link ClassGrowth}, although both hold eight doubles. "A
 * value" and "a value per level" are different things, and a shared type would have permitted the
 * confusion that only has to happen once to compute wrongly for sixty levels.
 *
 * <p>Backed by an array indexed by {@link Attribute#ordinal()} rather than a map: this is read on
 * every recalculation, and Constitution II forbids avoidable allocation in the hot path.
 */
public final class ClassBaseStats {

    private final double[] values;

    private ClassBaseStats(double[] values) {
        this.values = values;
    }

    /**
     * @param values one entry per attribute, in declaration order of {@link Attribute}
     * @throws IllegalArgumentException if the length is wrong or a value is not finite - a missing
     *     field is a startup error, never a silent zero (FR-001)
     */
    public static ClassBaseStats of(double[] values) {
        Objects.requireNonNull(values, "values");
        if (values.length != Attribute.count()) {
            throw new IllegalArgumentException(
                    "base-stats needs exactly "
                            + Attribute.count()
                            + " values, but got "
                            + values.length);
        }
        for (Attribute attribute : Attribute.all()) {
            double value = values[attribute.ordinal()];
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException(
                        "base-stats." + attribute.key() + " must be finite, but was " + value);
            }
        }
        return new ClassBaseStats(values.clone());
    }

    public double of(Attribute attribute) {
        return values[attribute.ordinal()];
    }

    /** Adds every base value to the sink. Part of the single class contribution (FR-009). */
    public void contributeTo(BaseStatSink sink) {
        for (Attribute attribute : Attribute.all()) {
            double value = values[attribute.ordinal()];
            if (value != 0.0) {
                sink.addBase(attribute, value);
            }
        }
    }
}
