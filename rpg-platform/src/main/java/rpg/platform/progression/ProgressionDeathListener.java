package rpg.platform.progression;

import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Server;
import org.bukkit.entity.Entity;

import rpg.core.combat.CombatDeathEvent;
import rpg.core.event.EventBus;
import rpg.core.event.Subscription;
import rpg.core.progression.WorldPoint;
import rpg.core.progression.XpDistributor;

/**
 * Turns a death from B05 into distributed experience (FR-008).
 *
 * <p><b>Not a Bukkit listener.</b> B05 publishes {@code CombatDeathEvent} on the core event bus, and
 * it does so <em>synchronously</em> while Bukkit's own death handling is still running. That timing
 * is what makes the two Bukkit lookups here safe: the creature still exists, so its type and its
 * location can be read.
 *
 * <p><b>The location is read here and passed as a value</b>, never looked up later from the id. The
 * creature is dead: {@code Server.getEntity} stops finding it as soon as it is removed, and a
 * proximity check that resolved the id itself would work in testing and fail in play. If the lookup
 * fails even here, the distribution falls back to crediting the contributor alone rather than
 * guessing (FR-044).
 */
public final class ProgressionDeathListener {

    private final Server server;
    private final XpDistributor distributor;
    private final Logger logger;

    private Subscription subscription;

    public ProgressionDeathListener(Server server, XpDistributor distributor, Logger logger) {
        this.server = Objects.requireNonNull(server, "server");
        this.distributor = Objects.requireNonNull(distributor, "distributor");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /** Subscribes to the core bus. Called once at startup. */
    public void subscribeTo(EventBus events) {
        Objects.requireNonNull(events, "events");
        subscription = events.subscribe(CombatDeathEvent.class, this::onDeath);
    }

    public void unsubscribe() {
        if (subscription != null) {
            subscription.close();
            subscription = null;
        }
    }

    private void onDeath(CombatDeathEvent death) {
        if (death.playerVictim()) {
            return;
        }
        try {
            Entity victim = server.getEntity(death.victimId());
            String typeKey = victim == null ? "UNKNOWN" : victim.getType().name();
            WorldPoint origin = originOf(victim);
            distributor.distribute(death, typeKey, origin);
        } catch (RuntimeException failure) {
            // A fault here must not take the death handling of B05 down with it (FR-059).
            logger.log(
                    Level.WARNING,
                    "[progression] distributing experience for " + death.victimId() + " failed",
                    failure);
        }
    }

    private WorldPoint originOf(Entity victim) {
        if (victim == null) {
            // Already removed - rarer than it sounds, because B05 publishes inside the death
            // handling, but not impossible. Without an origin the split credits the contributor
            // alone instead of inventing a position.
            return null;
        }
        var location = victim.getLocation();
        var world = location.getWorld();
        if (world == null) {
            return null;
        }
        return new WorldPoint(world.getUID(), location.getX(), location.getY(), location.getZ());
    }
}
