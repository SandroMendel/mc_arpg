package rpg.core.combat;

import rpg.core.stats.DamageMitigation;

/**
 * The damage formula (FR-001 to FR-006).
 *
 * <pre>
 *   PHYSICAL / MAGIC:  raw   = attackerAttribute * factor
 *                      final = raw * 100/(100 + defence)      [B04's divisor model]
 *
 *   ENVIRONMENT:       raw   = configured amount               [fall: from the height]
 *                      final = raw                             [defence does not apply]
 * </pre>
 *
 * <p>Static, stateless and free of randomness. There is no crit, no dodge, no block and no
 * resistance type (ADR-008), which is also why the question "does crit apply before or after
 * defence" does not exist here.
 *
 * <p>Worked examples, kept in sync with the tests and with data-model.md:
 *
 * <table border="1">
 *   <caption>Examples</caption>
 *   <tr><th>attacker</th><th>factor</th><th>defence</th><th>raw</th><th>final</th></tr>
 *   <tr><td>50 physical</td><td>1.0</td><td>100</td><td>50.0</td><td>25.0</td></tr>
 *   <tr><td>100 physical</td><td>1.0</td><td>300</td><td>100.0</td><td>25.0</td></tr>
 *   <tr><td>100 physical</td><td>1.0</td><td>0</td><td>100.0</td><td>100.0</td></tr>
 *   <tr><td>40 magic</td><td>1.8</td><td>100</td><td>72.0</td><td>36.0</td></tr>
 *   <tr><td>fall from 10</td><td>-</td><td>any</td><td>28.0</td><td>28.0</td></tr>
 * </table>
 */
public final class DamageFormula {

    private DamageFormula() {}

    /**
     * Raw damage before defence.
     *
     * @param baseAttribute the attacker's value for the attribute this type uses
     * @param factor share of it; 1.0 for a melee swing, 1.8 for an ability at 180% (FR-002a)
     * @throws IllegalArgumentException if either input is not finite, or the factor is negative -
     *     refused rather than reinterpreted as healing (FR-006)
     */
    public static double rawDamage(double baseAttribute, double factor) {
        if (!Double.isFinite(baseAttribute) || baseAttribute < 0.0) {
            throw new IllegalArgumentException(
                    "base attribute must be finite and not negative, but was " + baseAttribute);
        }
        if (!Double.isFinite(factor) || factor < 0.0) {
            throw new IllegalArgumentException(
                    "damage factor must be finite and not negative, but was " + factor);
        }
        return baseAttribute * factor;
    }

    /**
     * Applies the target's defence - B04's divisor model, unchanged.
     *
     * <p>Delegated rather than reimplemented: defence is a property of the attribute, and having
     * two copies of the curve is how the combat pipeline and the character sheet start disagreeing.
     */
    public static double afterDefence(double raw, double defence) {
        return DamageMitigation.afterDefense(raw, defence);
    }

    /**
     * Fall damage from the height fallen (FR-012c).
     *
     * <p>A fixed amount, not a share of maximum health. That is the design decision, not a
     * simplification: a fall should matter to a beginner with 100 health and become negligible to a
     * geared player with 2000. With the shipped values a fall from 10 blocks costs 28 - 28% of a
     * beginner, 1.4% of a geared player.
     */
    public static double fallDamage(double fallenBlocks, FallDamageConfig config) {
        if (!Double.isFinite(fallenBlocks) || fallenBlocks <= 0.0) {
            return 0.0;
        }
        double beyondSafe = fallenBlocks - config.safeBlocks();
        if (beyondSafe <= 0.0) {
            return 0.0;
        }
        return Math.min(config.maxDamage(), beyondSafe * config.damagePerBlock());
    }

    /** Whether a raw damage value may be applied at all (FR-006). */
    public static boolean isUsable(double damage) {
        return Double.isFinite(damage) && damage >= 0.0;
    }
}
