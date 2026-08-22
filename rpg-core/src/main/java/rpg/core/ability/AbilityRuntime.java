package rpg.core.ability;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import rpg.core.ability.effect.EffectDispatcher;
import rpg.core.stats.Attribute;
import rpg.core.stats.ResourceView;
import rpg.core.stats.StatEngine;
import rpg.core.stats.StatSnapshot;

/**
 * Triggering an ability: the checks, the cost, the cooldowns and the effects (FR-024 to FR-030).
 *
 * <p><b>Nothing here counts down.</b> A cooldown is two timestamps compared when somebody asks, and
 * the global lock is one more. That is what keeps Principle II true at 150 players: no recurring task
 * per player exists, because none is needed.
 */
public final class AbilityRuntime {

    /** The hard cap on cooldown reduction from ADR-008. Not configurable, by decision. */
    public static final double MAX_COOLDOWN_REDUCTION = 0.40;

    private final AbilityRegistry registry;
    private final StatEngine stats;
    private final TargetResolver targets;
    private final EffectDispatcher effects;
    private final AbilityStateRepository repository;
    private final Clock clock;

    public AbilityRuntime(
            AbilityRegistry registry,
            StatEngine stats,
            TargetResolver targets,
            EffectDispatcher effects,
            AbilityStateRepository repository,
            Clock clock) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.stats = Objects.requireNonNull(stats, "stats");
        this.targets = Objects.requireNonNull(targets, "targets");
        this.effects = Objects.requireNonNull(effects, "effects");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Tries to trigger an ability.
     *
     * <p><b>The order of the checks is the contract</b> (contracts/ability-api.md): character active,
     * ability unlocked, global lock free, own cooldown free, mana sufficient. The first violated
     * condition decides, and <b>none of them consumes anything</b> - a refused trigger costs no mana,
     * starts no cooldown and does not take the global lock (FR-024, FR-025).
     */
    public AbilityResult trigger(UUID characterId, String abilityId) {
        Objects.requireNonNull(characterId, "characterId");
        Objects.requireNonNull(abilityId, "abilityId");

        Ability ability = registry.config().require(abilityId);
        Instant now = clock.instant();

        if (registry.classOf(characterId).isEmpty()) {
            // Before the class selection there is no game state at all (ADR-020, ADR-021). Checked
            // first because everything below it would be answering about a character that is not
            // playing.
            return AbilityResult.NO_CHARACTER;
        }
        if (!ability.isActive()) {
            // A passive is not triggered by the player. Reaching here means an item carried a passive
            // id, which the hotbar never builds - so this is a guard, not a player-facing case.
            return AbilityResult.NOT_UNLOCKED;
        }
        if (!isUnlocked(characterId, abilityId)) {
            return AbilityResult.NOT_UNLOCKED;
        }
        if (isGloballyLocked(characterId, now)) {
            return AbilityResult.GLOBAL_LOCK;
        }

        AbilityState state = registry.stateOf(characterId, abilityId);
        if (state.runningCooldown(now).isPresent()) {
            return AbilityResult.ON_COOLDOWN;
        }

        ResourceView resources = stats.resources(characterId);
        if (resources.currentMana() < ability.manaCost()) {
            return AbilityResult.NOT_ENOUGH_MANA;
        }

        // Past every gate. From here on things are consumed, and in this order: the global lock takes
        // hold at the START of a trigger (FR-029), so an ability with a cast time cannot be used to
        // slip past it.
        registry.lockGlobally(characterId, now.plus(registry.config().globalCooldown()));
        if (ability.manaCost() > 0.0) {
            stats.changeMana(characterId, -ability.manaCost());
        }

        apply(ability, characterId, state.rank());
        startCooldown(characterId, ability, now);
        return AbilityResult.TRIGGERED;
    }

    /** Whether the character's level has reached this ability's unlock level (FR-061). */
    public boolean isUnlocked(UUID characterId, String abilityId) {
        return registry.unlockedFor(characterId).stream()
                .anyMatch(unlocked -> unlocked.id().equals(abilityId));
    }

    /** Whether the short lock after another ability is still running (FR-028). */
    public boolean isGloballyLocked(UUID characterId, Instant now) {
        return registry.remainingGlobalLock(characterId).isPresent();
    }

    /**
     * The cooldown this character would get on this ability, after their reduction.
     *
     * <p>Capped at {@link #MAX_COOLDOWN_REDUCTION} even if the attribute somehow exceeds it. The
     * attribute is already capped in {@code stats.yml}; enforcing it a second time here is cheap and
     * means a configuration mistake cannot produce a zero cooldown (FR-027, ADR-008).
     */
    public Duration effectiveCooldown(UUID characterId, Ability ability) {
        if (ability.cooldown().isZero()) {
            return Duration.ZERO;
        }
        double reduction =
                Math.min(
                        MAX_COOLDOWN_REDUCTION,
                        Math.max(0.0, stats.value(characterId, Attribute.ABILITY_COOLDOWN)));
        long millis = Math.round(ability.cooldown().toMillis() * (1.0 - reduction));
        return Duration.ofMillis(millis);
    }

    /**
     * Applies the effects with the values as of this moment.
     *
     * <p>The snapshot is drawn once here and handed to every effect (FR-018). Drawing it per effect
     * would let a buff expiring mid-ability change what the second half of it does.
     */
    private void apply(Ability ability, UUID casterId, int rank) {
        StatSnapshot snapshot = stats.snapshot(casterId);
        List<UUID> resolved = targets.resolve(casterId, ability.target());
        effects.run(ability, casterId, resolved, rank, snapshot);
    }

    /**
     * Starts the cooldown - at the moment the ability <b>takes effect</b> (FR-030).
     *
     * <p>Also the only write path: the state is marked and B02's write-behind buffer does the rest.
     * No game event reaches the database directly (FR-032).
     */
    private void startCooldown(UUID characterId, Ability ability, Instant now) {
        Duration cooldown = effectiveCooldown(characterId, ability);
        if (cooldown.isZero()) {
            return;
        }
        registry.put(registry.stateOf(characterId, ability.id()).withCooldown(now.plus(cooldown)));
        repository.markDirty(characterId);
    }
}
