package rpg.core.combat;

import java.util.Optional;
import java.util.UUID;

import rpg.core.stats.StatSnapshot;

/**
 * One damage event, from source to aftermath - reused across hits (FR-045, research.md E2).
 *
 * <p>At 150 players against 800 mobs this runs thousands of times per second. One object per hit is
 * rubbish the tick pays for, so there is one context per thread and it is reset between events.
 *
 * <p>The price of reuse is a trap: a stage that keeps the context past its event sees another
 * fight's data, and the bug shows up as wrong damage somewhere entirely different. That is why
 * {@link #active} exists - once the event finishes, every {@link DamageView} accessor throws instead
 * of answering. The failure lands at the line that caused it.
 *
 * <p>Not thread-safe by design. Combat runs on the tick, and one context per thread is what makes
 * the reuse sound.
 */
public final class DamageContext implements DamageView {

    private UUID attackerId;
    private UUID targetId;
    private DamageType type;
    private DamageOrigin origin;
    private EnvironmentSource environmentSource;
    private double factor;
    private double rawDamage;
    private double finalDamage;
    private StatSnapshot attackerSnapshot;
    private StatSnapshot targetSnapshot;
    private PipelineStage stage;
    private boolean cancelled;

    /** False once the event has finished; guards every accessor against a stale holder. */
    private boolean active;

    /**
     * Prepares this context for a new event.
     *
     * @param factor share of the base attribute; 1.0 for a melee swing
     */
    public void begin(
            UUID attackerId,
            UUID targetId,
            DamageType type,
            DamageOrigin origin,
            EnvironmentSource environmentSource,
            double factor,
            StatSnapshot attackerSnapshot,
            StatSnapshot targetSnapshot) {
        this.attackerId = attackerId;
        this.targetId = targetId;
        this.type = type;
        this.origin = origin;
        this.environmentSource = environmentSource;
        this.factor = factor;
        this.attackerSnapshot = attackerSnapshot;
        this.targetSnapshot = targetSnapshot;
        this.rawDamage = 0.0;
        this.finalDamage = 0.0;
        this.stage = PipelineStage.SOURCE;
        this.cancelled = false;
        this.active = true;
    }

    /** Ends the event and invalidates every view handed out during it. */
    public void reset() {
        this.active = false;
        this.attackerId = null;
        this.targetId = null;
        this.type = null;
        this.origin = null;
        this.environmentSource = null;
        this.attackerSnapshot = null;
        this.targetSnapshot = null;
        this.factor = 0.0;
        this.rawDamage = 0.0;
        this.finalDamage = 0.0;
        this.stage = null;
        this.cancelled = false;
    }

    void advanceTo(PipelineStage next) {
        requireActive();
        this.stage = next;
    }

    /** Whether this context currently describes a running event. */
    public boolean isActive() {
        return active;
    }

    // --------------------------------------------------------- DamageView

    @Override
    public Optional<UUID> attackerId() {
        requireActive();
        return Optional.ofNullable(attackerId);
    }

    @Override
    public UUID targetId() {
        requireActive();
        return targetId;
    }

    @Override
    public DamageType type() {
        requireActive();
        return type;
    }

    @Override
    public DamageOrigin origin() {
        requireActive();
        return origin;
    }

    @Override
    public Optional<EnvironmentSource> environmentSource() {
        requireActive();
        return Optional.ofNullable(environmentSource);
    }

    @Override
    public double factor() {
        requireActive();
        return factor;
    }

    @Override
    public double rawDamage() {
        requireActive();
        return rawDamage;
    }

    @Override
    public double finalDamage() {
        requireActive();
        return finalDamage;
    }

    @Override
    public Optional<StatSnapshot> attackerSnapshot() {
        requireActive();
        return Optional.ofNullable(attackerSnapshot);
    }

    @Override
    public StatSnapshot targetSnapshot() {
        requireActive();
        return targetSnapshot;
    }

    @Override
    public PipelineStage stage() {
        requireActive();
        return stage;
    }

    @Override
    public boolean isCancelled() {
        requireActive();
        return cancelled;
    }

    @Override
    public void setRawDamage(double value) {
        requireActive();
        requireFinite("rawDamage", value);
        this.rawDamage = value;
    }

    @Override
    public void setFinalDamage(double value) {
        requireActive();
        requireFinite("finalDamage", value);
        this.finalDamage = value;
    }

    @Override
    public void cancel() {
        requireActive();
        this.cancelled = true;
    }

    private void requireActive() {
        if (!active) {
            throw new IllegalStateException(
                    "this DamageView belongs to a finished damage event - it is only valid for the"
                            + " duration of the call that handed it out. Read what you need while"
                            + " you are called; the context behind it is reused for the next hit.");
        }
    }

    private static void requireFinite(String field, double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(field + " must be finite, but was " + value);
        }
    }
}
