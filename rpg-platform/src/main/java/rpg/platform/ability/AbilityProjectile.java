package rpg.platform.ability;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Server;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.SmallFireball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.util.Vector;

import rpg.core.ability.effect.ProjectileEffect;
import rpg.core.combat.CombatPipeline;

/**
 * The Paper half of {@link ProjectileEffect}: throws it, and settles it when it arrives.
 *
 * <p><b>The payload is held here, not on the entity.</b> A projectile in flight is looked up by its
 * id in one map; storing the values in persistent data would mean serialising a {@code StatSnapshot}
 * onto an entity that lives for half a second.
 *
 * <p><b>The map is swept, not trusted.</b> A projectile that flies into unloaded chunks, is removed
 * by a plugin or simply despawns never fires {@code ProjectileHitEvent}, and its entry would sit
 * there forever. {@link #sweep()} drops what is no longer in the world - one pass, driven from
 * outside like every other sweep in this codebase.
 *
 * <p>The damage goes through {@link CombatPipeline#abilityDamage} like every other source, and it
 * goes through with the caster named even if the caster has since left. That is the point of
 * carrying the snapshot: the pipeline gets values, not a lookup.
 */
public final class AbilityProjectile implements Listener, ProjectileEffect.Launcher {

    private final Server server;
    private final CombatPipeline pipeline;
    private final Logger logger;

    /** In flight: projectile id to what it is carrying. */
    private final Map<UUID, ProjectileEffect.Payload> inFlight = new ConcurrentHashMap<>();

    public AbilityProjectile(Server server, CombatPipeline pipeline, Logger logger) {
        this.server = Objects.requireNonNull(server, "server");
        this.pipeline = Objects.requireNonNull(pipeline, "pipeline");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public void launch(ProjectileEffect.Payload payload) {
        Entity caster = server.getEntity(payload.casterId());
        if (!(caster instanceof LivingEntity thrower)) {
            return;
        }
        Vector direction = thrower.getEyeLocation().getDirection().normalize();
        SmallFireball ball = thrower.launchProjectile(SmallFireball.class, direction.multiply(payload.speed()));
        // Vanilla's own fire damage on top of the pipeline's would be damage from two sources for one
        // hit, and only one of them obeys the rules.
        ball.setIsIncendiary(false);
        ball.setYield(0.0f);
        inFlight.put(ball.getUniqueId(), payload);
    }

    /** Settles the hit: the values from the throw, applied where it landed. */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        ProjectileEffect.Payload payload = inFlight.remove(projectile.getUniqueId());
        if (payload == null) {
            return;
        }
        // A projectile that hits a block did its flight and is done. Only a body takes damage.
        if (!(event.getHitEntity() instanceof LivingEntity target)) {
            return;
        }
        try {
            pipeline.abilityDamage(
                    payload.casterId(), target.getUniqueId(), payload.damageType(), payload.amount());
        } catch (RuntimeException failure) {
            logger.log(
                    Level.WARNING,
                    failure,
                    () -> "[abilities] projectile of " + payload.abilityId() + " failed on impact");
        }
    }

    /**
     * Drops entries for projectiles that are no longer in the world.
     *
     * <p>Without this the map only ever grows: despawning and chunk unloading are silent, and a
     * server running for a week would accumulate one dead entry per lost fireball.
     *
     * @return how many were dropped
     */
    public int sweep() {
        int before = inFlight.size();
        inFlight.keySet().removeIf(id -> server.getEntity(id) == null);
        return before - inFlight.size();
    }

    /** How many are in flight. For the leak test. */
    public int inFlightCount() {
        return inFlight.size();
    }
}
