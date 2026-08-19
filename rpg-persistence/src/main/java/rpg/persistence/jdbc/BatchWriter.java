package rpg.persistence.jdbc;

import java.util.List;

import javax.sql.DataSource;

import rpg.core.persistence.DirtyMark;

/**
 * Writes the pending aggregates of one type in a single batch.
 *
 * <p>One implementation per aggregate type, each owning its table and its statement. The flush
 * cycle knows only this interface, which is what keeps SQL out of the scheduling logic.
 */
public interface BatchWriter {

    /**
     * Writes the aggregates named by {@code marks}.
     *
     * <p>Returns the marks that were actually persisted rather than a count: the caller removes
     * exactly those from the buffer, so a partial success keeps the rest for the next round instead
     * of losing them.
     *
     * <p>Implementations use {@code INSERT ... ON CONFLICT DO UPDATE} so no read is needed before a
     * write (FR-007), and run everything in one transaction so a failure leaves nothing half
     * applied.
     *
     * @throws rpg.core.persistence.PersistenceException if the batch could not be executed at all;
     *     the caller then keeps every mark and records an outage
     */
    List<DirtyMark> write(DataSource dataSource, List<DirtyMark> marks);
}
