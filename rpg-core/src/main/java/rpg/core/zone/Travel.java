package rpg.core.zone;

import java.util.UUID;

/**
 * Travelling between waypoint crystals (FR-050).
 *
 * <p><b>Both identifiers, and that is not redundancy.</b> The unlock and the fare belong to the
 * character (ADR-011); the combat state and the body that gets moved belong to the player. There is
 * no way for {@code rpg-core} to get from one to the other - that mapping lives in the session, in
 * the platform layer - and inventing a lookup here would have put a second answer to "who is this"
 * inside the domain. The caller holds both already.
 *
 * <p><b>The order is part of the contract</b> (FR-050e): unlocked → in combat → debit → teleport →
 * refund on failure. All of it in the same tick phase, so nothing can observe the half-done state.
 * See {@link DefaultTravel} for why the debit cannot come after the move.
 */
@FunctionalInterface
public interface Travel {

    /**
     * Sends this character to the crystal's region.
     *
     * @param holderId the player - for the combat check and the move
     * @param characterId the character - for the unlock and the fare
     * @param crystalKey which crystal was chosen
     */
    TravelResult travelTo(UUID holderId, UUID characterId, String crystalKey);
}
