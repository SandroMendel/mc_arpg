package rpg.core.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * T048 / FR-002: marking the same aggregate repeatedly produces one mark, and therefore one write.
 */
class WriteBehindBufferCoalescingTest {

    private static final Instant T0 = Instant.parse("2026-08-19T12:00:00Z");

    private static WriteBehindBuffer buffer() {
        return new WriteBehindBuffer(1_000, Clock.fixed(T0, ZoneOffset.UTC));
    }

    @Test
    void markingTheSameAggregateTwiceLeavesOneMark() {
        WriteBehindBuffer buffer = buffer();
        String player = UUID.randomUUID().toString();

        assertThat(buffer.mark(AggregateType.PLAYER_STATE, player)).isTrue();
        assertThat(buffer.mark(AggregateType.PLAYER_STATE, player)).isFalse();

        assertThat(buffer.pending()).isEqualTo(1);
    }

    @Test
    void aThousandChangesToOneAggregateCostOneWrite() {
        WriteBehindBuffer buffer = buffer();
        String player = UUID.randomUUID().toString();

        for (int i = 0; i < 1_000; i++) {
            buffer.mark(AggregateType.PLAYER_STATE, player);
        }

        // This is what FR-002 and SC-005 come down to: game events cost map writes, not database
        // round trips.
        assertThat(buffer.pending()).isEqualTo(1);
        assertThat(buffer.snapshot()).hasSize(1);
    }

    @Test
    void theSameIdUnderDifferentTypesAreDistinctMarks() {
        WriteBehindBuffer buffer = buffer();
        String id = UUID.randomUUID().toString();

        buffer.mark(AggregateType.PLAYER_STATE, id);
        buffer.mark(AggregateType.ITEM_INSTANCE, id);

        assertThat(buffer.pending()).isEqualTo(2);
    }

    @Test
    void reMarkingKeepsTheOriginalTimestamp() {
        // Keeping the first timestamp is what makes "how long has this change been waiting"
        // measurable; refreshing it would reset that clock on every change.
        MutableClock clock = new MutableClock(T0);
        WriteBehindBuffer buffer = new WriteBehindBuffer(1_000, clock);
        String player = UUID.randomUUID().toString();

        buffer.mark(AggregateType.PLAYER_STATE, player);
        clock.advance(Duration.ofMinutes(5));
        buffer.mark(AggregateType.PLAYER_STATE, player);

        assertThat(buffer.snapshot()).singleElement().extracting(DirtyMark::markedAt).isEqualTo(T0);
    }

    @Test
    void snapshotDoesNotRemoveAnything() {
        WriteBehindBuffer buffer = buffer();
        buffer.mark(AggregateType.PLAYER_STATE, "a-player");

        buffer.snapshot();
        buffer.snapshot();

        // Removal happens only after a write succeeded; a snapshot must be repeatable.
        assertThat(buffer.pending()).isEqualTo(1);
    }

    @Test
    void onlyWrittenMarksAreRemoved() {
        WriteBehindBuffer buffer = buffer();
        buffer.mark(AggregateType.PLAYER_STATE, "written");
        buffer.mark(AggregateType.PLAYER_STATE, "not-written");

        List<DirtyMark> snapshot = buffer.snapshot();
        DirtyMark written =
                snapshot.stream().filter(m -> m.aggregateId().equals("written")).findFirst().orElseThrow();

        assertThat(buffer.removeWritten(List.of(written))).isEqualTo(1);
        assertThat(buffer.snapshot()).singleElement().extracting(DirtyMark::aggregateId)
                .isEqualTo("not-written");
    }

    @Test
    void anAggregateReMarkedDuringAFlushSurvivesThatFlush() {
        // The edge case from spec.md: a change made while the batch is running must not be cleared
        // by that batch. The re-mark carries a newer timestamp, so it is a different mark.
        MutableClock clock = new MutableClock(T0);
        WriteBehindBuffer buffer = new WriteBehindBuffer(1_000, clock);
        buffer.mark(AggregateType.PLAYER_STATE, "busy");

        List<DirtyMark> snapshot = buffer.snapshot(); // the flush takes its snapshot

        buffer.removeWritten(snapshot); // ... writes it ...
        clock.advance(Duration.ofSeconds(1));
        buffer.mark(AggregateType.PLAYER_STATE, "busy"); // ... and the change arrives after

        assertThat(buffer.pending()).isEqualTo(1);
        assertThat(buffer.snapshot())
                .singleElement()
                .extracting(DirtyMark::markedAt)
                .isEqualTo(T0.plusSeconds(1));
    }

    @Test
    void snapshotOfReturnsOnlyTheRequestedType() {
        WriteBehindBuffer buffer = buffer();
        buffer.mark(AggregateType.PLAYER_STATE, "p");
        buffer.mark(AggregateType.STATISTICS, "s");
        buffer.mark(AggregateType.AUDIT_LOG, "a");

        assertThat(buffer.snapshotOf(AggregateType.STATISTICS))
                .singleElement()
                .extracting(DirtyMark::aggregateId)
                .isEqualTo("s");
    }

    /** Clock that can be moved forward, so timestamp behaviour is testable without sleeping. */
    static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
