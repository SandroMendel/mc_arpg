package rpg.core.combat;

import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * How often an attack counts (FR-020 to FR-023).
 *
 * <p>One timestamp per attacker, compared against the clock on access. No timer, no task, no expiry
 * list - at 200 players a task each would be 200 repeating tasks for a rule that is one subtraction
 * (Principle II).
 *
 * <p>A swing inside the window is <b>discarded</b>, not weakened. Weakening is what vanilla does,
 * and it makes spamming the mouse the dominant strategy; discarding makes {@code attackSpeed} mean
 * what it says.
 *
 * <p>The gap is derived from the attribute on every swing, so a changed attack speed applies to the
 * very next one without anything being rescheduled (FR-023).
 */
public final class AttackWindow {

    private final Map<UUID, Long> lastAttackAt = new ConcurrentHashMap<>();
    private final Clock clock;

    public AttackWindow(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Whether an attack counts right now, and if so records it.
     *
     * @param attackSpeed attacks per second, from the attacker's snapshot
     * @return {@code true} if the attack counts; {@code false} if it falls inside the window
     */
    public boolean tryAttack(UUID attackerId, double attackSpeed) {
        long now = clock.millis();
        long gap = minimumGapMillis(attackSpeed);
        Long previous = lastAttackAt.get(attackerId);
        if (previous != null && now - previous < gap) {
            return false;
        }
        lastAttackAt.put(attackerId, now);
        return true;
    }

    /** Whether an attack would count, without recording it. */
    public boolean canAttack(UUID attackerId, double attackSpeed) {
        Long previous = lastAttackAt.get(attackerId);
        return previous == null || clock.millis() - previous >= minimumGapMillis(attackSpeed);
    }

    /** Drops an attacker's timestamp - on logout, or when a creature is removed. */
    public void forget(UUID attackerId) {
        lastAttackAt.remove(attackerId);
    }

    /** How many attackers are currently tracked. For leak tests. */
    public int trackedCount() {
        return lastAttackAt.size();
    }

    /**
     * The minimum time between two counting attacks.
     *
     * <p>An attack speed at or below zero would mean "never attack again", which no configuration
     * allows but a misbehaving contributor could produce; it is treated as one attack per second so
     * the player is slowed rather than frozen.
     */
    static long minimumGapMillis(double attackSpeed) {
        if (!Double.isFinite(attackSpeed) || attackSpeed <= 0.0) {
            return 1000L;
        }
        return (long) (1000.0 / attackSpeed);
    }
}
