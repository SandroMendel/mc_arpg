package rpg.core.ability.effect;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.DoubleConsumer;

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
        StatSnapshot snapshot,
        TriggerData trigger) {

    /**
     * What a passive's trigger brought with it, or {@code null} for an actively triggered ability.
     *
     * <p>Two effects need it and could not work without: lifesteal needs the amount that actually
     * landed, and evasion needs a way to refuse the damage rather than undo it afterwards.
     *
     * @param damage the amount at this point in the pipeline - after mitigation on the application
     *     stage, before it on the modifier stage
     * @param cancel refuses the damage event; only meaningful before it has been applied
     * @param reduce takes a <b>share</b> off the damage event and leaves the rest standing - what
     *     {@code cancel} cannot express. Given the same share twice it takes it twice, so two
     *     mitigations on one hit compound instead of the larger one winning; only meaningful before
     *     the damage has been applied
     * @param counterpart the other party in the event - the target when the holder dealt the damage,
     *     the attacker when they took it, and {@code null} when there is nobody (a fall, a kill with
     *     no attributable killer). The positional condition needs it and nothing else does
     */
    public record TriggerData(
            double damage, Runnable cancel, DoubleConsumer reduce, UUID counterpart) {

        public TriggerData {
            // A trigger that cannot be reduced is the normal case, not an error: only the
            // damage-taken hook sits on a stage where the number can still be changed. Defaulting
            // here rather than at each call site keeps a mitigation from needing a null check.
            reduce = reduce == null ? share -> {} : reduce;
        }

        /** Without a way to reduce - for hooks past the stage where the number can still change. */
        public TriggerData(double damage, Runnable cancel, UUID counterpart) {
            this(damage, cancel, null, counterpart);
        }

        /** Without a counterpart - for triggers where there is no second party. */
        public TriggerData(double damage, Runnable cancel) {
            this(damage, cancel, null, null);
        }
    }

    /** An actively triggered ability - no damage event behind it. */
    public EffectContext(
            Ability ability,
            EffectSpec spec,
            UUID casterId,
            List<UUID> targets,
            int rank,
            StatSnapshot snapshot) {
        this(ability, spec, casterId, targets, rank, snapshot, null);
    }

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

    /** The damage behind the trigger, or zero for an actively triggered ability. */
    public double triggerDamage() {
        return trigger == null ? 0.0 : trigger.damage();
    }

    /** Refuses the damage event behind the trigger. A no-op when there is none. */
    public void cancelTrigger() {
        if (trigger != null) {
            trigger.cancel().run();
        }
    }

    /**
     * Takes {@code share} off the damage event behind the trigger. A no-op when there is none.
     *
     * <p>Clamped here rather than trusted from the configuration: a share above 1 would turn a hit
     * into a heal, and a negative one would sharpen the blow the ability is supposed to soften. The
     * schema already refuses both at load - this is the second line, for the ranks it cannot see.
     */
    public void reduceTrigger(double share) {
        if (trigger == null) {
            return;
        }
        trigger.reduce().accept(Math.clamp(share, 0.0, 1.0));
    }
}
