package rpg.core.combat;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import rpg.core.event.EventBus;
import rpg.core.session.SessionNotReadyException;
import rpg.core.session.SessionRegistry;
import rpg.core.stats.Attribute;
import rpg.core.stats.ResourceView;
import rpg.core.stats.StatEngine;
import rpg.core.stats.StatSnapshot;

/**
 * The combat pipeline (FR-007 to FR-010).
 *
 * <p>Six stages, one interception point each, and one reused damage context per thread. Everything
 * about this class is shaped by one number: at 150 players against 800 mobs this runs thousands of
 * times per second, and it has to fit inside a tick alongside everything else.
 *
 * <p>Three consequences worth stating outright:
 *
 * <ul>
 *   <li>Nothing here schedules anything. Attack window, combat state and contribution age are
 *       timestamps read on access; a tick without a hit costs nothing because nothing is running.
 *   <li>Nothing here allocates per hit except the snapshot the engine hands out. The context is
 *       reused, the attribution window is a fixed array, rejections are cached instances.
 *   <li>Nothing here touches the database. B05 has none.
 * </ul>
 */
public final class DefaultCombatPipeline implements CombatPipeline {

    private final StatEngine stats;
    private final EventBus eventBus;
    private final Logger logger;
    private final Clock clock;

    /** Optional: without it, no holder is subject to B03's readiness rule (FR-046). */
    private final SessionRegistry sessions;

    private final AttackWindow attackWindow;
    private final CombatState combatState;
    private final AttributionWindow attribution;
    private final DamageAggregator aggregator;

    private volatile CombatConfig config;
    private volatile DamagePermission permission = DamagePermission.defaultRule();
    private volatile DamageFeedback feedback;
    private volatile MobStatProvider mobStatProvider;

    private final Map<PipelineStage, List<DamageInterceptor>> interceptors =
            new EnumMap<>(PipelineStage.class);

    /**
     * One context per thread, reused across hits (FR-045).
     *
     * <p>Combat runs on the tick, so in practice this is a single instance. Binding it to the thread
     * rather than to the pipeline keeps the reuse sound if anything ever calls in from elsewhere.
     */
    private final ThreadLocal<DamageContext> contexts = ThreadLocal.withInitial(DamageContext::new);

    /** Targets already reported dead, so a second lethal hit in the same tick does nothing. */
    private final Map<UUID, Boolean> dead = new java.util.concurrent.ConcurrentHashMap<>();

    public DefaultCombatPipeline(
            CombatConfig config,
            StatEngine stats,
            EventBus eventBus,
            SessionRegistry sessions,
            Clock clock,
            Logger logger) {
        this.config = Objects.requireNonNull(config, "config");
        this.stats = Objects.requireNonNull(stats, "stats");
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
        this.sessions = sessions;
        this.clock = Objects.requireNonNull(clock, "clock");
        this.logger = Objects.requireNonNull(logger, "logger");

        this.attackWindow = new AttackWindow(clock);
        this.combatState = new CombatState(clock, config.combatTimeout());
        this.attribution =
                new AttributionWindow(clock, config.maxAttackers(), config.attributionTimeout());
        this.aggregator = new DamageAggregator(clock, config.aggregationWindow());

        for (PipelineStage stage : PipelineStage.values()) {
            interceptors.put(stage, new java.util.concurrent.CopyOnWriteArrayList<>());
        }
    }

    // ------------------------------------------------------------- dealing damage

    @Override
    public DamageResult meleeAttack(UUID attackerId, UUID targetId) {
        return attack(
                attackerId, targetId, DamageType.PHYSICAL, DamageOrigin.MELEE, 1.0, 0.0, false);
    }

    @Override
    public DamageResult abilityDamage(UUID attackerId, UUID targetId, DamageType type, double factor) {
        return attack(attackerId, targetId, type, DamageOrigin.ABILITY, factor, 0.0, false);
    }

    @Override
    public DamageResult projectileDamage(UUID shooterId, UUID targetId, double rawDamage) {
        return attack(
                shooterId,
                targetId,
                DamageType.PHYSICAL,
                DamageOrigin.PROJECTILE,
                1.0,
                rawDamage,
                true);
    }

    @Override
    public DamageResult environmentDamage(UUID targetId, EnvironmentSource source) {
        return environment(targetId, source, config.environmentDamageOf(source));
    }

    @Override
    public DamageResult fallDamage(UUID targetId, double fallenBlocks) {
        double amount = DamageFormula.fallDamage(fallenBlocks, config.fallDamage());
        return amount <= 0.0
                ? DamageResult.of(RejectReason.INVALID_DAMAGE)
                : environment(targetId, EnvironmentSource.FALL, amount);
    }

    @Override
    public void kill(UUID targetId, DeathCause cause) {
        Objects.requireNonNull(targetId, "targetId");
        if (dead.putIfAbsent(targetId, Boolean.TRUE) != null) {
            return;
        }
        stats.findSnapshot(targetId)
                .ifPresent(snapshot -> stats.changeHealth(targetId, -Double.MAX_VALUE / 2));
        publishDeath(targetId, null, cause);
    }

    // ------------------------------------------------------------- the pipeline

    /**
     * One combat damage event, end to end.
     *
     * @param presetRaw a raw damage figure worked out elsewhere - a projectile carries one from the
     *     moment it was fired
     * @param hasPreset whether {@code presetRaw} means anything.
     *     <p>A separate flag rather than a sentinel value. {@code Double.NaN} used to serve as the
     *     sentinel, which made "no preset" and "a preset that is broken" the same thing: a projectile
     *     carrying NaN silently got full attribute-based damage instead of being neutralised. There is
     *     no {@code double} that cannot arrive as data, so no {@code double} can be the sentinel.
     */
    private DamageResult attack(
            UUID attackerId,
            UUID targetId,
            DamageType type,
            DamageOrigin origin,
            double factor,
            double presetRaw,
            boolean hasPreset) {
        Objects.requireNonNull(targetId, "targetId");

        if (!hasPreset && !DamageFormula.isUsable(factor)) {
            // Refused, not thrown. B08 will call this from inside an ability, and an exception
            // escaping the pipeline would take that ability down with it - FR-006 says reject and
            // log, and FR-010 keeps a fault local.
            logger.warning(
                    "[combat] refused damage factor "
                            + factor
                            + " from "
                            + attackerId
                            + " - not a usable value; a negative hit is not healing (FR-006)");
            return DamageResult.of(RejectReason.INVALID_DAMAGE);
        }

        // --- SOURCE -------------------------------------------------------
        if (Boolean.TRUE.equals(dead.get(targetId))) {
            return DamageResult.of(RejectReason.ALREADY_DEAD);
        }
        Optional<StatSnapshot> targetStats = stats.findSnapshot(targetId);
        Optional<StatSnapshot> attackerStats =
                attackerId == null ? Optional.empty() : stats.findSnapshot(attackerId);
        if (targetStats.isEmpty() || (attackerId != null && attackerStats.isEmpty())) {
            // Not part of this combat system - an ordinary animal, for instance (FR-018).
            return DamageResult.of(RejectReason.NO_HOLDER);
        }
        if (!isSessionReady(attackerId) || !isSessionReady(targetId)) {
            return DamageResult.of(RejectReason.SESSION_NOT_READY);
        }
        if (!permission.isAllowed(attackerId, isPlayer(attackerId), targetId, isPlayer(targetId))) {
            return DamageResult.of(RejectReason.NOT_PERMITTED);
        }
        if (origin == DamageOrigin.MELEE
                && !attackWindow.tryAttack(
                        attackerId, attackerStats.get().get(Attribute.ATTACK_SPEED))) {
            return DamageResult.of(RejectReason.ATTACK_TOO_SOON);
        }

        DamageContext context = contexts.get();
        try {
            context.begin(
                    attackerId,
                    targetId,
                    type,
                    origin,
                    null,
                    factor,
                    attackerStats.orElse(null),
                    targetStats.get());

            if (!run(context, PipelineStage.SOURCE)) {
                return DamageResult.of(RejectReason.CANCELLED);
            }

            // --- RAW_DAMAGE ------------------------------------------------
            context.advanceTo(PipelineStage.RAW_DAMAGE);
            double raw =
                    hasPreset
                            ? presetRaw
                            : DamageFormula.rawDamage(
                                    attackerStats.get().get(type.basis()), factor);
            if (!DamageFormula.isUsable(raw)) {
                logger.warning(
                        "[combat] refused raw damage " + raw + " from " + attackerId + " - not a"
                                + " usable value; a negative hit is not healing (FR-006)");
                return DamageResult.of(RejectReason.INVALID_DAMAGE);
            }
            context.setRawDamage(raw);
            if (!run(context, PipelineStage.RAW_DAMAGE)) {
                return DamageResult.of(RejectReason.CANCELLED);
            }

            // --- MODIFIERS -------------------------------------------------
            context.advanceTo(PipelineStage.MODIFIERS);
            if (!run(context, PipelineStage.MODIFIERS)) {
                return DamageResult.of(RejectReason.CANCELLED);
            }

            // --- DEFENCE ---------------------------------------------------
            context.advanceTo(PipelineStage.DEFENCE);
            double afterDefence =
                    type.defenceApplies()
                            ? DamageFormula.afterDefence(
                                    context.rawDamage(),
                                    targetStats.get().get(Attribute.DEFENSE))
                            : context.rawDamage();
            context.setFinalDamage(afterDefence);
            if (!run(context, PipelineStage.DEFENCE)) {
                return DamageResult.of(RejectReason.CANCELLED);
            }

            return applyAndFinish(context);
        } finally {
            context.reset();
        }
    }

    /** Environmental damage takes the same path, minus attacker, attack window and defence. */
    private DamageResult environment(UUID targetId, EnvironmentSource source, double amount) {
        Objects.requireNonNull(targetId, "targetId");
        if (Boolean.TRUE.equals(dead.get(targetId))) {
            return DamageResult.of(RejectReason.ALREADY_DEAD);
        }
        Optional<StatSnapshot> targetStats = stats.findSnapshot(targetId);
        if (targetStats.isEmpty()) {
            return DamageResult.of(RejectReason.NO_HOLDER);
        }
        if (!isSessionReady(targetId)) {
            return DamageResult.of(RejectReason.SESSION_NOT_READY);
        }
        if (!DamageFormula.isUsable(amount) || amount == 0.0) {
            return DamageResult.of(RejectReason.INVALID_DAMAGE);
        }

        DamageContext context = contexts.get();
        try {
            context.begin(
                    null,
                    targetId,
                    DamageType.ENVIRONMENT,
                    DamageOrigin.ENVIRONMENT,
                    source,
                    1.0,
                    null,
                    targetStats.get());

            if (!run(context, PipelineStage.SOURCE)) {
                return DamageResult.of(RejectReason.CANCELLED);
            }
            context.advanceTo(PipelineStage.RAW_DAMAGE);
            context.setRawDamage(amount);
            if (!run(context, PipelineStage.RAW_DAMAGE)) {
                return DamageResult.of(RejectReason.CANCELLED);
            }
            context.advanceTo(PipelineStage.MODIFIERS);
            if (!run(context, PipelineStage.MODIFIERS)) {
                return DamageResult.of(RejectReason.CANCELLED);
            }
            // DEFENCE is entered but does nothing: environment damage ignores defence (FR-012b).
            context.advanceTo(PipelineStage.DEFENCE);
            context.setFinalDamage(context.rawDamage());
            if (!run(context, PipelineStage.DEFENCE)) {
                return DamageResult.of(RejectReason.CANCELLED);
            }

            return applyAndFinish(context);
        } finally {
            context.reset();
        }
    }

    /** APPLICATION and AFTERMATH, shared by both paths. */
    private DamageResult applyAndFinish(DamageContext context) {
        UUID targetId = context.targetId();
        UUID attackerId = context.attackerId().orElse(null);
        double damage = context.finalDamage();
        DamageType type = context.type();

        // --- APPLICATION ---------------------------------------------------
        context.advanceTo(PipelineStage.APPLICATION);
        if (!run(context, PipelineStage.APPLICATION)) {
            return DamageResult.of(RejectReason.CANCELLED);
        }

        double remaining = stats.changeHealth(targetId, -damage);
        attribution.record(targetId, attackerId, damage);
        markCombat(targetId);
        if (attackerId != null) {
            markCombat(attackerId);
        }

        // --- AFTERMATH -----------------------------------------------------
        context.advanceTo(PipelineStage.AFTERMATH);
        run(context, PipelineStage.AFTERMATH);

        boolean lethal = remaining <= 0.0;
        DamageFeedback target = feedback;
        if (target != null && !lethal) {
            target.playHurtAnimation(targetId);
            if (attackerId != null) {
                target.applyKnockback(targetId, attackerId, config.knockbackStrength());
            }
        }

        DamageDealtEvent closed = aggregator.record(attackerId, targetId, type, damage);
        if (closed != null) {
            eventBus.publish(closed);
        }

        if (lethal && dead.putIfAbsent(targetId, Boolean.TRUE) == null) {
            aggregator.closeFor(targetId).forEach(eventBus::publish);
            publishDeath(
                    targetId,
                    attackerId,
                    attackerId != null ? DeathCause.COMBAT : DeathCause.ENVIRONMENT);
        }
        return DamageResult.applied(damage, lethal);
    }

    /**
     * Runs the interception points of one stage, each behind a fault barrier.
     *
     * @return {@code false} if the event was cancelled
     */
    private boolean run(DamageContext context, PipelineStage stage) {
        for (DamageInterceptor interceptor : interceptors.get(stage)) {
            try {
                interceptor.intercept(context);
            } catch (RuntimeException failure) {
                // Confined to this event: one broken interceptor must not cost the tick or other
                // fights (FR-010) - same barrier as B01's modules and B04's contributors.
                logger.log(
                        Level.WARNING,
                        "[combat] interceptor '"
                                + interceptor.id()
                                + "' failed at stage "
                                + stage
                                + "; continuing without it",
                        failure);
            }
            if (context.isCancelled()) {
                return false;
            }
        }
        return !context.isCancelled();
    }

    private void publishDeath(UUID targetId, UUID killerId, DeathCause cause) {
        DamageShare shares = attribution.consume(targetId);
        UUID characterId = stats.characterIdOf(targetId).orElse(null);
        eventBus.publish(
                new CombatDeathEvent(
                        targetId, characterId, killerId, cause, shares, characterId != null));
    }

    private void markCombat(UUID holderId) {
        if (combatState.markInCombat(holderId)) {
            eventBus.publish(
                    new CombatStateChangedEvent(
                            holderId, stats.characterIdOf(holderId).orElse(null), true));
        }
    }

    /**
     * B03's readiness rule (FR-046).
     *
     * <p>Only for holders with a session. A creature has none and must not fail on one - the same
     * distinction B04 draws.
     */
    private boolean isSessionReady(UUID holderId) {
        if (sessions == null || holderId == null || !isPlayer(holderId)) {
            return true;
        }
        return sessions.isReady(holderId);
    }

    /**
     * Whether this holder is a player character.
     *
     * <p>Asked of the stat engine, not of the session registry. The registry answers "is a session
     * loaded", which is a different question and gives the wrong answer twice: a mob would look like
     * a player wherever no registry is wired, and a player between sessions would look like a mob.
     */
    private boolean isPlayer(UUID holderId) {
        return holderId != null && stats.characterIdOf(holderId).isPresent();
    }

    // ------------------------------------------------------------- reading

    @Override
    public boolean isInCombat(UUID holderId) {
        return combatState.isInCombat(holderId);
    }

    @Override
    public Optional<Duration> remainingCombatTime(UUID holderId) {
        return combatState.remaining(holderId);
    }

    @Override
    public boolean canAttackNow(UUID attackerId) {
        return stats.findSnapshot(attackerId)
                .map(snapshot -> attackWindow.canAttack(attackerId, snapshot.get(Attribute.ATTACK_SPEED)))
                .orElse(false);
    }

    @Override
    public Optional<DamageShare> currentShares(UUID targetId) {
        DamageShare share = attribution.shareOf(targetId);
        return share.isEmpty() ? Optional.empty() : Optional.of(share);
    }

    // ------------------------------------------------------------- registration

    @Override
    public void registerInterceptor(DamageInterceptor interceptor) {
        Objects.requireNonNull(interceptor, "interceptor");
        interceptors.get(interceptor.stage()).add(interceptor);
    }

    @Override
    public void setMobStatProvider(MobStatProvider provider) {
        this.mobStatProvider = provider;
    }

    /** The current mob stat supply, for the platform listener that equips creatures. */
    public Optional<MobStatProvider> mobStatProvider() {
        return Optional.ofNullable(mobStatProvider);
    }

    @Override
    public void registerFeedback(DamageFeedback feedback) {
        this.feedback = feedback;
    }

    @Override
    public void setPermission(DamagePermission permission) {
        this.permission = Objects.requireNonNull(permission, "permission");
    }

    /** Replaces the configuration; the time-based rules keep their existing windows. */
    public void reload(CombatConfig newConfig) {
        this.config = Objects.requireNonNull(newConfig, "newConfig");
    }

    // ------------------------------------------------------------- lifecycle

    @Override
    public void forget(UUID holderId) {
        attackWindow.forget(holderId);
        combatState.forget(holderId);
        attribution.forget(holderId);
        aggregator.forget(holderId);
        dead.remove(holderId);
    }

    /**
     * Publishes the leaving edge for holders whose combat expired.
     *
     * <p>Called from wherever the state is already being read - not from a task (FR-030d).
     */
    public void publishExpiredCombatStates() {
        for (UUID holderId : combatState.drainExpired()) {
            eventBus.publish(
                    new CombatStateChangedEvent(
                            holderId, isPlayer(holderId) ? holderId : null, false));
        }
    }

    /** Resource reading for the platform side. */
    public Optional<ResourceView> resources(UUID holderId) {
        try {
            return Optional.of(stats.resources(holderId));
        } catch (RuntimeException notAvailable) {
            return Optional.empty();
        }
    }

    /** Counters for the leak tests: attack window, combat state, attribution, aggregation. */
    public int[] trackedCounts() {
        return new int[] {
            attackWindow.trackedCount(),
            combatState.trackedCount(),
            attribution.trackedCount(),
            aggregator.openWindowCount()
        };
    }

    /** Marks a target alive again - after a respawn. */
    @Override
    public void clearDeathMark(UUID targetId) {
        dead.remove(targetId);
    }

    /** All registered interceptors of one stage, for diagnostics. */
    public List<DamageInterceptor> interceptorsAt(PipelineStage stage) {
        return new ArrayList<>(interceptors.get(stage));
    }
}
