package rpg.core.combat;

import java.util.Optional;
import java.util.UUID;

import rpg.core.stats.StatSnapshot;

/**
 * What an interception point gets to see and change (FR-008).
 *
 * <p><b>Valid only for the duration of the call that hands it out.</b> The underlying damage event
 * is reused across hits so that this block does not allocate per hit (FR-045); an interceptor that
 * stores the view and reads it later would be reading another fight's numbers.
 *
 * <p>That is not left to discipline: every method throws {@link IllegalStateException} once the
 * event has finished. A stale read fails loudly at the place that caused it, rather than quietly
 * producing wrong damage somewhere else.
 */
public interface DamageView {

    /** The attacker, or empty for environmental damage. */
    Optional<UUID> attackerId();

    /** The target. Never absent. */
    UUID targetId();

    DamageType type();

    DamageOrigin origin();

    /** The environmental hazard, or empty for combat damage. */
    Optional<EnvironmentSource> environmentSource();

    /** The share of the base attribute this attack uses; 1.0 for a melee swing (FR-002a). */
    double factor();

    /** Damage before defence. Meaningful from {@link PipelineStage#RAW_DAMAGE} onwards. */
    double rawDamage();

    /** Damage after defence. Meaningful from {@link PipelineStage#DEFENCE} onwards. */
    double finalDamage();

    /** The attacker's values from the moment this event started (FR-005). */
    Optional<StatSnapshot> attackerSnapshot();

    /** The target's values. */
    StatSnapshot targetSnapshot();

    /** Which stage is running right now. */
    PipelineStage stage();

    /** Whether an interception point has cancelled this event. */
    boolean isCancelled();

    // ------------------------------------------------------------- changing

    /** Replaces the raw damage. Refuses values that are not finite. */
    void setRawDamage(double value);

    /** Replaces the damage after defence. Refuses values that are not finite. */
    void setFinalDamage(double value);

    /**
     * Cancels the event (FR-009).
     *
     * <p>A cancelled event produces no damage, no hurt animation and no attribution - it is as if
     * the swing never happened.
     */
    void cancel();
}
