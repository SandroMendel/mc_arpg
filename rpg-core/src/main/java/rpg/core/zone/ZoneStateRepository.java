package rpg.core.zone;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Loads and stores what a character carries out of this block.
 *
 * <p><b>Reading is asynchronous, writing is not a write.</b> The load happens once when a session
 * starts - off the tick, like every database access in this project (Constitution I). Everything
 * afterwards changes the in-memory state and marks it dirty; the actual row is written by B02's
 * write-behind buffer on its own cadence. No game event ever waits for the database (FR-063).
 *
 * <p><b>An empty result means "never seen".</b> Not "no unlocks" - those are two different things,
 * and the difference is what decides whether a character starts in the start region (FR-037b).
 */
public interface ZoneStateRepository {

    /** The stored state, or empty when this block has never placed that character. */
    CompletableFuture<Optional<ZoneCharacterState>> find(UUID characterId);

    /**
     * Records that this block has now seen the character, so the next login knows they are not new.
     *
     * <p>Idempotent: calling it for a character who already has a row changes nothing.
     */
    void markSeen(UUID characterId);

    /** Records a discovered crystal. Grows only - there is deliberately no way to take one back. */
    void unlock(UUID characterId, String crystalKey);

    /** Sets or clears the respawn a combat logout owes this character (ADR-030). */
    void setPendingRespawn(UUID characterId, String zoneKey);
}
