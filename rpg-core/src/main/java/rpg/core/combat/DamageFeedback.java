package rpg.core.combat;

import java.util.UUID;

/**
 * Hurt animation and knockback (FR-037).
 *
 * <p>Needed because a vanilla damage event set to zero no longer shows either. Neutralising the
 * damage is what keeps the health system honest; losing the feedback with it would make every hit
 * feel like a miss.
 *
 * <p>An interface in {@code rpg-core} with its implementation in {@code rpg-platform}: without a
 * registered feedback this does nothing, which is exactly what keeps the whole pipeline testable
 * without a server.
 */
public interface DamageFeedback {

    /** Plays the hurt animation on the target. */
    void playHurtAnimation(UUID targetId);

    /**
     * Applies knockback to the target, away from the source.
     *
     * <p>Vanilla knockback, deliberately: a knockback model of this block's own appears in no block
     * brief and would be scope growth without an order.
     */
    void applyKnockback(UUID targetId, UUID sourceId, double strength);
}
