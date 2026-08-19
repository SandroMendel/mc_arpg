package rpg.core.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * T062 / FR-009c: the warning fires once per crossing, not on every check.
 *
 * <p>Worth asserting rather than trusting: a warning repeated on every cycle would flood the log
 * during exactly the outage an operator needs to read it in.
 */
class BufferWarnThresholdTest {

    private static final Clock FIXED =
            Clock.fixed(Instant.parse("2026-08-19T12:00:00Z"), ZoneOffset.UTC);

    private static WriteBehindBuffer buffer(int capacity) {
        return new WriteBehindBuffer(capacity, 0.8d, FIXED);
    }

    private static void fill(WriteBehindBuffer buffer, int count) {
        for (int i = 0; i < count; i++) {
            buffer.mark(AggregateType.PLAYER_STATE, "player-" + i);
        }
    }

    @Test
    void belowTheThresholdNothingWarns() {
        WriteBehindBuffer buffer = buffer(100);
        fill(buffer, 79);

        assertThat(buffer.status().warning()).isFalse();
        assertThat(buffer.shouldWarnNow()).isFalse();
    }

    @Test
    void theWarningFiresOnceWhenTheThresholdIsCrossed() {
        WriteBehindBuffer buffer = buffer(100);
        fill(buffer, 80);

        assertThat(buffer.status().warning()).isTrue();
        assertThat(buffer.shouldWarnNow()).isTrue();
        // Every further check while still above stays quiet.
        assertThat(buffer.shouldWarnNow()).isFalse();
        assertThat(buffer.shouldWarnNow()).isFalse();
    }

    @Test
    void droppingBelowAndCrossingAgainWarnsAgain() {
        WriteBehindBuffer buffer = buffer(100);
        fill(buffer, 80);
        assertThat(buffer.shouldWarnNow()).isTrue();

        // A successful flush brings it back down...
        List<DirtyMark> snapshot = buffer.snapshot();
        buffer.removeWritten(snapshot.subList(0, 40));
        assertThat(buffer.status().warning()).isFalse();

        // ... and the next crossing is a new event worth reporting.
        fill(buffer, 80);
        assertThat(buffer.shouldWarnNow()).isTrue();
    }

    @Test
    void atCapacityItIsNoLongerAWarningButAnOverflow() {
        WriteBehindBuffer buffer = buffer(100);
        fill(buffer, 100);

        BufferStatus status = buffer.status();
        assertThat(status.overCapacity()).isTrue();
        assertThat(status.warning()).isFalse(); // past warning; FR-009b applies now
        assertThat(status.fillRatio()).isEqualTo(1.0d);
    }
}
