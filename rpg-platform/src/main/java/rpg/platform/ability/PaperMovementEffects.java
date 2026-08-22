package rpg.platform.ability;

import java.util.Objects;
import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;

import rpg.core.ability.effect.AbilityEffect;
import rpg.core.ability.effect.EffectContext;

/**
 * The three primitives that move something: dash, knockback and teleport.
 *
 * <p>All of them are one impulse or one reposition, and all of them need the world - which is why
 * they live here rather than in {@code rpg-core}. What they do <em>not</em> do is decide who they act
 * on: the resolver already did that.
 *
 * <p>Each returns quietly when the entity is gone. An ability that fired at something which
 * despawned in the same tick is an ordinary race, not an error.
 */
public final class PaperMovementEffects {

    private final Server server;
    private final Logger logger;

    public PaperMovementEffects(Server server, Logger logger) {
        this.server = Objects.requireNonNull(server, "server");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /** A push in the caster's view direction - the warrior's Leap. */
    public AbilityEffect dash() {
        return context -> {
            Entity caster = server.getEntity(context.casterId());
            if (caster == null) {
                return;
            }
            Vector direction = caster.getLocation().getDirection().normalize();
            // A little upward lift, or the dash scrapes along the floor and stops at the first slab.
            caster.setVelocity(direction.multiply(context.value()).setY(Math.max(0.35, direction.getY())));
        };
    }

    /** A push away from the caster - what the whirl does at its edge. */
    public AbilityEffect knockback() {
        return context -> {
            Entity caster = server.getEntity(context.casterId());
            if (caster == null) {
                return;
            }
            Location from = caster.getLocation();
            for (UUID targetId : context.targets()) {
                Entity target = server.getEntity(targetId);
                if (target == null || targetId.equals(context.casterId())) {
                    continue;
                }
                Vector away = target.getLocation().toVector().subtract(from.toVector());
                if (away.lengthSquared() == 0.0) {
                    // Standing exactly inside the caster. Any direction would be arbitrary, so none.
                    continue;
                }
                target.setVelocity(away.normalize().multiply(context.value()).setY(0.35));
            }
        };
    }

    /**
     * An instant reposition to the target - the rogue's Teleport.
     *
     * <p>Lands <em>next to</em> the target rather than inside it: arriving in the same block pushes
     * both of them apart in whatever direction the collision solver picks, which reads as a bug.
     */
    public AbilityEffect teleport() {
        return context -> {
            Entity caster = server.getEntity(context.casterId());
            if (caster == null || context.targets().isEmpty()) {
                return;
            }
            Entity target = server.getEntity(context.targets().get(0));
            if (target == null) {
                return;
            }
            Location destination = target.getLocation();
            Vector back = destination.getDirection().normalize().multiply(-1.0);
            caster.teleport(destination.clone().add(back).setDirection(destination.getDirection()));
        };
    }
}
