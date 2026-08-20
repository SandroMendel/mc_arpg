package rpg.core.combat;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Whether a holder is in combat (FR-030c to FR-030f).
 *
 * <p>Lives here rather than in B08 because only this block sees every hit. B08 already decided that
 * mana regeneration is reduced during combat; if the state lived there, B12 and B13 would each build
 * their own counter and there would be three answers to one question.
 *
 * <p>Same shape as {@link AttackWindow}: one timestamp per holder, evaluated on access. Leaving
 * combat is therefore noticed at the next read rather than announced by a timer - which is
 * immaterial to the one known consumer, because B08's regeneration is timestamp-based itself.
 */
public final class CombatState {

    private final Map<UUID, Long> lastCombatAt = new ConcurrentHashMap<>();

    /** Holders currently reported as in combat, so the leaving edge can be detected. */
    private final Map<UUID, Boolean> announced = new ConcurrentHashMap<>();

    private final Clock clock;
    private final Duration timeout;

    public CombatState(Clock clock, Duration timeout) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
    }

    /**
     * Records combat activity for a holder.
     *
     * @return {@code true} if this call moved the holder <em>into</em> combat, so the caller can
     *     publish the change exactly once
     */
    public boolean markInCombat(UUID holderId) {
        lastCombatAt.put(holderId, clock.millis());
        return announced.put(holderId, Boolean.TRUE) == null;
    }

    /** Whether this holder counts as in combat right now. */
    public boolean isInCombat(UUID holderId) {
        Long last = lastCombatAt.get(holderId);
        return last != null && clock.millis() - last < timeout.toMillis();
    }

    /** How much longer, or empty if not in combat. */
    public Optional<Duration> remaining(UUID holderId) {
        Long last = lastCombatAt.get(holderId);
        if (last == null) {
            return Optional.empty();
        }
        long elapsed = clock.millis() - last;
        long left = timeout.toMillis() - elapsed;
        return left > 0 ? Optional.of(Duration.ofMillis(left)) : Optional.empty();
    }

    /**
     * Holders whose combat has expired since the last check, clearing them as it goes.
     *
     * <p>Called from the same place that already evaluates the state - not from a task. It walks
     * only the holders that were ever in combat, which at any moment is a small set.
     */
    public java.util.List<UUID> drainExpired() {
        java.util.List<UUID> expired = new java.util.ArrayList<>();
        long now = clock.millis();
        for (Map.Entry<UUID, Long> entry : lastCombatAt.entrySet()) {
            if (now - entry.getValue() >= timeout.toMillis()) {
                UUID holderId = entry.getKey();
                if (announced.remove(holderId) != null) {
                    expired.add(holderId);
                }
                lastCombatAt.remove(holderId);
            }
        }
        return expired;
    }

    /** Drops a holder entirely - on logout, or when a creature is removed. */
    public void forget(UUID holderId) {
        lastCombatAt.remove(holderId);
        announced.remove(holderId);
    }

    /** How many holders are currently tracked. For leak tests. */
    public int trackedCount() {
        return lastCombatAt.size();
    }
}
