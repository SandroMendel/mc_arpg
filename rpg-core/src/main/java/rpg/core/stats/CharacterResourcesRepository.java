package rpg.core.stats;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Reads and writes the persisted resources of a character (FR-028).
 *
 * <p>Declared here, implemented in {@code rpg-persistence}. Same split as B02's and B03's
 * repositories, and the reason {@code java.sql} never appears in this module.
 *
 * <p>Writing is not on this interface by design: nothing calls "save" directly. A change marks the
 * character through {@link #markDirty}, and B02's write-behind cycle does the rest - which is what
 * keeps a game event from ever producing a database round trip (SC-012).
 */
public interface CharacterResourcesRepository {

    /** The stored resources of one character, or empty if it has none yet - which means new. */
    CompletableFuture<Optional<CharacterResources>> find(UUID characterId);

    /** Marks a character's resources for the next write-behind flush. */
    void markDirty(UUID characterId);
}
