package rpg.core.combat;

import rpg.core.stats.Attribute;

/**
 * What kind of damage this is (FR-002, FR-012b).
 *
 * <p>Decides two things at once: which attribute supplies the base, and whether defence applies.
 * Environment damage deliberately ignores defence - otherwise good armour would make falls and lava
 * completely irrelevant, and the progression would come from two mechanisms instead of one.
 */
public enum DamageType {

    /** Melee and projectile damage, based on the attacker's physical damage. */
    PHYSICAL(Attribute.PHYSICAL_DAMAGE, true),

    /** Ability damage, based on the attacker's magic damage. */
    MAGIC(Attribute.MAGIC_DAMAGE, true),

    /**
     * Falls, fire, lava and the rest. A fixed configured amount, and defence does not apply.
     *
     * <p>Fixed rather than proportional on purpose: a hazard should matter to a beginner with 100
     * health and become negligible to a geared player with 2000. A percentage would stay equally
     * dangerous forever, which is the opposite of the intent.
     */
    ENVIRONMENT(null, false);

    private final Attribute basis;
    private final boolean defenceApplies;

    DamageType(Attribute basis, boolean defenceApplies) {
        this.basis = basis;
        this.defenceApplies = defenceApplies;
    }

    /** The attacker attribute this type scales from, or {@code null} for environment damage. */
    public Attribute basis() {
        return basis;
    }

    /** Whether the target's defence reduces this damage. */
    public boolean defenceApplies() {
        return defenceApplies;
    }

    /** Whether this type needs an attacker at all. */
    public boolean requiresAttacker() {
        return basis != null;
    }
}
