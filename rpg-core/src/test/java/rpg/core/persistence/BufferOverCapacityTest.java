package rpg.core.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

/**
 * T063 / FR-009a, FR-009b: reaching capacity is reported, and nothing is discarded to make room.
 *
 * <p>The last part is the point. Dropping the oldest entries would keep the server running and
 * silently destroy progress - which is precisely what FR-009 rules out, and why the chosen response
 * is to disconnect players instead.
 */
class BufferOverCapacityTest {

    private static final Clock FIXED =
            Clock.fixed(Instant.parse("2026-08-19T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void reachingCapacityIsReported() {
        WriteBehindBuffer buffer = new WriteBehindBuffer(10, FIXED);
        for (int i = 0; i < 10; i++) {
            buffer.mark(AggregateType.PLAYER_STATE, "player-" + i);
        }

        assertThat(buffer.isOverCapacity()).isTrue();
        assertThat(buffer.status().overCapacity()).isTrue();
    }

    @Test
    void marksAreNeverDiscardedToMakeRoom() {
        WriteBehindBuffer buffer = new WriteBehindBuffer(10, FIXED);
        for (int i = 0; i < 10; i++) {
            buffer.mark(AggregateType.PLAYER_STATE, "player-" + i);
        }

        // Past capacity, new distinct aggregates still enter - the buffer does not evict.
        buffer.mark(AggregateType.PLAYER_STATE, "player-overflow");

        assertThat(buffer.pending()).isEqualTo(11);
        assertThat(buffer.snapshot())
                .extracting(DirtyMark::aggregateId)
                .contains("player-0", "player-overflow");
    }

    @Test
    void successfulWritesBringItBackUnderCapacity() {
        WriteBehindBuffer buffer = new WriteBehindBuffer(10, FIXED);
        for (int i = 0; i < 10; i++) {
            buffer.mark(AggregateType.PLAYER_STATE, "player-" + i);
        }

        buffer.removeWritten(buffer.snapshot());

        assertThat(buffer.isOverCapacity()).isFalse();
        assertThat(buffer.pending()).isZero();
    }

    @Test
    void anInvalidCapacityIsRejectedAtConstruction() {
        assertThatThrownBy(() -> new WriteBehindBuffer(0, FIXED))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WriteBehindBuffer(10, 0d, FIXED))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WriteBehindBuffer(10, 1.5d, FIXED))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
