package rpg.core.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import rpg.core.persistence.WriteBehindBufferCoalescingTest.MutableClock;

/**
 * T049: proves the assumption the whole capacity design rests on.
 *
 * <p>research.md argues that 50 000 entries is a genuine emergency brake rather than something
 * reached in normal operation, because the buffer grows with the number of <em>distinct</em>
 * aggregates touched, not with the duration of an outage. If that were wrong, FR-009b - disconnect
 * every player - would fire during ordinary outages, which would be a far worse outcome than the
 * one it protects against. So it is asserted rather than assumed.
 */
class WriteBehindBufferGrowthTest {

    private static final Instant T0 = Instant.parse("2026-08-19T12:00:00Z");

    @Test
    void aTenHourOutageWithTheSamePlayersHoldsNoMoreThanThosePlayers() {
        MutableClock clock = new MutableClock(T0);
        WriteBehindBuffer buffer = new WriteBehindBuffer(50_000, clock);

        String[] players = new String[200];
        for (int i = 0; i < players.length; i++) {
            players[i] = UUID.randomUUID().toString();
        }

        // Ten hours of play at a change per player per second, with nothing ever flushed.
        for (int second = 0; second < 10 * 60 * 60; second++) {
            for (String player : players) {
                buffer.mark(AggregateType.PLAYER_STATE, player);
            }
            clock.advance(Duration.ofSeconds(1));
        }

        // 7.2 million marking calls, 200 entries.
        assertThat(buffer.pending()).isEqualTo(200);
        assertThat(buffer.isOverCapacity()).isFalse();
        assertThat(buffer.status().fillRatio()).isLessThan(0.01d);
    }

    @Test
    void theBufferGrowsWithDistinctAggregatesNotWithTime() {
        MutableClock clock = new MutableClock(T0);
        WriteBehindBuffer buffer = new WriteBehindBuffer(50_000, clock);

        for (int i = 0; i < 5_000; i++) {
            buffer.mark(AggregateType.PLAYER_STATE, "player-" + i);
        }
        int afterDistinct = buffer.pending();

        // A lot more time and a lot more changes, but no new aggregates.
        for (int round = 0; round < 100; round++) {
            for (int i = 0; i < 5_000; i++) {
                buffer.mark(AggregateType.PLAYER_STATE, "player-" + i);
            }
            clock.advance(Duration.ofMinutes(1));
        }

        assertThat(afterDistinct).isEqualTo(5_000);
        assertThat(buffer.pending()).isEqualTo(5_000);
    }

    @Test
    void statisticsGrowPerPlayerMetricAndDayAsTheDataModelStates() {
        MutableClock clock = new MutableClock(T0);
        WriteBehindBuffer buffer = new WriteBehindBuffer(50_000, clock);

        // 200 players x 5 metrics x 2 days = 2000 distinct keys, regardless of event count.
        for (int event = 0; event < 50; event++) {
            for (int player = 0; player < 200; player++) {
                for (int metric = 0; metric < 5; metric++) {
                    for (int day = 0; day < 2; day++) {
                        buffer.mark(
                                AggregateType.STATISTICS,
                                "player-" + player + "|metric-" + metric + "|day-" + day);
                    }
                }
            }
        }

        assertThat(buffer.pending()).isEqualTo(2_000);
    }

    @Test
    void reachingCapacityIsPossibleButTakesGenuinelyManyDistinctAggregates() {
        // The brake does work - it just needs the pathological case it was designed for.
        WriteBehindBuffer buffer =
                new WriteBehindBuffer(100, Clock.fixed(T0, ZoneOffset.UTC));

        for (int i = 0; i < 100; i++) {
            buffer.mark(AggregateType.PLAYER_STATE, "player-" + i);
        }

        assertThat(buffer.isOverCapacity()).isTrue();
        assertThat(buffer.status().overCapacity()).isTrue();
    }
}
