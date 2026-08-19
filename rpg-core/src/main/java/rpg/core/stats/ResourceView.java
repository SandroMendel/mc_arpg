package rpg.core.stats;

/**
 * A reading of a holder's resources: current values with the maxima they are measured against.
 *
 * <p>Both maxima come from the same snapshot, so a caller can never hold a current value against a
 * maximum from a different calculation round.
 */
public record ResourceView(
        double currentHealth, double maxHealth, double currentMana, double maxMana) {

    /** The share of health remaining, in {@code [0, 1]}. Used for the vanilla bar (ADR-003). */
    public double healthFraction() {
        return maxHealth <= 0.0 ? 0.0 : currentHealth / maxHealth;
    }

    /** The share of mana remaining, in {@code [0, 1]}. */
    public double manaFraction() {
        return maxMana <= 0.0 ? 0.0 : currentMana / maxMana;
    }
}
