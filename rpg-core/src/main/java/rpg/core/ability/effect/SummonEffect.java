package rpg.core.ability.effect;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import rpg.core.stats.StatSnapshot;

/**
 * A creature called into the world for a while - the rogue's Clone (FR-016c).
 *
 * <p>It carries <b>the summoner's values at the moment of the call</b>, not a live link to the
 * summoner: once it stands, it is its own thing, and buffing the summoner afterwards does not buff
 * it. That is the same choice B05 makes for projectiles, and for the same reason - what was true at
 * the throw decides, or every effect has to keep asking who threw it.
 *
 * <p><b>The creature does not attack.</b> Its job is to be attacked instead of the summoner, and to
 * fire an effect when it runs out - by duration or by health.
 *
 * <p><b>Redirecting mob aggression is not implemented here and will not be until B10.</b> The hook
 * below exists and is called; the platform's answer is currently "nothing happened". A clone today
 * stands there and can be hit; whether mobs prefer it is a question about mob behaviour, and mob
 * behaviour is B10's block (Rule 5). Until then the ability is honestly weaker than its description,
 * and that is written down rather than quietly true.
 */
public final class SummonEffect implements AbilityEffect {

    /** Places a creature in the world. The platform installs the real one. */
    @FunctionalInterface
    public interface Placer {

        /**
         * @param summonerId who called it
         * @param snapshot the summoner's values at the moment of the call - the creature keeps these
         * @param health how much it can take before it is gone
         * @param lifetime how long it stands if nothing kills it
         * @return the creature, or empty if it could not be placed
         */
        Optional<UUID> place(
                UUID summonerId, StatSnapshot snapshot, double health, Duration lifetime);

        /** Places nothing. The default until the platform installs one. */
        static Placer none() {
            return (summonerId, snapshot, health, lifetime) -> Optional.empty();
        }
    }

    /**
     * Turns mob attention towards the creature.
     *
     * <p><b>Empty until B10.</b> It is declared now because the ability's description depends on it,
     * and a named gap is easier to close than an unnamed one.
     */
    @FunctionalInterface
    public interface AggressionRedirect {
        void redirect(UUID summonerId, UUID creatureId);

        /** Redirects nothing - the state of things until B10. */
        static AggressionRedirect none() {
            return (summonerId, creatureId) -> {};
        }
    }

    private final Placer placer;
    private volatile AggressionRedirect redirect = AggressionRedirect.none();

    public SummonEffect(Placer placer) {
        this.placer = Objects.requireNonNull(placer, "placer");
    }

    /** Installs the B10 redirect, once B10 has one. */
    public void setAggressionRedirect(AggressionRedirect redirect) {
        this.redirect = Objects.requireNonNull(redirect, "redirect");
    }

    @Override
    public void apply(EffectContext context) {
        Duration lifetime = context.spec().duration();
        if (lifetime == null || lifetime.isZero()) {
            return;
        }
        placer.place(context.casterId(), context.snapshot(), context.value(), lifetime)
                .ifPresent(creature -> redirect.redirect(context.casterId(), creature));
    }
}
