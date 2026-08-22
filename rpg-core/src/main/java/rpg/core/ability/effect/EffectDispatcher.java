package rpg.core.ability.effect;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import rpg.core.ability.Ability;
import rpg.core.ability.EffectSpec;
import rpg.core.ability.EffectType;
import rpg.core.stats.StatSnapshot;

/**
 * Runs the effects of an ability, one after the other, and keeps a failure from spreading.
 *
 * <p><b>The barrier is here and nowhere else</b> (FR-017). An exception out of one application is
 * caught, logged with the ability's id and the primitive, and confined to that one event; the
 * remaining effects of the same ability still run. The same barrier B01 uses for modules, B04 for
 * base stat contributors and B05 for interceptors - and putting it here rather than in each
 * primitive means it exists once instead of sixteen times.
 *
 * <p>A primitive that is not registered is not an error either. Until every one of the sixteen is
 * built, an ability naming one that is missing simply does nothing for that effect - and says so in
 * the log, once, rather than refusing the whole ability.
 */
public final class EffectDispatcher {

    private final Map<EffectType, AbilityEffect> effects = new EnumMap<>(EffectType.class);
    private final Logger logger;

    public EffectDispatcher(Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /** Installs the application for one primitive. At startup, not during play. */
    public void register(EffectType type, AbilityEffect effect) {
        effects.put(
                Objects.requireNonNull(type, "type"), Objects.requireNonNull(effect, "effect"));
    }

    /** Whether a primitive has an application yet. */
    public boolean supports(EffectType type) {
        return effects.containsKey(type);
    }

    /**
     * Applies one prepared effect, behind the same barrier as the rest.
     *
     * <p>For {@link IntervalEffectRunner}, which resolves stacking into a single value before handing
     * it over - a primitive applies an amount and should not have to know that stacking exists.
     */
    public void runOne(
            Ability ability,
            EffectSpec spec,
            UUID casterId,
            List<UUID> targets,
            int rank,
            StatSnapshot snapshot) {
        AbilityEffect effect = effects.get(spec.type());
        if (effect == null) {
            return;
        }
        try {
            effect.apply(new EffectContext(ability, spec, casterId, targets, rank, snapshot, null));
        } catch (RuntimeException failure) {
            logger.log(
                    Level.WARNING,
                    "[abilities] " + ability.id() + ": interval effect " + spec.type() + " failed",
                    failure);
        }
    }

    /**
     * Applies every effect of {@code ability} to {@code targets}.
     *
     * @param snapshot the caster's values as of the trigger, held for all of them (FR-018)
     */
    public void run(
            Ability ability, UUID casterId, List<UUID> targets, int rank, StatSnapshot snapshot) {
        run(ability, casterId, targets, rank, snapshot, null);
    }

    /**
     * The same, for a passive fired by an event.
     *
     * @param data what the trigger brought - the amount that landed, and a way to refuse it. Lifesteal
     *     and evasion cannot work without it; every other primitive ignores it.
     */
    public void run(
            Ability ability,
            UUID casterId,
            List<UUID> targets,
            int rank,
            StatSnapshot snapshot,
            EffectContext.TriggerData data) {
        for (EffectSpec spec : ability.effects()) {
            AbilityEffect effect = effects.get(spec.type());
            if (effect == null) {
                logger.fine(
                        () ->
                                "[abilities] "
                                        + ability.id()
                                        + ": no application for "
                                        + spec.type()
                                        + " yet - skipped");
                continue;
            }
            try {
                effect.apply(
                        new EffectContext(ability, spec, casterId, targets, rank, snapshot, data));
            } catch (RuntimeException failure) {
                // Confined to this one effect of this one trigger. The player keeps playing, the
                // remaining effects still run, and the log names what to look at.
                logger.log(
                        Level.WARNING,
                        "[abilities] "
                                + ability.id()
                                + ": effect "
                                + spec.type()
                                + " failed and was contained",
                        failure);
            }
        }
    }
}
