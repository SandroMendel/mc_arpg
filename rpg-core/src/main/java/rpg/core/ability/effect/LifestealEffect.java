package rpg.core.ability.effect;

import java.util.Objects;

import rpg.core.stats.StatEngine;

/**
 * Heals a share of the damage the caster actually dealt (FR-016).
 *
 * <p><b>Actually dealt</b>, which is why this hangs on the application stage. Before mitigation
 * stands a number the target never took, and a warrior swinging at a heavily armoured enemy would
 * heal for more than he inflicted - a bug that only shows up against high-defence targets, which is
 * to say late.
 *
 * <p><b>No new attribute.</b> ADR-008 holds secondary values back, and ADR-022 kept lifesteal on this
 * side of that line: the percentage comes from the ability's rank, not from a stat. Adding a ninth
 * attribute would have opened the stat engine, persistence and the HUD at once, and with them the
 * door for crit and resistances.
 *
 * <p>Overhealing is silently discarded - {@code changeHealth} clamps, and a full-health warrior
 * hitting something is the normal case, not an error.
 */
public final class LifestealEffect implements AbilityEffect {

    private final StatEngine stats;

    public LifestealEffect(StatEngine stats) {
        this.stats = Objects.requireNonNull(stats, "stats");
    }

    @Override
    public void apply(EffectContext context) {
        double dealt = context.triggerDamage();
        if (dealt <= 0.0) {
            return;
        }
        stats.changeHealth(context.casterId(), dealt * context.value());
    }
}
