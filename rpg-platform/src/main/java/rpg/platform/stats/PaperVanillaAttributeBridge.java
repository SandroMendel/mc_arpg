package rpg.platform.stats;

import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Server;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import rpg.core.scheduler.EntityRef;
import rpg.core.scheduler.Scheduler;
import rpg.core.stats.VanillaAttributeBridge;

/**
 * Mirrors computed values onto vanilla attributes (FR-030 to FR-033).
 *
 * <p>The only place in B04 that touches Bukkit. Everything it does happens on the tick of the
 * holder it concerns, through the scheduler abstraction from B01 - never on whatever thread
 * happened to trigger the recalculation (Principle I).
 *
 * <h2>ADR-003 in practice</h2>
 *
 * <p>Maximum health stays pinned at {@value #VANILLA_MAX_HEALTH} and the displayed value becomes
 * {@code current / max * 20}. The hearts are therefore a percentage bar at every scale, whether the
 * holder has 100 or 2000 health.
 *
 * <p><b>Naming note:</b> ADR-003 and the block brief say {@code GENERIC_MAX_HEALTH}. That prefix
 * was dropped from the Bukkit attribute names; on Paper 26.2 the constants are {@code MAX_HEALTH},
 * {@code ATTACK_SPEED} and {@code MOVEMENT_SPEED}. Same attributes, current names.
 */
public final class PaperVanillaAttributeBridge implements VanillaAttributeBridge {

    /** Vanilla maximum, pinned. Ten hearts, twenty half-hearts (ADR-003). */
    public static final double VANILLA_MAX_HEALTH = 20.0;

    /**
     * Smallest displayable step: half a heart.
     *
     * <p>Used as the floor for a living holder (FR-031). Without it, someone at 0.4% health sees an
     * empty bar and believes they are dead - which is worse than a slightly optimistic display,
     * because it changes what they do next.
     */
    public static final double SMALLEST_VISIBLE_HEALTH = 0.5;

    private final Server server;
    private final Scheduler scheduler;
    private final Logger logger;

    public PaperVanillaAttributeBridge(Server server, Scheduler scheduler, Logger logger) {
        this.server = server;
        this.scheduler = scheduler;
        this.logger = logger;
    }

    @Override
    public void mirrorHealth(UUID holderId, double currentHealth, double maxHealth) {
        onTick(
                holderId,
                entity -> {
                    AttributeInstance max =
                            entity.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
                    if (max != null && max.getBaseValue() != VANILLA_MAX_HEALTH) {
                        max.setBaseValue(VANILLA_MAX_HEALTH);
                    }
                    entity.setHealth(displayedHealth(currentHealth, maxHealth));
                });
    }

    @Override
    public void mirrorAttackSpeed(UUID holderId, double value) {
        onTick(holderId, entity -> setBase(entity, org.bukkit.attribute.Attribute.ATTACK_SPEED, value));
    }

    @Override
    public void mirrorMovementSpeed(UUID holderId, double value) {
        onTick(
                holderId,
                entity -> setBase(entity, org.bukkit.attribute.Attribute.MOVEMENT_SPEED, value));
    }

    /**
     * The vanilla health value for a given share (ADR-003, FR-030, FR-031).
     *
     * <p>Static and free of Bukkit so the rule itself stays testable without a server.
     */
    public static double displayedHealth(double currentHealth, double maxHealth) {
        if (currentHealth <= 0.0) {
            return 0.0;
        }
        if (maxHealth <= 0.0) {
            return SMALLEST_VISIBLE_HEALTH;
        }
        double scaled = currentHealth / maxHealth * VANILLA_MAX_HEALTH;
        if (scaled > VANILLA_MAX_HEALTH) {
            return VANILLA_MAX_HEALTH;
        }
        return Math.max(SMALLEST_VISIBLE_HEALTH, scaled);
    }

    private void setBase(LivingEntity entity, org.bukkit.attribute.Attribute attribute, double value) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance != null && instance.getBaseValue() != value) {
            instance.setBaseValue(value);
        }
    }

    /**
     * Runs an action on the holder's own tick.
     *
     * <p>Entity-bound rather than global: it is the rule from Principle I and the reason ADR-007's
     * Folia path stays open. A holder that is not currently a living entity - offline, unloaded,
     * already dead - is skipped rather than treated as an error; that is the normal case during a
     * logout, not a fault.
     */
    private void onTick(UUID holderId, java.util.function.Consumer<LivingEntity> action) {
        scheduler.runSyncOnEntity(
                new EntityRef(holderId),
                () -> {
                    LivingEntity entity = resolve(holderId);
                    if (entity == null) {
                        return;
                    }
                    try {
                        action.accept(entity);
                    } catch (RuntimeException e) {
                        // Confined to this holder: a failure to mirror must not take the tick with
                        // it (Principle VI).
                        logger.log(
                                Level.WARNING,
                                "[stats] could not mirror vanilla attributes for " + holderId,
                                e);
                    }
                });
    }

    private LivingEntity resolve(UUID holderId) {
        Entity entity = server.getPlayer(holderId);
        if (entity == null) {
            entity = server.getEntity(holderId);
        }
        return entity instanceof LivingEntity living ? living : null;
    }
}
