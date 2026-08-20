package rpg.core.combat;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sums hits into one display event per attacker-target pair (FR-038, FR-040).
 *
 * <p>The aggregation lives here rather than in B13 because it is damage logic: what counts as "one
 * blow" is a combat question, and B13 should get a number to draw, not a firehose to filter.
 *
 * <p>Timestamp-based like everything else in this block - the window is closed on the next hit or
 * on death, never by a task (Principle II). A window that nobody touches again simply stops
 * existing when its target is forgotten.
 */
public final class DamageAggregator {

    /** Key for one attacker-target pair. Environmental damage uses a null attacker. */
    private record Pair(UUID attackerId, UUID targetId) {}

    private static final class Bucket {
        double total;
        int hits;
        long openedAt;
        DamageType type;
    }

    private final Map<Pair, Bucket> open = new ConcurrentHashMap<>();
    private final Clock clock;
    private final long windowMillis;

    public DamageAggregator(Clock clock, Duration window) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.windowMillis = Objects.requireNonNull(window, "window").toMillis();
    }

    /**
     * Adds one hit.
     *
     * @return the event to publish if this hit closed a window, or {@code null} while it stays open
     */
    public DamageDealtEvent record(UUID attackerId, UUID targetId, DamageType type, double damage) {
        if (damage <= 0.0) {
            // A hit that did nothing is not worth an event (FR-040).
            return null;
        }
        Pair key = new Pair(attackerId, targetId);
        long now = clock.millis();

        Bucket bucket = open.computeIfAbsent(key, k -> newBucket(now, type));
        synchronized (bucket) {
            if (now - bucket.openedAt >= windowMillis) {
                DamageDealtEvent closed = toEvent(key, bucket, false);
                bucket.total = damage;
                bucket.hits = 1;
                bucket.openedAt = now;
                bucket.type = type;
                return closed;
            }
            bucket.total += damage;
            bucket.hits++;
            return null;
        }
    }

    /**
     * Closes every window for a target - called when it dies, so the last hits are not left hanging
     * until a window that will never see another hit expires.
     */
    public List<DamageDealtEvent> closeFor(UUID targetId) {
        List<DamageDealtEvent> events = new ArrayList<>();
        open.entrySet()
                .removeIf(
                        entry -> {
                            if (!entry.getKey().targetId().equals(targetId)) {
                                return false;
                            }
                            synchronized (entry.getValue()) {
                                events.add(toEvent(entry.getKey(), entry.getValue(), true));
                            }
                            return true;
                        });
        return events;
    }

    /** Drops everything for a target without publishing - on unload. */
    public void forget(UUID targetId) {
        open.entrySet().removeIf(entry -> entry.getKey().targetId().equals(targetId));
    }

    /** How many windows are currently open. For leak tests. */
    public int openWindowCount() {
        return open.size();
    }

    private Bucket newBucket(long now, DamageType type) {
        Bucket bucket = new Bucket();
        bucket.openedAt = now;
        bucket.type = type;
        return bucket;
    }

    private static DamageDealtEvent toEvent(Pair key, Bucket bucket, boolean lethal) {
        return new DamageDealtEvent(
                key.attackerId(), key.targetId(), bucket.type, bucket.total, bucket.hits, lethal);
    }
}
