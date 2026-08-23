package rpg.core.ability.effect;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import rpg.core.combat.DamageType;

/**
 * Absorbs damage before health does (FR-015, FR-015a).
 *
 * <p><b>The filter is the interesting part.</b> The warrior's Block takes physical damage only and
 * lets magic straight through; the mage's Magic Shield takes everything. One primitive, one optional
 * field - the alternative would have been two shields that differ in a single condition.
 *
 * <p>Ends on expiry <em>or</em> when used up, whichever comes first. The remaining pool is a number
 * per character, not a scheduled countdown: it shrinks when hit and is compared against the clock
 * when read, like everything else in this block.
 */
public final class ShieldEffect implements AbilityEffect {

    /** What is left of a shield, and until when. */
    public record Absorption(double remaining, Instant until, DamageType filter) {

        boolean covers(DamageType type, Instant now) {
            return remaining > 0.0
                    && until.isAfter(now)
                    && (filter == null || filter == type);
        }
    }

    private final Map<UUID, Absorption> shields = new ConcurrentHashMap<>();
    private final java.time.Clock clock;

    public ShieldEffect(java.time.Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public void apply(EffectContext context) {
        if (context.spec().duration() == null) {
            return;
        }
        for (UUID target : context.targets()) {
            shields.put(
                    target,
                    new Absorption(
                            context.value(),
                            clock.instant().plus(context.spec().duration()),
                            context.spec().damageType()));
        }
    }

    /**
     * Takes what this shield can from an incoming hit and returns what is left over.
     *
     * <p>Called from the damage pipeline. A shield that does not cover this damage type is not
     * touched at all - the warrior's Block must still be there after a fireball went through it.
     *
     * @return the damage that still has to be applied
     */
    public double absorb(UUID targetId, DamageType type, double damage, Instant now) {
        Absorption shield = shields.get(targetId);
        if (shield == null || !shield.covers(type, now)) {
            return damage;
        }
        double taken = Math.min(shield.remaining(), damage);
        double left = shield.remaining() - taken;
        if (left <= 0.0) {
            shields.remove(targetId);
        } else {
            shields.put(targetId, new Absorption(left, shield.until(), shield.filter()));
        }
        return damage - taken;
    }

    /**
     * The interceptor that actually spends the pool.
     *
     * <p><b>Without this the shield was a number nobody read.</b> {@link #absorb} was written, tested
     * and never called: the pool filled on every cast, the comment at the wiring said "the pipeline
     * has to be able to ask it what it can take", and nothing ever asked. Block and Magic Shield were
     * eight seconds of nothing happening.
     *
     * <p>Lives here rather than next to the passives' interceptors because the pool lives here. A
     * consumer in another class would need the map handed to it, and that is how the first one got
     * forgotten.
     *
     * <p><b>APPLICATION, and before Second Life.</b> Defence has already been paid by then, so the
     * number this takes is the number that would otherwise have come off health - and Second Life,
     * which asks whether the blow is lethal, has to see what is left AFTER the shield, or a shield
     * that would have survived the hit still burns the rogue's one save.
     */
    public rpg.core.combat.DamageInterceptor interceptor() {
        return new rpg.core.combat.DamageInterceptor() {
            @Override
            public String id() {
                return "abilities.shield";
            }

            @Override
            public rpg.core.combat.PipelineStage stage() {
                return rpg.core.combat.PipelineStage.APPLICATION;
            }

            @Override
            public void intercept(rpg.core.combat.DamageView damage) {
                double left =
                        absorb(
                                damage.targetId(),
                                damage.type(),
                                damage.finalDamage(),
                                clock.instant());
                if (left != damage.finalDamage()) {
                    damage.setFinalDamage(left);
                }
            }
        };
    }

    /** What is left, for the display in B13. */
    public double remaining(UUID targetId, Instant now) {
        Absorption shield = shields.get(targetId);
        return shield != null && shield.until().isAfter(now) ? shield.remaining() : 0.0;
    }

    /** Drops a character's shield. On logout, on death and on a character switch. */
    public void forget(UUID targetId) {
        shields.remove(targetId);
    }

    /** How many shields are held. For leak tests. */
    public int trackedCount() {
        return shields.size();
    }
}
