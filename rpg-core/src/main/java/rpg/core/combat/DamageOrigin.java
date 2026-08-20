package rpg.core.combat;

/**
 * Where a damage event came from.
 *
 * <p>Separate from {@link DamageType}: a fireball and a sword swing differ in origin even when both
 * end up as physical damage, and the attack window applies to one but not the other.
 */
public enum DamageOrigin {

    /** A melee swing. Subject to the attack window (FR-021). */
    MELEE,

    /** An arrow or thrown item. Carries the raw damage from the moment it was launched (FR-024b). */
    PROJECTILE,

    /** An ability (B08). Not subject to the attack window - abilities have their own cooldowns. */
    ABILITY,

    /** Falls, fire, lava and the rest. No attacker. */
    ENVIRONMENT,

    /** {@code /kill} and the void. Lethal without a formula and without attribution. */
    ADMIN
}
