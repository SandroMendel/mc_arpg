package rpg.core.persistence;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * How every other block reaches durable data. No block knows a table, a statement or the pool.
 *
 * <p>Two deliberate absences shape this interface:
 *
 * <ul>
 *   <li><strong>No synchronous load.</strong> A blocking read would be the easiest way to violate
 *       Constitution I.1 from the tick, so it does not exist to be called. The return type says so
 *       in the type system rather than in a comment.
 *   <li><strong>No {@code save}.</strong> When data is written is decided by the flush cycle
 *       (FR-003, FR-004, FR-011), not by the calling block. A public save would let any block bypass
 *       write-behind and reintroduce a database access per game event, which is precisely what
 *       SC-005 measures.
 * </ul>
 *
 * @param <I> the aggregate's business key
 * @param <T> the aggregate type
 */
public interface Repository<I, T> {

    /**
     * Loads an aggregate.
     *
     * @return the aggregate, or {@link Optional#empty()} if the key is unknown - not an error, just
     *     a player connecting for the first time. The future completes exceptionally if storage
     *     could not be reached or the record could not be read; the login path must then reject the
     *     session rather than substitute a default (FR-005a).
     */
    CompletableFuture<Optional<T>> load(I id);

    /**
     * Notes that an aggregate changed and must be written on the next flush.
     *
     * <p>Safe to call from the tick: no database access, no lock contention worth measuring, and
     * repeated calls for the same id coalesce into a single write (FR-002).
     */
    void markDirty(I id);

    /** Which aggregate type this repository owns; used when batching per table. */
    AggregateType aggregateType();
}
