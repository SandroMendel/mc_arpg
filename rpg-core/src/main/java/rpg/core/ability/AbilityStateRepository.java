package rpg.core.ability;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Loading and marking what a character owns per ability (FR-064).
 *
 * <p>Same shape as {@code CharacterProgressRepository} in B06 and {@code CharacterResourcesRepository}
 * in B04, and it lives in {@code rpg-core} for the same reason: the runtime needs it, and the
 * direction {@code plugin -> persistence -> core} allows nothing else. The JDBC implementation lives
 * in {@code rpg-persistence}.
 *
 * <p><b>A list, not a lookup per ability.</b> The load path reads a character's rows in one go while
 * its session is opening; asking per ability would mean up to eighteen round trips at exactly the
 * moment a player is waiting.
 *
 * <p>There is deliberately no {@code save}. A rank-up or a started cooldown marks the character and
 * the write-behind buffer from B02 does the rest - no game event ever reaches the database directly
 * (FR-032).
 */
public interface AbilityStateRepository {

    /**
     * Loads everything stored for one character, or an empty list when nothing was ever written.
     *
     * <p>Empty is the ordinary case for a fresh character: a row only appears once something differs
     * from the default (see the {@code V8_1} header).
     */
    CompletableFuture<List<AbilityState>> findAll(UUID characterId);

    /** Notes that this character changed. The only write path there is. */
    void markDirty(UUID characterId);
}
