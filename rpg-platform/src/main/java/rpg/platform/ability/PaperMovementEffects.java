package rpg.platform.ability;

import java.util.Objects;
import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.RayTraceResult;
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
            if (!(caster instanceof LivingEntity living)) {
                return;
            }
            // WOHIN DER SPIELER SIEHT, nicht zu welcher Kreatur.
            //
            // Vorher nahm das hier das erste aufgeloeste Ziel und stellte sich hinter das naechste
            // Mob - was der Resolver fuer CURSOR liefert, weil CURSOR und NEAREST bei ihm dieselbe
            // Suche sind. Fuer eine Fluchtfaehigkeit ist das die falsche Richtung: sie brachte den
            // Rogue AN den Gegner statt weg von ihm, und ohne Kreatur in Sicht tat sie gar nichts.
            double range = context.ability().target() == null ? 20.0 : context.ability().target().range();
            Location eye = living.getEyeLocation();
            Vector direction = eye.getDirection().normalize();
            // Bloecke UND Kreaturen, und was zuerst kommt, gewinnt.
            //
            // Vorher wurden nur Bloecke geprueft. Wer auf ein Mob zielte, traf damit die Wand
            // DAHINTER - und landete auf dem Fleck des Mobs, was von aussen aussah, als tauschten die
            // beiden die Plaetze. Eine Fluchtfaehigkeit, die einen im Gegner absetzt, ist genau
            // verkehrt herum.
            RayTraceResult hit =
                    living.getWorld()
                            .rayTrace(
                                    eye,
                                    direction,
                                    range,
                                    FluidCollisionMode.NEVER,
                                    true,
                                    0.3,
                                    candidate -> !candidate.equals(living));

            Location destination;
            if (hit == null) {
                // Freie Sicht bis ans Ende der Reichweite: dorthin.
                destination = eye.clone().add(direction.clone().multiply(range));
            } else {
                // Ein guter Schritt VOR das Getroffene - eine Wand schiebt einen sonst in eine
                // beliebige Richtung wieder heraus, und in einem Mob zu stehen ist kein Ziel.
                double back = hit.getHitEntity() == null ? 0.5 : 1.5;
                destination = hit.getHitPosition().toLocation(living.getWorld());
                destination.subtract(direction.clone().multiply(back));
            }
            destination.setDirection(direction);
            caster.teleport(safe(destination));
        };
    }

    /**
     * Nach unten auf festen Boden, hoechstens ein paar Bloecke weit.
     *
     * <p>Ein Ziel in der Luft ist kein Fehler - wer nach oben zielt, will hoch -, aber ein Ziel, das
     * einen Block ueber dem Boden schwebt, sieht aus wie ein verunglueckter Sprung. Mehr als das
     * Naheliegende macht diese Suche nicht: wer in den Himmel zielt, faellt eben.
     */
    private static Location safe(Location destination) {
        Location probe = destination.clone();
        for (int step = 0; step < 3; step++) {
            if (!probe.clone().subtract(0.0, 1.0, 0.0).getBlock().isPassable()) {
                return probe;
            }
            probe.subtract(0.0, 1.0, 0.0);
        }
        return destination;
    }
}
