package rpg.platform.stats;

import java.util.logging.Logger;

import org.bukkit.GameRules;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.world.WorldLoadEvent;

/**
 * Keeps vanilla from writing to the health bar B04 owns (FR-030a).
 *
 * <p>Without this, vanilla quietly heals the bar the engine just set: the hearts drift upward while
 * the actual health does not move, and the display is wrong from the first day of play. Turning off
 * natural regeneration and pinning saturation is the smallest thing that stops it.
 *
 * <p><b>Deliberately narrow.</b> This guard touches regeneration and food, and nothing else. It
 * registers no handler on {@code EntityDamageEvent}: redirecting fall, fire, lava or void damage is
 * B05's job and doing it here would quietly dissolve the block boundary (FR-030b, FR-042). There is
 * a test that asserts exactly that.
 */
public final class VanillaRegenerationGuard implements Listener {

    /**
     * Saturation is pinned here so hunger never falls, which is what would drive natural
     * regeneration and the sprint/exhaustion rules that go with it. Full, not zero: an empty food
     * bar has its own vanilla consequences.
     */
    public static final int PINNED_FOOD_LEVEL = 20;

    private final Logger logger;

    public VanillaRegenerationGuard(Logger logger) {
        this.logger = logger;
    }

    /** Applies the game rule to every world currently loaded. Called once after the bootstrap. */
    public void applyTo(Server server) {
        int changed = 0;
        for (World world : server.getWorlds()) {
            if (apply(world)) {
                changed++;
            }
        }
        logger.info(
                "[stats] natural regeneration disabled in "
                        + changed
                        + " world(s) - the health bar is B04's to write (ADR-003)");
    }

    /** A world loaded later gets the same treatment; otherwise it would regenerate on its own. */
    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        apply(event.getWorld());
    }

    /** Cancels vanilla regeneration; only the engine moves the bar (FR-030a). */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onRegainHealth(EntityRegainHealthEvent event) {
        switch (event.getRegainReason()) {
            case REGEN, SATIATED, EATING, MAGIC_REGEN -> event.setCancelled(true);
            default -> {
                // CUSTOM is how the engine itself would ever raise health; leave it alone.
            }
        }
    }

    /** Holds the food level steady so hunger never becomes a second health system. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player && event.getFoodLevel() < PINNED_FOOD_LEVEL) {
            event.setFoodLevel(PINNED_FOOD_LEVEL);
        }
    }

    private boolean apply(World world) {
        Boolean current = world.getGameRuleValue(GameRules.NATURAL_HEALTH_REGENERATION);
        if (Boolean.FALSE.equals(current)) {
            return false;
        }
        world.setGameRule(GameRules.NATURAL_HEALTH_REGENERATION, false);
        return true;
    }
}
