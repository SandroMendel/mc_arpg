package rpg.core.classes;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Reading and marking the class progress of a character.
 *
 * <p>Mirrors {@code CharacterProgressRepository} from B06 on purpose: reads are asynchronous, writes
 * are not a method here at all. A change is <b>marked</b>, and the write-behind buffer from B02
 * decides when it reaches the database - that is what keeps a tier advance from touching the database
 * inside a game event (Constitution II).
 */
public interface ClassProgressRepository {

    /** Empty for a character that has never had a row - the caller substitutes {@link ClassProgress#initial}. */
    CompletableFuture<Optional<ClassProgress>> find(UUID characterId);

    /** Marks the aggregate as needing a write. Never writes itself. */
    void markDirty(UUID characterId);
}
