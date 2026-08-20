package rpg.platform.combat;

import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Server;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import rpg.core.combat.DamageFeedback;
import rpg.core.scheduler.EntityRef;
import rpg.core.scheduler.Scheduler;

/**
 * Hurt animation and knockback (FR-037).
 *
 * <p>Both are needed because the vanilla event was zeroed: a hit that changes a number without any
 * visible reaction reads as a miss, and combat built on that feels broken regardless of how correct
 * the arithmetic is.
 *
 * <p>Everything runs on the tick of the entity it concerns, through B01's scheduler abstraction
 * (Principle I).
 */
public final class PaperDamageFeedback implements DamageFeedback {

    private final Server server;
    private final Scheduler scheduler;
    private final Logger logger;

    public PaperDamageFeedback(Server server, Scheduler scheduler, Logger logger) {
        this.server = server;
        this.scheduler = scheduler;
        this.logger = logger;
    }

    @Override
    public void playHurtAnimation(UUID targetId) {
        onTick(targetId, target -> target.playHurtAnimation(0.0f));
    }

    @Override
    public void applyKnockback(UUID targetId, UUID sourceId, double strength) {
        if (strength <= 0.0) {
            return;
        }
        onTick(
                targetId,
                target -> {
                    LivingEntity source = resolve(sourceId);
                    if (source == null) {
                        return;
                    }
                    // Direction from source to target, as vanilla does it.
                    double dx = target.getLocation().getX() - source.getLocation().getX();
                    double dz = target.getLocation().getZ() - source.getLocation().getZ();
                    target.knockback(strength, -dx, -dz);
                });
    }

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
                    } catch (RuntimeException failure) {
                        // Confined to this holder: failing to show a hit must not take the tick
                        // with it (Principle VI).
                        logger.log(
                                Level.WARNING,
                                "[combat] could not play feedback for " + holderId,
                                failure);
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
