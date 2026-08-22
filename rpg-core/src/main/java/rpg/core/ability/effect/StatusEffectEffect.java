package rpg.core.ability.effect;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

/**
 * A vanilla status effect for a duration - slow fall, slowness and whatever else a definition names.
 *
 * <p>Applying it needs Paper, so this primitive is a thin front for a platform hook. Same split as
 * {@code TargetResolver}: what to apply, for how long and to whom is a rule and stays here; telling
 * the server about it is not.
 *
 * <p>Without a hook installed it does nothing rather than throwing. An ability whose visual effect is
 * missing is a smaller problem than one that takes the trigger down with it.
 */
public final class StatusEffectEffect implements AbilityEffect {

    /** Applies a vanilla effect. The platform installs the real one. */
    @FunctionalInterface
    public interface Applier {
        void apply(UUID holderId, String effectName, Duration duration, int amplifier);

        /** Does nothing. The default until the platform installs one. */
        static Applier none() {
            return (holderId, effectName, duration, amplifier) -> {};
        }
    }

    private final Applier applier;

    public StatusEffectEffect(Applier applier) {
        this.applier = Objects.requireNonNull(applier, "applier");
    }

    @Override
    public void apply(EffectContext context) {
        Duration duration = context.spec().duration();
        if (duration == null || duration.isZero()) {
            return;
        }
        // The value is the amplifier, and vanilla counts it from zero: 1 is level II. Rounded rather
        // than truncated so a rank curve landing on 1.99 gives level III, not level II.
        int amplifier = (int) Math.max(0, Math.round(context.value()));
        for (UUID target : context.targets()) {
            applier.apply(target, context.spec().statusEffect(), duration, amplifier);
        }
    }
}
