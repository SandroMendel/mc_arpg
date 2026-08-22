package rpg.core.ability.effect;

import java.util.Objects;
import java.util.UUID;

import rpg.core.combat.CombatPipeline;

/**
 * Damage from an ability - through the regular pipeline, never around it (FR-012, FR-068).
 *
 * <p><b>The value is a factor, not an amount.</b> 1.4 means 140% of the caster's physical or magic
 * damage, and B05 works the rest out. That is why an ability scales with level and equipment without
 * reading a single attribute itself, and why a balancing pass on the ladders does not require
 * touching any ability (FR-013).
 *
 * <p>Not subject to the attack window: abilities have their own cooldowns, and checking both would
 * limit them twice. B05 decided that and says so in {@code abilityDamage}.
 */
public final class DamageEffect implements AbilityEffect {

    private final CombatPipeline pipeline;

    public DamageEffect(CombatPipeline pipeline) {
        this.pipeline = Objects.requireNonNull(pipeline, "pipeline");
    }

    @Override
    public void apply(EffectContext context) {
        double factor = context.value();
        for (UUID target : context.targets()) {
            // One call per target. The cap the resolver already applied is what keeps this bounded -
            // there is deliberately no second limit here, because two places enforcing one rule means
            // one of them is eventually wrong.
            pipeline.abilityDamage(context.casterId(), target, context.spec().damageType(), factor);
        }
    }
}
