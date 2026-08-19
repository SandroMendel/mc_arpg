package rpg.core.persistence;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Access to player state.
 *
 * <p>See {@code contracts/repository.md}. The two additions beyond the base interface both exist
 * for correctness rather than convenience.
 */
public interface PlayerStateRepository extends Repository<UUID, PlayerState> {

    /**
     * Completes once every pending write for this player has been persisted (FR-019a).
     *
     * <p>The login path awaits this before loading. Without it, a player who reconnects
     * immediately would read the older stored state, and the previous session's buffered flush
     * would then land on top - rolling their progress back or duplicating items. Waiting first is
     * what makes the handover lossless; the revision check is only the net beneath it.
     *
     * <p>The future completes exceptionally when {@code timeout} elapses, and the login is then
     * refused rather than left hanging (FR-019c).
     */
    CompletableFuture<Void> awaitPendingWrites(UUID playerId, Duration timeout);

    /**
     * Strips a player's data of its personal reference (FR-017a to FR-017c).
     *
     * <p>One transaction: state deleted, identifiers in statistics and audit log replaced by a
     * random substitute, and the act itself recorded. A partially anonymised state would be worse
     * than none - it would neither satisfy the request nor keep the data usable.
     */
    CompletableFuture<Void> anonymize(UUID playerId);
}
