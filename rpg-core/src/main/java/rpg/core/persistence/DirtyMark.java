package rpg.core.persistence;

import java.time.Instant;
import java.util.Objects;

/**
 * A note that one aggregate changed since the last write.
 *
 * <p>Identity is the pair ({@link #aggregateType()}, {@link #aggregateId()}) - deliberately not the
 * timestamp. That is what makes marks coalesce: a thousand changes to one player produce one mark,
 * not a thousand, and therefore one write. It is also why the buffer does not grow with the
 * duration of a database outage, only with the number of distinct aggregates touched during it.
 *
 * @param aggregateType which kind of aggregate changed
 * @param aggregateId its business key
 * @param markedAt when it was <em>first</em> marked since the last successful write - kept rather
 *     than refreshed on every change, so it can be measured how long a change has been waiting
 */
public record DirtyMark(AggregateType aggregateType, String aggregateId, Instant markedAt) {

    public DirtyMark {
        Objects.requireNonNull(aggregateType, "aggregateType");
        Objects.requireNonNull(aggregateId, "aggregateId");
        Objects.requireNonNull(markedAt, "markedAt");
        if (aggregateId.isBlank()) {
            throw new IllegalArgumentException("aggregateId must not be blank");
        }
    }

    /** The coalescing key: two marks with the same identity are the same mark. */
    public Identity identity() {
        return new Identity(aggregateType, aggregateId);
    }

    /** Value-based identity of a marked aggregate, without the timestamp. */
    public record Identity(AggregateType aggregateType, String aggregateId) {}
}
