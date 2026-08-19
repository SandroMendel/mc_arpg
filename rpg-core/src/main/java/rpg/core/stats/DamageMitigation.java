package rpg.core.stats;

/**
 * The defense formula from ADR-008, as a pure function (FR-015).
 *
 * <p>Divisor model: {@code damage * 100 / (100 + defense)}. It approaches complete immunity without
 * ever reaching it, which is why no separate cap is needed - 300 defense is 75% mitigation, 900
 * would be 90%, and no amount of stacking produces invulnerability.
 *
 * <p>Lives here rather than in B05 because it is a property of the defense attribute, not of the
 * combat pipeline. B05 decides when damage happens; this decides what defense does to it.
 */
public final class DamageMitigation {

    /** The constant from ADR-008. Defense equal to this value halves incoming damage. */
    public static final double DIVISOR_CONSTANT = 100.0;

    /**
     * Floor for the divisor.
     *
     * <p>Only reachable with defense at or below -100, which no configuration allows but a
     * misbehaving contributor could still produce. Without the floor the divisor would cross zero:
     * damage would flip sign and heal the target, or divide by zero outright. Clamping caps the
     * amplification at 100x instead, which is survivable and obvious in a log.
     */
    public static final double MIN_DIVISOR = 1.0;

    private DamageMitigation() {}

    /**
     * Applies defense to a raw damage value.
     *
     * @param raw damage before mitigation
     * @param defense the target's defense; may be negative, which amplifies
     * @return the damage that gets through - always finite, never negative for non-negative input
     */
    public static double afterDefense(double raw, double defense) {
        if (raw == 0.0) {
            return 0.0;
        }
        return raw * (DIVISOR_CONSTANT / divisor(defense));
    }

    /**
     * The fraction of damage that defense removes.
     *
     * <p>In {@code [0, 1)} for non-negative defense: 0 at defense 0, exactly 0.75 at 300, and
     * approaching 1 without reaching it. Negative for negative defense, where it means
     * amplification rather than mitigation.
     */
    public static double reductionFactor(double defense) {
        return 1.0 - (DIVISOR_CONSTANT / divisor(defense));
    }

    private static double divisor(double defense) {
        if (Double.isNaN(defense)) {
            throw new IllegalArgumentException("defense must not be NaN");
        }
        return Math.max(MIN_DIVISOR, DIVISOR_CONSTANT + defense);
    }
}
