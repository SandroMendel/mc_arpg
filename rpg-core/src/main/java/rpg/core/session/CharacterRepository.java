package rpg.core.session;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Access to characters.
 *
 * <p>Same shape as B02's repositories: asynchronous reads, a dirty mark instead of a save, and no
 * SQL visible to the caller. The implementation lives in {@code rpg-persistence} - B02 shipped a
 * static check that forbids {@code java.sql} anywhere else, and that rule applies to every block
 * from here on.
 */
public interface CharacterRepository {

    /** Every character of an account, at most one per class. */
    CompletableFuture<List<PlayerCharacter>> findByPlayer(UUID playerId);

    /** One character by its own identity. */
    CompletableFuture<Optional<PlayerCharacter>> find(UUID characterId);

    /**
     * Creates a character.
     *
     * <p>Fails exceptionally with {@link CharacterClassTakenException} if the account already has
     * one of that class (FR-020). The check is the database's unique key, not a preceding read -
     * a read-then-write would leave a window for two concurrent creations.
     */
    CompletableFuture<PlayerCharacter> create(UUID playerId, CharacterClass characterClass);

    /** Notes that a character changed and must be written on the next flush. Safe from the tick. */
    void markDirty(UUID characterId);
}
