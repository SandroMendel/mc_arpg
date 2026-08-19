package rpg.core.persistence;

import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Access to per-player statistics, aggregated by calendar day (FR-016a).
 *
 * <p>Individual events are never stored. They are summed in memory onto the day's value, which is
 * what keeps this table at a manageable size under indefinite retention (FR-017) while still
 * supporting both all-time and time-range queries (FR-016b).
 */
public interface StatisticsRepository {

    /**
     * Adds {@code delta} to today's value for this metric.
     *
     * <p>A delta rather than a value on purpose: only a delta can be written with
     * {@code ON CONFLICT DO UPDATE SET value = value + excluded.value}, and therefore without
     * reading first (FR-007). Setting an absolute value would require a read on every event, which
     * is exactly what FR-002 forbids.
     *
     * <p>Safe to call from the tick.
     */
    void increment(UUID playerId, String metric, long delta);

    /** Sum of one metric for one player over an inclusive date range (FR-016b). */
    CompletableFuture<Long> sum(UUID playerId, String metric, LocalDate from, LocalDate to);

    /** All-time sum of one metric for one player. */
    CompletableFuture<Long> total(UUID playerId, String metric);
}
