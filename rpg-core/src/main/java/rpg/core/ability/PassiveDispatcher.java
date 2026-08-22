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

    /** Whether a hit came from behind - the rogue's Sneaky Backstab (FR-052a). */
    private volatile BehindTargetCheck behindTarget = BehindTargetCheck.never();

    /** Whether the holder is somewhere the ability works at all (FR-052b). */
    private volatile WorldCondition worldCondition = WorldCondition.everywhere();

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
     * Installs the positional check. The platform does this at startup; without it no ability with
     * {@code requires-behind-target} ever fires, which is the safe direction.
     */
    public void setBehindTargetCheck(BehindTargetCheck check) {
        this.behindTarget = Objects.requireNonNull(check, "check");
    }

    /**
     * Installs the world check.
     *
     * <p><b>Until B09 exists nothing calls this</b>, and the default lets everything through: Second
     * Life works in an instance too, which is wrong but harmless, where the opposite default would
     * silently disable it everywhere (FR-052b, ADR-025).
     */
    public void setWorldCondition(WorldCondition condition) {
        this.worldCondition = Objects.requireNonNull(condition, "condition");
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
            if (ability.isActive() || !ability.firesOn(trigger)) {
                continue;
            }
            if (!takesHold(characterId, ability, damageType, now)) {
                continue;
            }
            if (!conditionsMet(characterId, ability, data)) {
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
     * The two conditions that depend on where the holder is standing rather than on the ability's own
     * state.
     *
     * <p>Separate from {@code takesHold} because they are the expensive half: both reach into the
     * world, where a toggle, a cooldown and a random number are field reads. Which of the two runs
     * first does not change the outcome - nothing before the cooldown in {@code fire} has a side
     * effect, so a rejection here costs exactly as much as a rejection there.
     */
    private boolean conditionsMet(
            UUID characterId, Ability ability, EffectContext.TriggerData data) {
        if (ability.requiresBehindTarget()) {
            UUID counterpart = data == null ? null : data.counterpart();
            if (counterpart == null || !behindTarget.test(characterId, counterpart)) {
                return false;
            }
        }
        // Read but not enforced while B09 is missing - the default condition says yes to everything.
        return !ability.openWorldOnly() || worldCondition.isOpenWorld(characterId);
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
