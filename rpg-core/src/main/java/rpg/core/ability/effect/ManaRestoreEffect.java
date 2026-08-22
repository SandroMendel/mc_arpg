package rpg.core.ability.effect;

import java.util.Objects;
import java.util.UUID;

import rpg.core.stats.StatEngine;

/**
 * Raises mana, clamped at the maximum. The mirror of {@link HealEffect}, and the mage's Arcane
 * Gathering on a kill.
 */
public final class ManaRestoreEffect implements AbilityEffect {

    private final StatEngine stats;

    public ManaRestoreEffect(StatEngine stats) {
        this.stats = Objects.requireNonNull(stats, "stats");
    }

    @Override
    public void apply(EffectContext context) {
        double amount = context.value();
        if (amount <= 0.0) {
            return;
        }
        for (UUID target : context.targets()) {
            stats.changeMana(target, amount);
        }
    }
}
