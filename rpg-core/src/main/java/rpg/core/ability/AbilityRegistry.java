package rpg.core.ability;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import rpg.core.classes.AbilityBinding;
import rpg.core.session.CharacterClass;

/**
 * The public entry point of B08 for other blocks - see {@code contracts/ability-api.md}.
 *
 * <p><b>Nothing here computes</b> (FR-067). Every answer is either read from an immutable definition,
 * derived from the character's level, or a comparison of two timestamps. That is the promise B13
 * relies on: a HUD asks often, and asking must cost nothing.
 *
 * <p>Changing anything on this class is ADR-worthy from now on - the same rule
 * {@code CombatPipeline} and {@code StatEngine} set for themselves.
 */
public final class AbilityRegistry {

    private final AbilityConfig config;
    private final Function<UUID, CharacterClass> classOf;
    private final Function<CharacterClass, List<AbilityBinding>> bindingsOf;
    private final Function<UUID, List<AbilityBinding>> unlockedOf;
    private final Clock clock;

    /** What each character owns per ability. Authoritative while it is online (Principle IV). */
    private final Map<UUID, Map<String, AbilityState>> states = new ConcurrentHashMap<>();

    /** When the global lock of a character expires. A timestamp, never a countdown (FR-026). */
    private final Map<UUID, Instant> globalLock = new ConcurrentHashMap<>();

    /**
     * @param classOf the character's class, supplied by B07
     * @param bindingsOf the class loadout, supplied by B07
     * @param unlockedOf what the character has unlocked - derived from the level by B07, never stored
     *     here (FR-061)
     */
    public AbilityRegistry(
            AbilityConfig config,
            Function<UUID, CharacterClass> classOf,
            Function<CharacterClass, List<AbilityBinding>> bindingsOf,
            Function<UUID, List<AbilityBinding>> unlockedOf,
            Clock clock) {
        this.config = Objects.requireNonNull(config, "config");
        this.classOf = Objects.requireNonNull(classOf, "classOf");
        this.bindingsOf = Objects.requireNonNull(bindingsOf, "bindingsOf");
        this.unlockedOf = Objects.requireNonNull(unlockedOf, "unlockedOf");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    // ---- reading ----------------------------------------------------------------------------------

    /** The abilities of a class, whatever the level. */
    public List<Ability> abilitiesOf(CharacterClass id) {
        return resolve(bindingsOf.apply(id));
    }

    /** The definition behind an id, or empty. */
    public Optional<Ability> find(String abilityId) {
        return config.find(abilityId);
    }

    /** What this character has unlocked right now - derived from the level (FR-061). */
    public List<Ability> unlockedFor(UUID characterId) {
        Objects.requireNonNull(characterId, "characterId");
        return resolve(unlockedOf.apply(characterId));
    }

    /** The rank this character has on this ability. 1 when it was never raised. */
    public int rankOf(UUID characterId, String abilityId) {
        return stateOf(characterId, abilityId).rank();
    }

    /** What is left of the cooldown, or empty when the ability is ready. */
    public Optional<Duration> remainingCooldown(UUID characterId, String abilityId) {
        Instant now = clock.instant();
        return stateOf(characterId, abilityId)
                .runningCooldown(now)
                .map(until -> Duration.between(now, until));
    }

    /** What is left of the global lock, or empty. */
    public Optional<Duration> remainingGlobalLock(UUID characterId) {
        Instant until = globalLock.get(characterId);
        if (until == null) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        return until.isAfter(now) ? Optional.of(Duration.between(now, until)) : Optional.empty();
    }

    /** The player's setting on an ability, or {@link ToggleState#ON} when never changed. */
    public ToggleState toggleOf(UUID characterId, String abilityId) {
        return stateOf(characterId, abilityId).effectiveToggle();
    }

    /**
     * The unlocked, switched-on ability granting a capability, or empty.
     *
     * <p>How the platform asks "may this character double jump" without naming an ability id in code.
     * A capability is a primitive that is read rather than applied - see {@link EffectType#DOUBLE_JUMP}.
     */
    public Optional<Ability> capability(UUID characterId, EffectType capability) {
        for (Ability ability : unlockedFor(characterId)) {
            boolean grants =
                    ability.effects().stream().anyMatch(spec -> spec.type() == capability);
            if (!grants) {
                continue;
            }
            if (ability.playerToggle()
                    && stateOf(characterId, ability.id()).effectiveToggle() == ToggleState.OFF) {
                continue;
            }
            return Optional.of(ability);
        }
        return Optional.empty();
    }

    /** The class of a character, as B07 knows it. */
    public Optional<CharacterClass> classOf(UUID characterId) {
        return Optional.ofNullable(classOf.apply(characterId));
    }

    public AbilityConfig config() {
        return config;
    }

    // ---- state, for the runtime and the flush -----------------------------------------------------

    /**
     * The stored state of one ability, or the untouched default.
     *
     * <p>Never {@code null} and never absent: an ability nobody has touched is rank 1 with no cooldown
     * and no toggle, and materialising that is cheaper than making every caller handle empty.
     */
    public AbilityState stateOf(UUID characterId, String abilityId) {
        Objects.requireNonNull(characterId, "characterId");
        Objects.requireNonNull(abilityId, "abilityId");
        Map<String, AbilityState> owned = states.get(characterId);
        AbilityState state = owned == null ? null : owned.get(abilityId);
        return state == null ? AbilityState.initial(characterId, abilityId) : state;
    }

    /** Replaces one ability's state. The runtime calls this; nothing else should. */
    public void put(AbilityState state) {
        Objects.requireNonNull(state, "state");
        states.computeIfAbsent(state.characterId(), id -> new ConcurrentHashMap<>())
                .put(state.abilityId(), state);
    }

    /** Everything currently held for a character - what the flush reads (FR-032). */
    public List<AbilityState> statesOf(UUID characterId) {
        Map<String, AbilityState> owned = states.get(characterId);
        return owned == null ? List.of() : List.copyOf(owned.values());
    }

    /** Installs what was loaded from the database when a character is activated. */
    public void restore(UUID characterId, List<AbilityState> loaded) {
        Objects.requireNonNull(characterId, "characterId");
        Map<String, AbilityState> owned = new ConcurrentHashMap<>();
        loaded.forEach(state -> owned.put(state.abilityId(), state));
        states.put(characterId, owned);
    }

    /** Sets the global lock. Called when an ability is triggered (FR-029). */
    public void lockGlobally(UUID characterId, Instant until) {
        globalLock.put(characterId, until);
    }

    /** Drops everything held for a character - on logout or on a character switch. */
    public void forget(UUID characterId) {
        states.remove(characterId);
        globalLock.remove(characterId);
    }

    /** How many characters currently hold state. For leak tests. */
    public int trackedCount() {
        return states.size();
    }

    private List<Ability> resolve(List<AbilityBinding> bindings) {
        List<Ability> resolved = new ArrayList<>(bindings.size());
        for (AbilityBinding binding : bindings) {
            resolved.add(config.require(binding.abilityId()));
        }
        return List.copyOf(resolved);
    }
}
