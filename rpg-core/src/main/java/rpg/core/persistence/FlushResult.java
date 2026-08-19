package rpg.core.persistence;

import java.time.Duration;
import java.util.Objects;

/**
 * Outcome of one flush.
 *
 * <p>Returned rather than thrown even when writes failed: a flush must never fail outwards, or the
 * cycle would stop after the first outage and persistence would stand still exactly when it is
 * needed most (see {@code contracts/write-behind.md}).
 *
 * @param reason what triggered this flush
 * @param written aggregates successfully written
 * @param failed aggregates whose write failed; their marks are kept for the next attempt
 * @param took wall-clock duration
 */
public record FlushResult(FlushReason reason, int written, int failed, Duration took) {

    public FlushResult {
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(took, "took");
        if (written < 0 || failed < 0) {
            throw new IllegalArgumentException("counts must not be negative");
        }
    }

    /** Whether every aggregate in this flush was written. */
    public boolean complete() {
        return failed == 0;
    }

    /** Nothing was pending. */
    public static FlushResult empty(FlushReason reason) {
        return new FlushResult(reason, 0, 0, Duration.ZERO);
    }
}
