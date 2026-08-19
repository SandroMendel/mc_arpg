package rpg.core.stats;

/**
 * The current health and mana of one holder (FR-025 to FR-027).
 *
 * <p>Immutable, like everything else that leaves this block. The maxima are deliberately not stored
 * here - they live in the snapshot, and duplicating them would create the one state nobody wants: a
 * current value clamped against a maximum from an older calculation round.
 *
 * <p>This block owns the container and the clamping rules. It does <b>not</b> own the reasons a
 * value changes: damage and healing are B05, ability costs and mana regeneration are B08. Reaching
 * zero health is reported as a fact, never acted upon.
 *
 * @param currentHealth in {@code [0, maxHealth]}
 * @param currentMana in {@code [0, maxMana]}
 */
public record ResourcePool(double currentHealth, double currentMana) {

    public ResourcePool {
        if (!Double.isFinite(currentHealth) || currentHealth < 0.0) {
            throw new IllegalArgumentException(
                    "currentHealth must be finite and not negative, but was " + currentHealth);
        }
        if (!Double.isFinite(currentMana) || currentMana < 0.0) {
            throw new IllegalArgumentException(
                    "currentMana must be finite and not negative, but was " + currentMana);
        }
    }

    /** A freshly created holder starts at its maxima (FR-027). */
    public static ResourcePool full(double maxHealth, double maxMana) {
        return new ResourcePool(Math.max(0.0, maxHealth), Math.max(0.0, maxMana));
    }

    /** The same pool with a different health value, clamped into {@code [0, maxHealth]}. */
    public ResourcePool withHealth(double value, double maxHealth) {
        return new ResourcePool(clamp(value, maxHealth), currentMana);
    }

    /** The same pool with a different mana value, clamped into {@code [0, maxMana]}. */
    public ResourcePool withMana(double value, double maxMana) {
        return new ResourcePool(currentHealth, clamp(value, maxMana));
    }

    /**
     * The same pool with both values clamped against new maxima (FR-026).
     *
     * <p>A rising maximum leaves the current value alone - putting on a health item is not a heal.
     * A falling maximum pulls the value down with it, which is what stops a gear change from
     * leaving someone at 900 of 800 health. Neither case is a death: this returns a value, nothing
     * more.
     */
    public ResourcePool clampedTo(double maxHealth, double maxMana) {
        double health = clamp(currentHealth, maxHealth);
        double mana = clamp(currentMana, maxMana);
        return health == currentHealth && mana == currentMana
                ? this
                : new ResourcePool(health, mana);
    }

    /** Whether this holder has no health left. B05 decides what that means. */
    public boolean isDepleted() {
        return currentHealth == 0.0;
    }

    private static double clamp(double value, double max) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("resource value must be finite, but was " + value);
        }
        if (value < 0.0) {
            return 0.0;
        }
        double ceiling = Math.max(0.0, max);
        return value > ceiling ? ceiling : value;
    }
}
