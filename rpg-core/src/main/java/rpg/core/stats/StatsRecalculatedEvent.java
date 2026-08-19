package rpg.core.stats;

import java.util.UUID;

/**
 * Published after every completed recalculation (FR-023).
 *
 * <p>Lets B13 update a HUD and B05 drop cached values without polling the engine. Published
 * <b>after</b> the vanilla mirror has been triggered, so a subscriber never observes a state where
 * the number and the bar disagree.
 *
 * <p>There is deliberately no precomputed list of what changed. Building one would be work in
 * exactly the case where nobody needs it - the first calculation during login, where everything
 * "changed". A subscriber that cares compares {@code current.get(x)} against
 * {@code previous.get(x)}.
 *
 * @param holderId the holder
 * @param characterId its character, or {@code null} for a holder without a player
 * @param previous the result before this calculation, or {@code null} the very first time
 * @param current the new result; never {@code null}
 */
public record StatsRecalculatedEvent(
        UUID holderId, UUID characterId, StatSnapshot previous, StatSnapshot current) {}
