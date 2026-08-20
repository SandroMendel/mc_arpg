package rpg.core.combat;

/**
 * How a fall turns into damage (FR-012c).
 *
 * <pre>
 *   damage = min(maxDamage, max(0, fallenBlocks - safeBlocks) * damagePerBlock)
 * </pre>
 *
 * <p>The ceiling exists so a drop next to the void does not produce an absurd number, and the safe
 * height so ordinary movement is not a slow death.
 *
 * @param safeBlocks below this height nothing happens
 * @param damagePerBlock damage per block beyond the safe height
 * @param maxDamage upper bound
 */
public record FallDamageConfig(double safeBlocks, double damagePerBlock, double maxDamage) {

    public FallDamageConfig {
        if (!Double.isFinite(safeBlocks) || safeBlocks < 0.0) {
            throw new IllegalArgumentException(
                    "environment.fall.safe-blocks must be finite and not negative, but was "
                            + safeBlocks);
        }
        if (!Double.isFinite(damagePerBlock) || damagePerBlock <= 0.0) {
            throw new IllegalArgumentException(
                    "environment.fall.damage-per-block must be finite and greater than zero, but"
                            + " was " + damagePerBlock);
        }
        if (!Double.isFinite(maxDamage) || maxDamage <= 0.0) {
            throw new IllegalArgumentException(
                    "environment.fall.max-damage must be finite and greater than zero, but was "
                            + maxDamage);
        }
    }

    /** The shipped values. A fall from 10 blocks costs 28. */
    public static FallDamageConfig defaults() {
        return new FallDamageConfig(3.0, 4.0, 200.0);
    }
}
