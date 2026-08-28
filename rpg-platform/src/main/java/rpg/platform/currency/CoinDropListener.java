package rpg.platform.currency;

import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;

import rpg.core.combat.CombatDeathEvent;
import rpg.core.currency.BookingReason;
import rpg.core.currency.CoinDropPlan;
import rpg.core.currency.CoinDropPlanner;
import rpg.core.currency.Currency;
import rpg.core.event.EventBus;
import rpg.core.event.Subscription;
import rpg.core.progression.WorldPoint;

/**
 * Turns a death from B05 into coin piles (FR-019, FR-020).
 *
 * <p><b>Not a Bukkit listener.</b> B05 publishes {@code CombatDeathEvent} on the core event bus, and
 * it does so <em>synchronously</em> while Bukkit's own death handling is still running. That timing
 * is what makes the two lookups here safe: the creature still exists, so its type and its location
 * can be read. Modelled on {@code ProgressionDeathListener}, which solved the same problem for
 * experience.
 *
 * <p><b>The place is read here and passed as a value</b>, never looked up later from the id
 * (ADR-015 point 6). The creature is dead: {@code Server.getEntity} stops finding it as soon as it
 * is removed, and a drop that resolved the id itself would work in testing and fail in play.
 *
 * <p><b>When the cap has no room, the coins are credited directly instead of dropped</b> (FR-030).
 * The cap exists because of <em>entities</em>, not because of amounts - a credit is an addition and
 * costs no tick. A player must never lose coins to server load.
 */
public final class CoinDropListener {

    private final Server server;
    private final CoinDropPlanner planner;
    private final CoinPile piles;
    private final CoinPileRegistry registry;
    private final Currency currency;
    private final Logger logger;

    private Subscription subscription;

    public CoinDropListener(
            Server server,
            CoinDropPlanner planner,
            CoinPile piles,
            CoinPileRegistry registry,
            Currency currency,
            Logger logger) {
        this.server = Objects.requireNonNull(server, "server");
        this.planner = Objects.requireNonNull(planner, "planner");
        this.piles = Objects.requireNonNull(piles, "piles");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.currency = Objects.requireNonNull(currency, "currency");
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
            Entity creature = server.getEntity(death.victimId());
            if (creature == null) {
                // Already removed. Without a type and a place there is nothing to drop, and
                // guessing either would be worse than dropping nothing.
                return;
            }
            String mobTypeKey = creature.getType().name();
            WorldPoint origin = pointOf(creature.getLocation());

            List<CoinDropPlan> plans = planner.planFor(death, mobTypeKey, origin);
            for (CoinDropPlan plan : plans) {
                realise(plan);
            }
        } catch (RuntimeException failure) {
            // A failure here must not take B05's death handling with it (Constitution VI).
            logger.log(Level.WARNING, "[currency] could not drop coins for a death", failure);
        }
    }

    private void realise(CoinDropPlan plan) {
        java.util.Optional<Item> pile = piles.drop(plan, registry);
        if (pile.isPresent()) {
            registry.register(pile.get(), plan.characterId());
            return;
        }
        // No room, or no world. Credit rather than let the coins vanish - the pile is the gesture,
        // the coins are the point.
        currency.credit(plan.characterId(), plan.amount(), BookingReason.PILE_CASHED_IN);
    }

    private WorldPoint pointOf(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        return new WorldPoint(
                location.getWorld().getUID(), location.getX(), location.getY(), location.getZ());
    }
}
