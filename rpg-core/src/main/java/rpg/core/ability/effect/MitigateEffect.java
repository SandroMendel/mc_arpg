package rpg.core.ability.effect;

/**
 * Takes a share off incoming damage and lets the rest through (FR-016a).
 *
 * <p><b>Softens, not refuses.</b> Where {@link EvadeEffect} turns a blow away whole and leaves the
 * next one untouched, this one answers every hit it is filtered onto and always leaves something
 * standing. That difference is the whole reason the primitive exists: a mitigation is felt
 * constantly and an evasion is felt rarely and hugely, and the same number means something else in
 * each.
 *
 * <p>Hangs on the modifier stage for the reason evasion does - past it the damage has landed.
 *
 * <p><b>Which hits it answers is not decided here.</b> The damage-type and origin filters sit on the
 * spec and {@code PassiveDispatcher} has already honoured both; by the time this runs, the hit has
 * been established as one this ability cares about. The mage's Magic Life filters on origin alone,
 * so a sword and a skeleton's arrow are both softened and a cast fireball is not.
 *
 * <p>The share comes from {@link EffectContext#value()} and therefore from the rank - which is what
 * makes ranking the ability the thing that widens the band.
 */
public final class MitigateEffect implements AbilityEffect {

    @Override
    public void apply(EffectContext context) {
        context.reduceTrigger(context.value());
    }
}
