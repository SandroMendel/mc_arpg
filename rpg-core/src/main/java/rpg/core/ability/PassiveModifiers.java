package rpg.core.ability;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import rpg.core.stats.ModifierSet;
import rpg.core.stats.SourceId;
import rpg.core.stats.SourceKind;
import rpg.core.stats.StatEngine;
import rpg.core.stats.StatModifier;

/**
 * The {@code ALWAYS} trigger: a passive that simply is (FR-052).
 *
 * <p>The only trigger with no hook at all. It registers a modifier set once, when the character
 * enters play or unlocks the ability, and removes it when the character leaves - there is no event
 * to react to, because "always" is the absence of one.
 *
 * <p><b>One source per ability, not one per character.</b> {@link SourceId} carries the ability id,
 * so a later ability replaces its own contribution rather than adding a second - the same reason B04
 * keys contributions by source at all. Registering both would double the buff and nothing would look
 * wrong.
 */
public final class PassiveModifiers {

    private final AbilityRegistry registry;
    private final StatEngine stats;

    public PassiveModifiers(AbilityRegistry registry, StatEngine stats) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.stats = Objects.requireNonNull(stats, "stats");
    }

    /**
     * Applies every unlocked {@code ALWAYS} passive of this character.
     *
     * <p>Called on entering play and after an unlock. Recomputing the whole set rather than patching
     * it means a missed event cannot leave a stale modifier in place for the rest of a session - the
     * same argument the hotbar makes for rebuilding rather than patching.
     */
    public void refresh(UUID characterId) {
        Objects.requireNonNull(characterId, "characterId");
        for (Ability ability : registry.unlockedFor(characterId)) {
            if (ability.isActive() || ability.trigger() != AbilityTrigger.ALWAYS) {
                continue;
            }
            SourceId source = sourceOf(ability);
            if (isOff(characterId, ability)) {
                // A player who turned it off means it, and the contribution has to go rather than
                // merely stop being refreshed (FR-052d).
                stats.remove(characterId, source);
                continue;
            }
            stats.apply(characterId, new ModifierSet(source, modifiersOf(characterId, ability)));
        }
    }

    /** Removes every contribution of this block. On logout and on a character switch. */
    public void clear(UUID characterId) {
        Objects.requireNonNull(characterId, "characterId");
        // A character whose class is already gone has nothing registered either - the release path
        // can run in either order, and asking for the loadout of a null class would throw here rather
        // than in the block that actually made the mistake.
        registry.classOf(characterId)
                .ifPresent(
                        id -> {
                            for (Ability ability : registry.abilitiesOf(id)) {
                                stats.remove(characterId, sourceOf(ability));
                            }
                        });
    }

    private boolean isOff(UUID characterId, Ability ability) {
        return ability.playerToggle()
                && registry.stateOf(characterId, ability.id()).effectiveToggle() == ToggleState.OFF;
    }

    private List<StatModifier> modifiersOf(UUID characterId, Ability ability) {
        int rank = registry.stateOf(characterId, ability.id()).rank();
        List<StatModifier> modifiers = new ArrayList<>(ability.effects().size());
        for (EffectSpec spec : ability.effects()) {
            if (spec.type() != EffectType.BUFF || spec.attribute() == null) {
                // Only a buff turns into a permanent contribution. Anything else on an ALWAYS passive
                // is either handled elsewhere or does nothing - and doing nothing quietly beats
                // inventing a modifier out of a primitive that is not one.
                continue;
            }
            modifiers.add(StatModifier.flat(spec.attribute(), spec.valueAtRank(rank)));
        }
        return modifiers;
    }

    private static SourceId sourceOf(Ability ability) {
        return SourceId.of(SourceKind.BUFF, "ability:" + ability.id());
    }
}
