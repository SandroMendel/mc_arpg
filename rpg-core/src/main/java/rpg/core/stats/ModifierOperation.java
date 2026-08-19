package rpg.core.stats;

/**
 * How a contribution enters the formula (FR-005).
 *
 * <p>Two operations, because ADR-008 defines two:
 * {@code (base + sum(FLAT)) * (1 + sum(PERCENT))}. There is deliberately no third - a
 * "multiplicative" operation would reintroduce the chained percentages the ADR rules out.
 */
public enum ModifierOperation {

    /** Adds to the base value before the percentage factor is applied. */
    FLAT,

    /** Contributes to a single summed percentage, applied once. Expressed as a fraction: 0.2 = +20%. */
    PERCENT
}
