package rpg.core.ability;

import java.util.UUID;
import java.util.function.BiPredicate;

/**
 * Whether a hit landed from behind - the rogue's Sneaky Backstab (FR-052a).
 *
 * <p>The rule is a comparison of two directions and lives here; reading where the two of them are
 * standing needs the world and does not. Same split as {@link TargetResolver}.
 *
 * <p><b>The angle, not the side.</b> "Behind" is a cone, and where its edge sits is a balancing
 * decision rather than a geometric fact: a narrow cone rewards positioning, a wide one rewards
 * standing roughly right. {@link #DEFAULT_ANGLE} is where it starts.
 */
@FunctionalInterface
public interface BehindTargetCheck extends BiPredicate<UUID, UUID> {

    /**
     * Half the opening of the cone counted as "behind", in degrees.
     *
     * <p>90 means exactly the rear hemisphere: anything the target is not facing. Tighter would make
     * a backstab a matter of pixels, which is not what a server with 150 players can promise.
     */
    double DEFAULT_ANGLE = 90.0;

    /**
     * @param attackerId who hit
     * @param targetId who was hit
     * @return whether the attacker stood behind the target at that moment
     */
    @Override
    boolean test(UUID attackerId, UUID targetId);

    /** Never behind. The default until the platform installs the real one. */
    static BehindTargetCheck never() {
        return (attackerId, targetId) -> false;
    }

    /**
     * The geometry, given the two directions.
     *
     * <p>Extracted so it can be tested without a server: the dot product of the target's facing and
     * the direction from target to attacker is negative exactly when the attacker is behind.
     *
     * @param facingX where the target is looking, horizontally
     * @param facingZ the same
     * @param toAttackerX from the target towards the attacker
     * @param toAttackerZ the same
     */
    static boolean isBehind(
            double facingX, double facingZ, double toAttackerX, double toAttackerZ, double angle) {
        double facingLength = Math.hypot(facingX, facingZ);
        double towardsLength = Math.hypot(toAttackerX, toAttackerZ);
        if (facingLength == 0.0 || towardsLength == 0.0) {
            // Standing exactly on top of each other, or a target with no facing at all. Not behind:
            // an undecidable case must not silently count as the favourable one.
            return false;
        }
        double cosine =
                (facingX * toAttackerX + facingZ * toAttackerZ) / (facingLength * towardsLength);
        double threshold = Math.cos(Math.toRadians(180.0 - angle));

        // The tolerance is not decoration. At the default 90 degrees the threshold is cos(90), which
        // ought to be zero and comes out of the trig round-trip as 6.1e-17 - so an attacker standing
        // exactly on the shoulder line has a cosine of 0 and lands just under it, counting as behind.
        // A backstab that fires from the side is exactly the kind of bug nobody reports and everybody
        // notices, so the boundary is excluded explicitly rather than left to floating point.
        return cosine < threshold - 1e-9;
    }
}
