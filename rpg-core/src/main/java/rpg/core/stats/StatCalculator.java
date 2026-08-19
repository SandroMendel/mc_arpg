package rpg.core.stats;

import java.util.Collection;
import java.util.Objects;

/**
 * The one formula (FR-011 to FR-014).
 *
 * <pre>
 *   effectiveBase = definition.base + sum(base contributions)
 *   raw           = (effectiveBase + sum(FLAT)) * (1 + sum(PERCENT))
 *   banded        = clamp(raw, effectiveBase * (1 - band), effectiveBase * (1 + band))   [if a band applies]
 *   final         = clamp(banded, definition.min, definition.max)
 * </pre>
 *
 * <p>Percentages are summed and applied once. Chaining them - {@code base * 1.5 * 1.5} instead of
 * {@code base * (1 + 0.5 + 0.5)} - is the difference between +100% and +125% from the same two
 * items, and it makes the result depend on the order things were equipped. ADR-008 rules it out.
 *
 * <p><b>Always computes from all sources, never by subtracting a removed one.</b> Floating point
 * addition is not associative: {@code (a + b) - b} is not reliably {@code a}. Incremental
 * bookkeeping would leave a residue every time a player takes an item off, and that residue
 * accumulates over a play session in a way that no single round-trip test would catch. Recomputing
 * from what remains makes the round trip exact by construction (FR-017, SC-004).
 *
 * <p>Cost: with eight attributes and ~20 sources this is a few hundred floating point operations,
 * three small arrays and one snapshot - and it only runs when something actually changed.
 */
public final class StatCalculator {

    private StatCalculator() {}

    /**
     * Computes a snapshot.
     *
     * @param config the validated attribute definitions
     * @param sets all contributing sources, <b>in a deterministic order</b> - the caller guarantees
     *     that, which is why holders keep their sources in a sorted map (FR-016)
     * @param baseBonus per-attribute base contributions indexed by ordinal, or {@code null} if
     *     there are none
     * @param revision the revision to stamp on the result
     */
    public static StatSnapshot compute(
            StatConfig config, Collection<ModifierSet> sets, double[] baseBonus, long revision) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(sets, "sets");

        int size = Attribute.count();
        double[] flat = new double[size];
        double[] percent = new double[size];

        for (ModifierSet set : sets) {
            // Indexed loop rather than for-each: this runs per source per recalculation, and an
            // iterator per set is an allocation this block promises not to make.
            var modifiers = set.modifiers();
            for (int i = 0, n = modifiers.size(); i < n; i++) {
                StatModifier modifier = modifiers.get(i);
                int index = modifier.attribute().ordinal();
                if (modifier.operation() == ModifierOperation.FLAT) {
                    flat[index] += modifier.value();
                } else {
                    percent[index] += modifier.value();
                }
            }
        }

        double[] values = new double[size];
        Attribute[] attributes = Attribute.all();
        for (int i = 0; i < size; i++) {
            AttributeDefinition definition = config.definition(attributes[i]);
            double effectiveBase = definition.base() + (baseBonus == null ? 0.0 : baseBonus[i]);
            double raw = (effectiveBase + flat[i]) * (1.0 + percent[i]);

            if (definition.hasBand()) {
                raw =
                        clamp(
                                raw,
                                definition.bandFloor(effectiveBase),
                                definition.bandCeiling(effectiveBase));
            }
            values[i] = clamp(raw, definition.min(), definition.max());
        }
        return new StatSnapshot(values, revision);
    }

    /**
     * Clamps into {@code [min, max]}.
     *
     * <p>{@code NaN} cannot arrive here: every input is checked for finiteness where it enters -
     * {@link StatModifier}, {@link AttributeDefinition} and {@link DefaultStatEngine}'s base sink.
     * The only remaining path would be an overflow to infinity from absurd values, which the clamp
     * catches, because infinity compares correctly against both bounds.
     */
    private static double clamp(double value, double min, double max) {
        if (value < min) {
            return min;
        }
        return value > max ? max : value;
    }
}
