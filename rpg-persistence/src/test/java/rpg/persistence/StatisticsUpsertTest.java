package rpg.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import rpg.core.persistence.FlushReason;
import rpg.persistence.support.PersistenceHarness;
import rpg.persistence.support.PostgresContainer;

/**
 * T052 / T053 / FR-016a to FR-016c, FR-007: daily statistics accumulate without ever reading first,
 * the day rollover needs no special handling, and sums work over ranges as well as all time.
 */
class StatisticsUpsertTest {

    private static final String METRIC = "mob_kills";

    private MovableClock clock;
    private PersistenceHarness harness;

    @BeforeEach
    void setUp() {
        PostgresContainer.resetSchema();
        clock = new MovableClock(Instant.parse("2026-08-19T12:00:00Z"));
        harness = new PersistenceHarness(clock, 50_000);
    }

    @AfterEach
    void tearDown() {
        if (harness != null) {
            harness.close();
        }
    }

    @Test
    void incrementsAccumulateIntoOneRowPerDay() throws Exception {
        UUID player = UUID.randomUUID();
        for (int i = 0; i < 100; i++) {
            harness.statistics.increment(player, METRIC, 1);
        }

        // 100 events, one mark, one row.
        assertThat(harness.buffer.pending()).isEqualTo(1);
        harness.flushCycle.flushNow(FlushReason.INTERVAL).get();

        assertThat(harness.statistics.total(player, METRIC).get()).isEqualTo(100L);
    }

    @Test
    void aSecondFlushAddsRatherThanReplaces() throws Exception {
        UUID player = UUID.randomUUID();
        harness.statistics.increment(player, METRIC, 30);
        harness.flushCycle.flushNow(FlushReason.INTERVAL).get();

        harness.statistics.increment(player, METRIC, 12);
        harness.flushCycle.flushNow(FlushReason.INTERVAL).get();

        // The ON CONFLICT clause adds; replacing would silently drop the first 30.
        assertThat(harness.statistics.total(player, METRIC).get()).isEqualTo(42L);
    }

    @Test
    void theDayRolloverStartsANewRowWithoutLosingOrDoublingAnything() throws Exception {
        UUID player = UUID.randomUUID();
        harness.statistics.increment(player, METRIC, 5);
        harness.flushCycle.flushNow(FlushReason.INTERVAL).get();

        clock.advance(Duration.ofDays(1));
        harness.statistics.increment(player, METRIC, 7);
        harness.flushCycle.flushNow(FlushReason.INTERVAL).get();

        LocalDate firstDay = LocalDate.parse("2026-08-19");
        LocalDate secondDay = LocalDate.parse("2026-08-20");

        assertThat(harness.statistics.sum(player, METRIC, firstDay, firstDay).get()).isEqualTo(5L);
        assertThat(harness.statistics.sum(player, METRIC, secondDay, secondDay).get()).isEqualTo(7L);
        assertThat(harness.statistics.total(player, METRIC).get()).isEqualTo(12L);
    }

    @Test
    void aRangeSumCoversExactlyTheRequestedDays() throws Exception {
        UUID player = UUID.randomUUID();
        for (int day = 0; day < 5; day++) {
            harness.statistics.increment(player, METRIC, 10);
            harness.flushCycle.flushNow(FlushReason.INTERVAL).get();
            clock.advance(Duration.ofDays(1));
        }

        LocalDate from = LocalDate.parse("2026-08-20");
        LocalDate to = LocalDate.parse("2026-08-22");

        // Three days in range, five written - the promise made to B12's leaderboards.
        assertThat(harness.statistics.sum(player, METRIC, from, to).get()).isEqualTo(30L);
        assertThat(harness.statistics.total(player, METRIC).get()).isEqualTo(50L);
    }

    @Test
    void differentMetricsAndPlayersDoNotMix() throws Exception {
        UUID one = UUID.randomUUID();
        UUID two = UUID.randomUUID();
        harness.statistics.increment(one, METRIC, 3);
        harness.statistics.increment(one, "damage_dealt", 99);
        harness.statistics.increment(two, METRIC, 7);
        harness.flushCycle.flushNow(FlushReason.INTERVAL).get();

        assertThat(harness.statistics.total(one, METRIC).get()).isEqualTo(3L);
        assertThat(harness.statistics.total(one, "damage_dealt").get()).isEqualTo(99L);
        assertThat(harness.statistics.total(two, METRIC).get()).isEqualTo(7L);
    }

    @Test
    void aZeroDeltaIsNotEvenMarked() {
        harness.statistics.increment(UUID.randomUUID(), METRIC, 0);

        assertThat(harness.buffer.pending()).isZero();
    }

    /** Clock that can be moved forward, so the day rollover is testable without waiting. */
    static final class MovableClock extends Clock {

        private Instant now;

        MovableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
