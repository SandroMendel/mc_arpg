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
