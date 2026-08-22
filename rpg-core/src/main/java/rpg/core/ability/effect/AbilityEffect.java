package rpg.core.ability.effect;

/**
 * One effect primitive, applied.
 *
 * <p>Stateless and unaware of which ability it belongs to. That is what makes a new ability a
 * configuration entry rather than a class: sixteen small pieces that compose, instead of eighteen
 * classes that each do everything once.
 *
 * <p>An implementation does not catch its own failures - {@link EffectDispatcher} does, so the
 * barrier exists once instead of sixteen times (FR-017).
 */
@FunctionalInterface
public interface AbilityEffect {

    /** Applies this effect. Called on the tick, with the snapshot taken when the ability fired. */
    void apply(EffectContext context);
}
