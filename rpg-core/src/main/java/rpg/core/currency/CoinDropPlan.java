package rpg.core.currency;

import java.util.Objects;
import java.util.UUID;

import rpg.core.progression.WorldPoint;

/**
 * One pile that is about to exist: who it belongs to, how much, and where (FR-019, FR-026).
 *
 * <p><b>A value, and bukkit-free.</b> The rule layer decides who gets what; the platform layer turns
 * each of these into an entity. That split is what lets the whole entitlement calculation be tested
 * without a server.
 *
 * <p><b>The place is carried as a value, never looked up later from the dead creature's id</b>
 * (ADR-015 point 6). {@code Server.getEntity} stops finding a creature the moment it is removed, so
 * a lookup would work in testing and fail in play.
 *
 * @param characterId who may see and pick this up - the <b>character</b>, not the player (ADR-011)
 * @param holderId the stat holder the share was computed for, needed to find the player
 * @param amount coins; always positive - a plan for nothing is not created
 * @param origin where the creature died
 */
public record CoinDropPlan(UUID characterId, UUID holderId, long amount, WorldPoint origin) {

    public CoinDropPlan {
        Objects.requireNonNull(characterId, "characterId");
        Objects.requireNonNull(holderId, "holderId");
        Objects.requireNonNull(origin, "origin");
        if (amount <= 0L) {
            throw new IllegalArgumentException("amount must be positive, but was " + amount);
        }
    }
}
