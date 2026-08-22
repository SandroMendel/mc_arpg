package rpg.core.ability.effect;

import java.util.Objects;
import java.util.UUID;

import rpg.core.stats.StatEngine;

/**
 * Raises health, clamped at the maximum.
 *
 * <p>A surplus is discarded silently rather than reported: healing at full health is what happens
 * whenever a fight goes well, and treating it as an error would put a warning in the log on every
 * quiet moment.
 */
public final class HealEffect implements AbilityEffect {

    private final StatEngine stats;

    public HealEffect(StatEngine stats) {
        this.stats = Objects.requireNonNull(stats, "stats");
    }

    @Override
    public void apply(EffectContext context) {
        double amount = context.value();
        if (amount <= 0.0) {
            return;
        }
        for (UUID target : context.targets()) {
            // "A share of maximum health" is what Second Life is written as, and a share is a
            // different thing from an amount: 0.35 health would be a rounding error where 35% of a
            // warrior's 1997 is a rescue. The flag says which was meant, rather than a rule that
            // guesses from the size of the number.
            double healed =
                    context.spec().asFraction()
                            ? amount * stats.resources(target).maxHealth()
                            : amount;
            stats.changeHealth(target, healed);
        }
    }
}
