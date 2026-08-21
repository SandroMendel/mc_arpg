package rpg.core.progression;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Loading and marking a character's progress (FR-054).
 *
 * <p>Same two methods as {@code CharacterResourcesRepository} in B04, and it lives in
 * {@code rpg-core} for the same reason: {@link DefaultProgression} needs it, and the direction
 * {@code plugin -> persistence -> core} allows nothing else. The JDBC implementation lives in
 * {@code rpg-persistence}.
 *
 * <p>There is deliberately no {@code save}. A gain marks the character and the write-behind buffer
 * from B02 does the rest - no game event ever reaches the database directly.
 */
public interface CharacterProgressRepository {

    /** Loads one character's stored progress, or empty when it has never been written. */
    CompletableFuture<Optional<CharacterProgress>> find(UUID characterId);

    /** Notes that this character changed. The only write path there is (FR-054). */
    void markDirty(UUID characterId);
}
