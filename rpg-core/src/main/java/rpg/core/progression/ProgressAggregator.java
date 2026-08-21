package rpg.core.progression;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sums experience gains inside a window so B13 gets one message instead of a thousand (FR-023a).
 *
 * <p><b>The same pattern as {@code DamageAggregator} in B05</b>, deliberately and not by accident:
 * B05 already answered this question for damage numbers, and a second answer would be a second thing
 * that can break plus a second pattern somebody has to learn. The window closes when the next gain
 * arrives after it elapsed, when a level-up happens, or when the session ends - <b>never</b> by a
 * task (FR-061). A window nobody touches again simply stops.
 *
 * <p><b>A level-up flushes first</b> (FR-023c). Otherwise an older bundle could arrive after the
 * level-up event and make the progress bar jump backwards - a bug that only shows up on a player and
 * then refuses to reproduce.
 */
public final class ProgressAggregator {

    private static final class Bucket {
        long openedAt;
        long sum;
    }

    private final Clock clock;
    private final long windowMillis;
    private final Map<UUID, Bucket> buckets = new ConcurrentHashMap<>();

    public ProgressAggregator(Clock clock, Duration window) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.windowMillis = Objects.requireNonNull(window, "window").toMillis();
    }

    /**
     * Records a gain.
     *
     * @return the summed amount if this gain closed a window, or 0 while it stays open
     */
    public long record(UUID characterId, long amount) {
        Objects.requireNonNull(characterId, "characterId");
        if (amount <= 0L) {
            return 0L;
        }
        long now = clock.millis();
        Bucket bucket = buckets.computeIfAbsent(characterId, id -> new Bucket());
        synchronized (bucket) {
            if (bucket.sum == 0L) {
                bucket.openedAt = now;
                bucket.sum = amount;
                return 0L;
            }
            if (now - bucket.openedAt >= windowMillis) {
                long closed = bucket.sum + amount;
                bucket.sum = 0L;
                bucket.openedAt = now;
                return closed;
            }
            bucket.sum += amount;
            return 0L;
        }
    }

    /**
     * Closes the window now and returns what it held, or 0 if it was empty.
     *
     * <p>Called before a level-up event so the messages arrive in the order they happened (FR-023c).
     */
    public long flush(UUID characterId) {
        Bucket bucket = buckets.get(characterId);
        if (bucket == null) {
            return 0L;
        }
        synchronized (bucket) {
            long held = bucket.sum;
            bucket.sum = 0L;
            bucket.openedAt = clock.millis();
            return held;
        }
    }

    /**
     * Drops everything held for a character.
     *
     * <p>An open bundle is <b>discarded, not delivered</b>: it is presentation only, and the
     * recipient is already gone. The experience itself was credited long before and gets written.
     */
    public void release(UUID characterId) {
        buckets.remove(characterId);
    }

    /** How many windows are held. For leak tests. */
    public int openWindows() {
        return buckets.size();
    }
}
