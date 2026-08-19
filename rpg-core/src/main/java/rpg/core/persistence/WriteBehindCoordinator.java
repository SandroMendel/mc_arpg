package rpg.core.persistence;

import java.util.concurrent.CompletableFuture;

/**
 * Decides <em>when</em> data is written. The repositories decide only <em>what</em>.
 *
 * <p>See {@code contracts/write-behind.md}. The one rule worth restating here, because it is easy
 * to break by accident: {@link #flushNow} never fails outwards. A write failure appears in the
 * {@link FlushResult} and in the log, but the call itself completes normally - if it threw, the
 * self-rescheduling interval cycle would stop after the first outage and persistence would stand
 * still exactly when it is needed most.
 */
public interface WriteBehindCoordinator {

    /** Notes that an aggregate must be written on the next flush. Safe to call from the tick. */
    void markDirty(AggregateType type, String aggregateId);

    /**
     * Runs a flush now.
     *
     * <p>At most one flush runs at a time; asking while one is in flight returns the running one's
     * future rather than starting a second.
     */
    CompletableFuture<FlushResult> flushNow(FlushReason reason);

    /** Current fill state of the buffer (FR-009a to FR-009c). */
    BufferStatus bufferStatus();
}
