package rpg.core.stats;

/**
 * Whether an attribute carries an absolute number or a fraction.
 *
 * <p>This drives two things: how a value is presented (40% rather than 0.4) and how its
 * configuration is checked. A percent attribute whose ceiling exceeds 1.0 is a configuration error,
 * not a very strong buff - catching that at startup is cheaper than discovering it when a player
 * has a negative cooldown.
 */
public enum AttributeKind {

    /** A plain number: health, damage, defense. */
    ABSOLUTE,

    /** A fraction in [-1, 1]: cooldown reduction. */
    PERCENT
}
