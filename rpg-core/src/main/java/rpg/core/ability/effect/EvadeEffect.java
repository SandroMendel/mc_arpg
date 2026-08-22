package rpg.core.ability.effect;

/**
 * Refuses incoming damage outright (FR-016a).
 *
 * <p><b>Refuses, not undoes.</b> This hangs on the modifier stage, where the damage can still be
 * turned away; by the application stage it has landed and the only thing left would be healing it
 * back, which is a different mechanic with different numbers.
 *
 * <p>The probability is not checked here. {@code PassiveDispatcher} rolls it once per trigger, before
 * any effect runs - otherwise an ability with two effects would roll twice and half-succeed.
 *
 * <p>Whether it applies to physical or magic damage is the type filter on the spec, and the
 * dispatcher has already honoured it. The mage's Magic Life avoids magic only; against a sword it
 * does nothing at all.
 */
public final class EvadeEffect implements AbilityEffect {

    @Override
    public void apply(EffectContext context) {
        context.cancelTrigger();
    }
}
