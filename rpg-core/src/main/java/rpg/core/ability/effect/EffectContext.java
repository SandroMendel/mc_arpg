package rpg.core.ability.effect;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import rpg.core.ability.Ability;
import rpg.core.ability.EffectSpec;
import rpg.core.stats.StatSnapshot;

/**
 * Everything one effect application needs, gathered once when the ability was triggered.
 *
 * <p><b>The snapshot is taken at trigger time and held to the end</b> (FR-018). A buff that expires
 * between two effects of the same ability must not change what the second one does - the ability
 * acted on the values it saw, and half of it computing with different numbers would be a bug nobody
 * could reproduce.
 *
 * @param ability the definition, for the id in a log line
 * @param spec the effect being applied
 * @param casterId who triggered it
 * @param targets what it acts on, already capped and ordered by the resolver
 * @param rank the caster's rank on this ability, so the value can be scaled
 * @param snapshot the caster's values as of the trigger
 */
public record EffectContext(
        Ability ability,
        EffectSpec spec,
        UUID casterId,
        List<UUID> targets,
        int rank,
        StatSnapshot snapshot) {

    public EffectContext {
        Objects.requireNonNull(ability, "ability");
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(casterId, "casterId");
        targets = List.copyOf(Objects.requireNonNull(targets, "targets"));
        Objects.requireNonNull(snapshot, "snapshot");
        if (rank < 1) {
            throw new IllegalArgumentException("rank must be at least 1, but was " + rank);
        }
    }

    /** The effect's value at the caster's rank - one multiplication, no second definition. */
    public double value() {
        return spec.valueAtRank(rank);
    }
}
