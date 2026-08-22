package rpg.platform.ability;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;

import rpg.core.ability.effect.InvisibilityEffect;
import rpg.core.ability.effect.SummonEffect;
import rpg.core.scheduler.EntityRef;
import rpg.core.scheduler.Scheduler;
import rpg.core.stats.StatSnapshot;

/**
 * The Paper half of the two primitives that put something in the world: {@code SUMMON} and
 * {@code INVISIBILITY}.
 *
 * <p>They share this class because they share the shape - a state that is set, and one that is put
 * back after a duration - and because both leave a known gap that B10 will close.
 *
 * <p><b>Both use the entity-bound scheduler</b>, never the global one (ADR-007, Constitution I): the
 * clone's expiry is bound to the clone, the reappearance to the player. Neither is a recurring task;
 * each is one delayed call that runs once.
 */
public final class PaperSummons implements SummonEffect.Placer, InvisibilityEffect.Concealer {

    private final Server server;
    private final Scheduler scheduler;
    private final Logger logger;

    public PaperSummons(Server server, Scheduler scheduler, Logger logger) {
        this.server = Objects.requireNonNull(server, "server");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * Places a standing double of the summoner.
     *
     * <p>It is an armour stand rather than a mob on purpose: a mob would wander, fight back and be
     * pushed around, and the ability asks for none of that. It stands, it can be hit, and it goes
     * away.
     */
    @Override
    public Optional<UUID> place(
            UUID summonerId, StatSnapshot snapshot, double health, Duration lifetime) {
        Entity summoner = server.getEntity(summonerId);
        if (summoner == null) {
            return Optional.empty();
        }
        Location where = summoner.getLocation();
        Entity creature = where.getWorld().spawnEntity(where, EntityType.ARMOR_STAND);
        if (!(creature instanceof LivingEntity living)) {
            creature.remove();
            return Optional.empty();
        }
        applyHealth(living, health);
        living.setCustomName(summoner.getName());
        living.setCustomNameVisible(true);
        if (living instanceof Mob mob) {
            // It does not attack. That is the whole design of it (FR-016c).
            mob.setAware(false);
        }

        UUID creatureId = living.getUniqueId();
        scheduler.runSyncOnEntityDelayed(
                new EntityRef(creatureId),
                lifetime,
                () -> {
                    Entity still = server.getEntity(creatureId);
                    if (still != null) {
                        still.remove();
                    }
                });
        return Optional.of(creatureId);
    }

    @Override
    public void conceal(UUID holderId, Duration duration) {
        Entity entity = server.getEntity(holderId);
        if (!(entity instanceof LivingEntity living)) {
            return;
        }
        living.setInvisible(true);
        living.setInvulnerable(true);

        // Reappearing has to happen even if the ability ended early - the runtime's end cause will
        // call this back with a zero duration, and the delayed call is only the backstop.
        scheduler.runSyncOnEntityDelayed(
                new EntityRef(holderId),
                duration,
                () -> {
                    Entity still = server.getEntity(holderId);
                    if (still instanceof LivingEntity back) {
                        back.setInvisible(false);
                        back.setInvulnerable(false);
                    }
                });
    }

    /** Brings someone back into view immediately - used when the ability ends before its duration. */
    public void reveal(UUID holderId) {
        Entity entity = server.getEntity(holderId);
        if (entity instanceof LivingEntity living) {
            living.setInvisible(false);
            living.setInvulnerable(false);
        }
    }

    private void applyHealth(LivingEntity living, double health) {
        if (health <= 0.0) {
            return;
        }
        var attribute = living.getAttribute(Attribute.MAX_HEALTH);
        if (attribute == null) {
            logger.fine("[abilities] summoned entity has no health attribute; left at its default");
            return;
        }
        attribute.setBaseValue(health);
        living.setHealth(Math.min(health, attribute.getValue()));
    }
}
