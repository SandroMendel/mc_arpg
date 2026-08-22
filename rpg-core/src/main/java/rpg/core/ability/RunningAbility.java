package rpg.core.ability;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import rpg.core.scheduler.TaskHandle;

/**
 * One ability a character currently has going - either winding up or running (ADR-025).
 *
 * <p><b>Two phases, one object.</b> Keeping a cast and a sustained ability apart looked tidier and
 * was wrong: the same ability is first the one and then the other, the handle and the reserved mana
 * carry across, and two objects would have meant handing state from one to the other at exactly the
 * moment a mistake is invisible.
 *
 * <p>Which phase it is in decides what an interruption costs, and that is the whole of the two-phase
 * rule (FR-045d, FR-045e):
 *
 * <pre>
 *   WINDING_UP  - cast time, or the aim before a leap. Cancelling refunds and starts no cooldown.
 *   RUNNING     - the whirl is spinning, the shield is absorbing. Ending keeps the cost and starts
 *                 the cooldown.
 * </pre>
 *
 * @param characterId whose it is
 * @param abilityId what is running
 * @param phase where it is
 * @param startedAt when it began
 * @param dueAt when it takes effect (winding up) or ends (running)
 * @param reservedMana what was booked at the start, to be refunded if it never takes effect
 * @param task the scheduled one-shot that ends this phase
 */
public record RunningAbility(
        UUID characterId,
        String abilityId,
        Phase phase,
        Instant startedAt,
        Instant dueAt,
        double reservedMana,
        TaskHandle task) {

    /** Which half of the two-phase rule applies. */
    public enum Phase {
        /** Not in effect yet. Cancelling costs nothing (FR-045d). */
        WINDING_UP,

        /** In effect. Ending early keeps the cost and starts the cooldown (FR-045e). */
        RUNNING
    }

    public RunningAbility {
        Objects.requireNonNull(characterId, "characterId");
        Objects.requireNonNull(abilityId, "abilityId");
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(startedAt, "startedAt");
        Objects.requireNonNull(dueAt, "dueAt");
        if (reservedMana < 0.0) {
            throw new IllegalArgumentException("reservedMana must not be negative");
        }
    }

    public boolean isWindingUp() {
        return phase == Phase.WINDING_UP;
    }

    /**
     * The same ability, now in effect and ending at {@code endsAt}.
     *
     * <p>There is no way back (FR-045f): the leap is unabortable from the moment of the jump, and the
     * lightning from the moment it is cast.
     */
    public RunningAbility running(Instant endsAt, TaskHandle endTask) {
        return new RunningAbility(
                characterId, abilityId, Phase.RUNNING, startedAt, endsAt, reservedMana, endTask);
    }

    /** Stops the scheduled one-shot, if there still is one. */
    public void cancelTask() {
        if (task != null) {
            task.cancel();
        }
    }

    /** How far along, in {@code [0, 1]} - what B13 draws a bar from. */
    public double progress(Instant now) {
        long total = java.time.Duration.between(startedAt, dueAt).toMillis();
        if (total <= 0L) {
            return 1.0;
        }
        long done = java.time.Duration.between(startedAt, now).toMillis();
        return Math.max(0.0, Math.min(1.0, (double) done / total));
    }
}
