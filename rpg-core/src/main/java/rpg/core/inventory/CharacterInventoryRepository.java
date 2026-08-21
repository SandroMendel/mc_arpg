package rpg.core.inventory;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Reading and marking the stored inventory of a character.
 *
 * <p>Same shape as the other per-character repositories: reads are asynchronous, and there is no write
 * method. A change is <b>marked</b> and the write-behind buffer from B02 decides when it reaches the
 * database - picking up an item must never touch the database inside a game event (Constitution II).
 */
public interface CharacterInventoryRepository {

    /** Empty for a character that has never stored anything - the caller treats that as carrying nothing. */
    CompletableFuture<Optional<CharacterInventory>> find(UUID characterId);

    /** Marks the aggregate as needing a write. Never writes itself. */
    void markDirty(UUID characterId);
}
