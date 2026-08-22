package rpg.core.ability;

/**
 * The sixteen effect primitives an ability is composed of (FR-010).
 *
 * <p>A closed set, like {@link rpg.core.stats.Attribute}, and for a related reason: what an ability
 * does must be expressible in configuration, and a set that grows at runtime cannot be validated at
 * startup. A seventeenth primitive is one constant here plus one stateless application class.
 *
 * <p><b>There is no primitive for damage over time.</b> Any effect with an interval applies
 * repeatedly over its duration - {@link #DAMAGE} with one is a DoT, {@link #MANA_RESTORE} with one
 * is the mana potion. Four abilities need that, and four primitives for it would have been four ways
 * to do the same thing (ADR-025).
 */
public enum EffectType {

    /**
     * Damage through {@link rpg.core.combat.CombatPipeline#abilityDamage} - never around it.
     *
     * <p>The amount is a <b>factor</b> on the caster's damage attribute, not an absolute number.
     * That is the whole reason an ability scales with level and equipment without reading a single
     * attribute itself (FR-013).
     */
    DAMAGE,

    /** Raises health, clamped at the maximum. A surplus is lost silently, not an error. */
    HEAL,

    /** Raises mana, clamped at the maximum. */
    MANA_RESTORE,

    /**
     * Heals a share of the damage the caster actually dealt.
     *
     * <p><b>After mitigation</b>, which is why it hangs on the application stage rather than earlier:
     * before mitigation stands an amount the target never took, and a warrior against an armoured
     * target would heal more than he deals (FR-016).
     */
    LIFESTEAL,

    /**
     * Absorbs damage before health does, ending on expiry or when used up (FR-015).
     *
     * <p>Carries an optional damage-type filter: the warrior's Block takes physical damage only, the
     * mage's Magic Shield takes everything (FR-015a).
     */
    SHIELD,

    /** A timed modifier on one attribute of the caster, expiring by timestamp rather than by tick. */
    BUFF,

    /** The same on a hostile target. */
    DEBUFF,

    /** A vanilla status effect for a duration - carries slow fall and slowness. */
    STATUS_EFFECT,

    /** An impulse in the caster's view direction. */
    DASH,

    /** An impulse away from the caster. */
    KNOCKBACK,

    /** Instant reposition; the range comes from the {@link TargetSpec}. */
    TELEPORT,

    /**
     * A launched carrier that applies the remaining effects on hit.
     *
     * <p>Carries the values from the moment it was launched, like B05's {@code projectileDamage}, and
     * still lands if the thrower is gone.
     */
    PROJECTILE,

    /**
     * A chance to avoid incoming damage entirely, with the same damage-type filter as {@link #SHIELD}.
     *
     * <p>Hangs on the modifiers stage, not the application stage: evasion has to <em>prevent</em> the
     * damage, and by application it has already landed. This is the mage's Magic Life (FR-016a).
     */
    EVADE,

    /**
     * A counter from 0 to 100 that rises with damage dealt or taken, falls after an idle window, and
     * scales an attribute by its level. This is the warrior's Rage (FR-016b).
     *
     * <p><b>It looks like a third resource next to health and mana and is not one.</b> It is not
     * stored, it does not survive logout, and its value follows from the last reading plus elapsed
     * time - so it costs no task and no table. The attribute contribution is refreshed on every
     * damage event, which is the only moment it matters anyway.
     */
    METER,

    /**
     * A creature with the caster's values that does not attack and fires an effect at its position
     * when it expires or drops to zero health. This is the rogue's Clone.
     *
     * <p><b>The aggro redirection is an empty hook until B10.</b> Mobs will not prefer it yet, because
     * mob AI is not this block's to write (ADR-025, workflow rule 5).
     */
    SUMMON,

    /**
     * Invisible and invulnerable for a duration, ending early when the caster deals damage.
     *
     * <p><b>Incomplete until B10.</b> The vanilla invisibility effect and the invulnerability work
     * now; that mobs turn away and that bosses still see him do not. The void stays lethal either
     * way (FR-016d).
     */
    INVISIBILITY,

    /**
     * A second jump in mid-air, followed by a slowed fall - the mage's Rise &amp; Fall.
     *
     * <p><b>A primitive rather than a hardcoded ability id.</b> The platform listener has to know
     * which character may double jump, and the only honest way to ask that without naming
     * {@code mage.rise-and-fall} in code is for the ability to say so itself. Naming the id would put
     * one piece of content into the source and quietly break the promise that abilities are
     * configuration (SC-001).
     *
     * <p>Unlike every other primitive this one is not <em>applied</em> - it is a capability the
     * platform reads. It therefore has no application class, and the dispatcher skipping it is
     * correct rather than a gap.
     */
    DOUBLE_JUMP
}
