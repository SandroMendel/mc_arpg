package rpg.core.currency;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Loading and marking a character's balance (FR-014).
 *
 * <p>Same two methods as {@code CharacterProgressRepository} in B06, and it lives in the <b>block
 * package</b> rather than {@code rpg/core/persistence/} for the same reason (ADR-015 point 4): what
 * lives in the persistence package are the aggregates B02 owns itself. The owner of this one should
 * be readable from where it sits. The JDBC implementation lives in {@code rpg-persistence}.
 *
 * <p><b>There is deliberately no {@code save}.</b> A booking marks the character and nothing more;
 * the write-behind buffer does the rest, so no game event ever reaches the database (Constitution
 * II).
 */
public interface CharacterBalanceRepository {

    /** Loads one character's stored balance, or empty when it has never been written. */
    CompletableFuture<Optional<CharacterBalance>> find(UUID characterId);

    /** Notes that this character changed. The only write path there is. */
    void markDirty(UUID characterId);
}
