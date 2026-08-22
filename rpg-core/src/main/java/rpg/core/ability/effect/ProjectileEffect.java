package rpg.core.ability.effect;

import java.util.Objects;
import java.util.UUID;

import rpg.core.combat.DamageType;
import rpg.core.stats.StatSnapshot;

/**
 * Something thrown that does its work on arrival - the mage's Fireball (FR-011).
 *
 * <p><b>It carries the values from the throw.</b> Snapshot, damage type and amount are decided here
 * and travel with the projectile; whatever the caster's stats do in the second between throw and
 * impact changes nothing. This is B05's {@code projectileDamage} rule, and the reason is the same:
 * the alternative is asking the caster on impact, and by then the caster may be dead, logged out or
 * two hundred blocks away.
 *
 * <p>That also means <b>the impact still lands if the thrower is gone</b>. A fireball in flight when
 * its caster disconnects still hits, and it hits for what it was worth when it left.
 */
public final class ProjectileEffect implements AbilityEffect {

    /** Everything the projectile needs to carry, decided at the throw. */
    public record Payload(
            String abilityId,
            UUID casterId,
            StatSnapshot snapshot,
            double amount,
            DamageType damageType,
            double speed) {

        public Payload {
            Objects.requireNonNull(abilityId, "abilityId");
            Objects.requireNonNull(casterId, "casterId");
            Objects.requireNonNull(snapshot, "snapshot");
        }
    }

    /** Launches it. The platform installs the real one. */
    @FunctionalInterface
    public interface Launcher {
        void launch(Payload payload);

        /** Launches nothing. The default until the platform installs one. */
        static Launcher none() {
            return payload -> {};
        }
    }

    /** How fast a projectile flies when the definition does not say. Blocks per tick. */
    private static final double DEFAULT_SPEED = 1.6;

    private final Launcher launcher;

    public ProjectileEffect(Launcher launcher) {
        this.launcher = Objects.requireNonNull(launcher, "launcher");
    }

    @Override
    public void apply(EffectContext context) {
        launcher.launch(
                new Payload(
                        context.ability().id(),
                        context.casterId(),
                        context.snapshot(),
                        context.value(),
                        context.spec().damageType(),
                        DEFAULT_SPEED));
    }
}
