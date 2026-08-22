package rpg.core.ability.effect;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

/**
 * Vanishing for a moment - the rogue's Vanish (FR-016d).
 *
 * <p>Vanilla invisibility plus invulnerability, and it <b>ends the moment the player deals
 * damage</b>. Without that it would not be an escape, it would be a free opening move, and the
 * ability was specified as the former.
 *
 * <p>The ending is not this class's job: B05's damage-dealt hook already knows when a player hits
 * something and {@code AbilityRuntime} already ends running abilities with
 * {@code EndCause.DAMAGE_DEALT}. This primitive only makes the player disappear.
 *
 * <p><b>Two things stay open until B10:</b> mobs already chasing the player do not lose interest, and
 * bosses are not excluded from either. Both are questions about mob behaviour. What is <em>not</em>
 * open: the void still kills. Invulnerability here means damage, not falling out of the world.
 */
public final class InvisibilityEffect implements AbilityEffect {

    /** Hides a player and makes them untouchable. The platform installs the real one. */
    @FunctionalInterface
    public interface Concealer {
        void conceal(UUID holderId, Duration duration);

        /** Conceals nothing. The default until the platform installs one. */
        static Concealer none() {
            return (holderId, duration) -> {};
        }
    }

    private final Concealer concealer;

    public InvisibilityEffect(Concealer concealer) {
        this.concealer = Objects.requireNonNull(concealer, "concealer");
    }

    @Override
    public void apply(EffectContext context) {
        Duration duration = context.spec().duration();
        if (duration == null || duration.isZero()) {
            return;
        }
        for (UUID target : context.targets()) {
            concealer.conceal(target, duration);
        }
    }
}
