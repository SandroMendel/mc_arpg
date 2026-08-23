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
import rpg.core.combat.DamageOrigin;
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
     *     filter on {@code SHIELD}, {@code EVADE} and {@code MITIGATE}
     * @param data what the trigger brought: the amount, and a way to refuse it
     * @return whether any of them took hold, which the damage interceptors use to decide whether the
     *     event still stands
     */
    public boolean fire(
            UUID holderId,
            AbilityTrigger trigger,
            DamageType damageType,
            EffectContext.TriggerData data) {
        return fire(holderId, trigger, damageType, null, data);
    }

    /**
     * The same, for a trigger that also knows <b>where</b> the damage came from.
     *
     * <p>Separate from the damage type and not derivable from it: a fireball and a sword swing can
     * both be physical, and an ability answering auto-attacks has to tell them apart. The three
     * damage hooks all know it; {@code ON_KILL} has no damage event behind it and passes {@code
     * null}.
     *
     * @param origin the origin behind the event, or {@code null} when there is none. An ability that
     *     filters on origin fires on nothing when it is absent, which is the safe direction: better
     *     silent than answering a fall the same as a sword
     */
    public boolean fire(
            UUID holderId,
            AbilityTrigger trigger,
            DamageType damageType,
            DamageOrigin origin,
            EffectContext.TriggerData data) {
        Objects.requireNonNull(holderId, "holderId");
        Objects.requireNonNull(trigger, "trigger");

        // A HOLDER comes in, because that is what the damage pipeline deals in - it has to, since the
        // same pipeline carries mobs, and a mob has no character. The registry below is keyed by
        // character (ADR-011), so the translation happens once, here, and both ids are then used for
        // what they actually name.
        //
        // This used to be missing, and the holder id went straight into unlockedFor. That never
        // threw: it simply returned an empty list, so every passive in the game silently did nothing.
        UUID characterId = stats.characterIdOf(holderId).orElse(null);
        if (characterId == null) {
            // A mob. It takes damage and deals it, and it has no passives to fire.
            return false;
        }

        Instant now = clock.instant();
        boolean anyFired = false;

        for (Ability ability : registry.unlockedFor(characterId)) {
            if (ability.isActive() || !ability.firesOn(trigger)) {
                continue;
            }
            if (!takesHold(characterId, ability, damageType, origin, now)) {
                continue;
            }
            if (!conditionsMet(holderId, ability, data)) {
                continue;
            }
            run(holderId, characterId, ability, data);
            startCooldown(characterId, ability, now);
            anyFired = true;
        }
        return anyFired;
    }

    /** Whether this passive is switched on, off cooldown, matches the hit and won its roll. */
    private boolean takesHold(
            UUID characterId,
            Ability ability,
            DamageType damageType,
            DamageOrigin origin,
            Instant now) {
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
        if (!matchesHit(ability, damageType, origin)) {
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
            UUID holderId, Ability ability, EffectContext.TriggerData data) {
        // Beide Fragen gehen an die WELT, nicht an gespeicherten Zustand: die eine schlaegt zwei
        // Entitaeten nach, die andere den Ort, an dem jemand steht. Eine Entitaet wird mit der
        // Halter-Id adressiert - mit der Charakter-Id fand der Hinterhalt nie einen Angreifer und
        // war damit lautlos wirkungslos.
        if (ability.requiresBehindTarget()) {
            UUID counterpart = data == null ? null : data.counterpart();
            if (counterpart == null || !behindTarget.test(holderId, counterpart)) {
                return false;
            }
        }
        // Read but not enforced while B09 is missing - the default condition says yes to everything.
        return !ability.openWorldOnly() || worldCondition.isOpenWorld(holderId);
    }

    /**
     * Whether the ability's filtered effects care about a hit of this kind, from there.
     *
     * <p>Only {@code SHIELD}, {@code EVADE} and {@code MITIGATE} carry filters, and an absent filter
     * means "any". The warrior's Block takes physical damage only; the mage's Magic Life filters the
     * other way round - any damage type, but only from an auto-attack.
     *
     * <p><b>One dissenting effect refuses the whole ability</b>, which is why the filters sit on the
     * effect and are asked here rather than in each primitive: the roll, the cooldown and the
     * conditions belong to the ability, and half of an ability firing would spend all three for a
     * fraction of the effect.
     */
    private boolean matchesHit(Ability ability, DamageType damageType, DamageOrigin origin) {
        for (EffectSpec spec : ability.effects()) {
            if (!spec.matches(damageType, origin)) {
                return false;
            }
        }
        return true;
    }

    private void run(
            UUID holderId, UUID characterId, Ability ability, EffectContext.TriggerData data) {
        int rank = registry.stateOf(characterId, ability.id()).rank();
        // A passive acts on its holder. Nothing here resolves targets: the event already decided who
        // is involved, and asking the resolver would find a second, unrelated set.
        //
        // The rank comes from the CHARACTER, everything below addresses the HOLDER. Two ids, two
        // jobs - the line above is what the character earned, the line below is who it happens to.
        // WEN es trifft, steht in der Konfiguration - und bis hierher wurde sie ignoriert.
        //
        // Fast jede Passive wirkt auf ihren Traeger: Raserei, Lebensraub, Schilde, Ausweichen. Eine
        // nicht: die Vergiftete Klinge vergiftet, was der Rogue trifft, und sie sagt das auch
        // (target mode NEAREST). Weil hier pauschal der Traeger eingesetzt wurde, vergiftete sie
        // ihren eigenen Besitzer.
        //
        // Aufgeloest wird NICHT ueber den Resolver - das Ereignis hat den Gegenpart schon benannt,
        // und ein zweiter Suchlauf faende einen anderen. Faellt der Gegenpart weg, bleibt der
        // Traeger: besser auf dem Falschen als gar nicht, und ohne Gegenpart gibt es kein Ziel.
        boolean onSelf = ability.target() == null || ability.target().mode() == TargetMode.SELF;
        UUID victim =
                onSelf || data == null || data.counterpart() == null
                        ? holderId
                        : data.counterpart();
        List<UUID> targets = List.of(victim);
        effects.run(ability, holderId, targets, rank, stats.snapshot(holderId), data);
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
