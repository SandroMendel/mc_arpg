package rpg.core.ability;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.DoubleSupplier;

import rpg.core.ability.effect.EffectContext;
import rpg.core.ability.effect.EffectDispatcher;
import rpg.core.combat.DamageType;
import rpg.core.stats.StatEngine;

/**
 * Decides which passive abilities take hold on a given event, and runs them (FR-046 to FR-052).
 *
 * <p>Passives needed a dispatcher the moment the unique was allowed to be passive (ADR-022): "passive
 * means a permanent modifier" describes neither Second Life, which fires on death, nor Lifesteal,
 * which fires on dealing damage. So the trigger is the rule here and the permanent modifier is the
 * special case.
 *
 * <p><b>The probability is rolled once per trigger, before any effect runs</b> (FR-049). Rolling per
 * effect would let an ability with two of them half-succeed - poison applied but the slow not - which
 * is a state no player could make sense of and no test could pin down.
 */
public final class PassiveDispatcher {

    private final AbilityRegistry registry;
    private final EffectDispatcher effects;
    private final StatEngine stats;
    private final AbilityStateRepository repository;
    private final Clock clock;

    /** Injected so a test can nail it down; {@code Math::random} in production (FR-049). */
    private final DoubleSupplier random;

    public PassiveDispatcher(
            AbilityRegistry registry,
            EffectDispatcher effects,
            StatEngine stats,
            AbilityStateRepository repository,
            Clock clock,
            DoubleSupplier random) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.effects = Objects.requireNonNull(effects, "effects");
        this.stats = Objects.requireNonNull(stats, "stats");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.random = Objects.requireNonNull(random, "random");
    }

    /**
     * Fires every unlocked passive of this character that hangs on {@code trigger}.
     *
     * @param damageType the type behind the event, or {@code null} when there is none - used for the
     *     filter on {@code SHIELD} and {@code EVADE}
     * @param data what the trigger brought: the amount, and a way to refuse it
     * @return whether any of them took hold, which the damage interceptors use to decide whether the
     *     event still stands
     */
    public boolean fire(
            UUID characterId,
            AbilityTrigger trigger,
            DamageType damageType,
            EffectContext.TriggerData data) {
        Objects.requireNonNull(characterId, "characterId");
        Objects.requireNonNull(trigger, "trigger");

        Instant now = clock.instant();
        boolean anyFired = false;

        for (Ability ability : registry.unlockedFor(characterId)) {
            if (ability.isActive() || ability.trigger() != trigger) {
                continue;
            }
            if (!takesHold(characterId, ability, damageType, now)) {
                continue;
            }
            run(characterId, ability, data);
            startCooldown(characterId, ability, now);
            anyFired = true;
        }
        return anyFired;
    }

    /** Whether this passive is switched on, off cooldown, matches the type and won its roll. */
    private boolean takesHold(
            UUID characterId, Ability ability, DamageType damageType, Instant now) {
        AbilityState state = registry.stateOf(characterId, ability.id());

        // FR-052d. A player who turned it off means it, and that comes before everything else.
        if (ability.playerToggle() && state.effectiveToggle() == ToggleState.OFF) {
            return false;
        }
        // FR-048. A passive has no player behind its trigger, but it can still be rate-limited -
        // Second Life would otherwise make a rogue immortal.
        if (state.runningCooldown(now).isPresent()) {
            return false;
        }
        if (!matchesDamageType(ability, damageType)) {
            return false;
        }
        // FR-049. Once, here, for the whole ability.
        return ability.chance() >= 1.0 || random.getAsDouble() < ability.chance();
    }

    /**
     * Whether the ability's filtered effects care about this damage type.
     *
     * <p>Only {@code SHIELD} and {@code EVADE} carry a filter, and an absent one means "every kind".
     * The mage's Magic Life sets {@code MAGIC} and therefore does nothing against a sword.
     */
    private boolean matchesDamageType(Ability ability, DamageType damageType) {
        for (EffectSpec spec : ability.effects()) {
            DamageType filter = spec.damageType();
            boolean filterable =
                    spec.type() == EffectType.EVADE || spec.type() == EffectType.SHIELD;
            if (filterable && filter != null && filter != damageType) {
                return false;
            }
        }
        return true;
    }

    private void run(UUID characterId, Ability ability, EffectContext.TriggerData data) {
        int rank = registry.stateOf(characterId, ability.id()).rank();
        // A passive acts on its holder. Nothing here resolves targets: the event already decided who
        // is involved, and asking the resolver would find a second, unrelated set.
        List<UUID> self = List.of(characterId);
        effects.run(ability, characterId, self, rank, stats.snapshot(characterId), data);
    }

    private void startCooldown(UUID characterId, Ability ability, Instant now) {
        Duration cooldown = ability.cooldown();
        if (cooldown.isZero()) {
            return;
        }
        registry.put(registry.stateOf(characterId, ability.id()).withCooldown(now.plus(cooldown)));
        repository.markDirty(characterId);
    }
}
