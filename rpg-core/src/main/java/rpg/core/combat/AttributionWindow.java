package rpg.core.combat;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who has been hitting a target, bounded in both count and age (FR-031 to FR-036).
 *
 * <p>Three parallel arrays of fixed size per target. Linear search over at most 16 slots is faster
 * than any map here, because the whole thing sits in one cache line - and it allocates nothing on
 * the hot path, which a map per mob at 800 mobs certainly would.
 *
 * <p>Two bounds, both required by the block brief:
 *
 * <ul>
 *   <li><b>Count</b> - when the array is full the smallest contribution is evicted (FR-032). An
 *       unbounded attacker list on a horde server is a slow leak with no upper limit.
 *   <li><b>Age</b> - a slot older than the timeout is treated as free (FR-033), checked on access
 *       rather than by a sweeping task.
 * </ul>
 *
 * <p>Memory: roughly 512 bytes per target, about 400 KB at 800 mobs. It does not grow.
 */
public final class AttributionWindow {

    private final Map<UUID, Slots> byTarget = new ConcurrentHashMap<>();
    private final Clock clock;
    private final int capacity;
    private final long timeoutMillis;

    public AttributionWindow(Clock clock, int capacity, java.time.Duration timeout) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.capacity = capacity;
        this.timeoutMillis = Objects.requireNonNull(timeout, "timeout").toMillis();
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be at least 1, but was " + capacity);
        }
    }

    /**
     * Records a contribution.
     *
     * <p>Self damage is not recorded (FR-035) - otherwise a holder killed by its own explosion would
     * be the top contributor to its own death and collect the loot.
     */
    public void record(UUID targetId, UUID attackerId, double damage) {
        if (attackerId == null || attackerId.equals(targetId) || damage <= 0.0) {
            return;
        }
        byTarget.computeIfAbsent(targetId, id -> new Slots(capacity))
                .add(attackerId, damage, clock.millis(), timeoutMillis);
    }

    /** The split as it stands, without clearing anything. */
    public DamageShare shareOf(UUID targetId) {
        Slots slots = byTarget.get(targetId);
        return slots == null ? DamageShare.empty() : slots.toShare(clock.millis(), timeoutMillis);
    }

    /**
     * The split at the moment of death, releasing the window as it goes (FR-036).
     *
     * <p>The one place in this block where an allocation per event is accepted: a death is rare, and
     * the result outlives the event.
     */
    public DamageShare consume(UUID targetId) {
        Slots slots = byTarget.remove(targetId);
        return slots == null ? DamageShare.empty() : slots.toShare(clock.millis(), timeoutMillis);
    }

    /** Drops a target's window - on death, on removal, on chunk unload (FR-036). */
    public void forget(UUID targetId) {
        byTarget.remove(targetId);
    }

    /** How many targets currently have a window. For leak tests. */
    public int trackedCount() {
        return byTarget.size();
    }

    /** How many attackers a target currently has recorded, ignoring expired slots. */
    public int attackerCount(UUID targetId) {
        Slots slots = byTarget.get(targetId);
        return slots == null ? 0 : slots.liveCount(clock.millis(), timeoutMillis);
    }

    /** The fixed-size store for one target. */
    private static final class Slots {

        private final UUID[] attackers;
        private final double[] damage;
        private final long[] lastAt;

        Slots(int capacity) {
            this.attackers = new UUID[capacity];
            this.damage = new double[capacity];
            this.lastAt = new long[capacity];
        }

        synchronized void add(UUID attackerId, double amount, long now, long timeoutMillis) {
            int free = -1;
            int smallest = -1;

            for (int i = 0; i < attackers.length; i++) {
                UUID current = attackers[i];
                if (current == null) {
                    if (free < 0) {
                        free = i;
                    }
                    continue;
                }
                if (now - lastAt[i] >= timeoutMillis) {
                    // Expired: the slot is free, and the contribution behind it no longer counts.
                    attackers[i] = null;
                    damage[i] = 0.0;
                    if (free < 0) {
                        free = i;
                    }
                    continue;
                }
                if (current.equals(attackerId)) {
                    damage[i] += amount;
                    lastAt[i] = now;
                    return;
                }
                if (smallest < 0 || damage[i] < damage[smallest]) {
                    smallest = i;
                }
            }

            int slot = free >= 0 ? free : smallest;
            attackers[slot] = attackerId;
            damage[slot] = amount;
            lastAt[slot] = now;
        }

        synchronized int liveCount(long now, long timeoutMillis) {
            int count = 0;
            for (int i = 0; i < attackers.length; i++) {
                if (attackers[i] != null && now - lastAt[i] < timeoutMillis) {
                    count++;
                }
            }
            return count;
        }

        synchronized DamageShare toShare(long now, long timeoutMillis) {
            double total = 0.0;
            for (int i = 0; i < attackers.length; i++) {
                if (attackers[i] != null && now - lastAt[i] < timeoutMillis) {
                    total += damage[i];
                }
            }
            if (total <= 0.0) {
                return DamageShare.empty();
            }

            Map<UUID, Double> shares = new HashMap<>();
            UUID top = null;
            double topDamage = -1.0;
            for (int i = 0; i < attackers.length; i++) {
                if (attackers[i] == null || now - lastAt[i] >= timeoutMillis) {
                    continue;
                }
                shares.put(attackers[i], damage[i] / total);
                if (damage[i] > topDamage) {
                    topDamage = damage[i];
                    top = attackers[i];
                }
            }
            return new DamageShare(shares, top, total);
        }
    }
}
