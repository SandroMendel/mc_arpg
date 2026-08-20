package rpg.platform.combat;

import java.util.logging.Logger;

import org.bukkit.GameRules;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.world.WorldLoadEvent;

import rpg.core.combat.CombatPipeline;
import rpg.core.stats.Attribute;
import rpg.core.stats.ResourcePool;
import rpg.core.stats.StatEngine;
import rpg.core.stats.StatSnapshot;

/**
 * What happens around a death (FR-029 to FR-030b).
 *
 * <p>Three separate jobs, each with its own reason:
 *
 * <ul>
 *   <li><b>Vanilla experience and loot are suppressed.</b> Progress comes from B06 and loot tables
 *       from B11; leaving vanilla's in place would run two progression systems side by side, one of
 *       which fills an experience bar that does nothing.
 *   <li><b>{@code keep_inventory} is on.</b> Vanilla drops everything on death, and next to losing a
 *       whole inventory the chosen penalty - equipment damage, applied by B11 - would be
 *       meaningless. Two penalties where one was decided, and the unintended one hides the intended
 *       one.
 *   <li><b>Respawn refills health and mana.</b> Coming back at partial values would be a second
 *       penalty, and the penalty was settled as equipment damage.
 * </ul>
 *
 * <p>What this deliberately does <b>not</b> do: touch equipment. That is B11's, on the strength of
 * the death event this block publishes (FR-030).
 */
public final class CombatDeathListener implements Listener {

    private final StatEngine stats;
    private final CombatPipeline pipeline;
    private final Logger logger;

    public CombatDeathListener(StatEngine stats, CombatPipeline pipeline, Logger logger) {
        this.stats = stats;
        this.pipeline = pipeline;
        this.logger = logger;
    }

    /** Applies the game rule to every loaded world. Called once after the bootstrap. */
    public void applyTo(Server server) {
        int changed = 0;
        for (World world : server.getWorlds()) {
            if (apply(world)) {
                changed++;
            }
        }
        logger.info(
                "[combat] inventory kept on death in "
                        + changed
                        + " world(s) - the death penalty is equipment damage (B11), not item loss");
    }

    /** A world loaded later gets the same treatment. */
    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        apply(event.getWorld());
    }

    /** Vanilla experience and loot are not this game's (FR-030a, FR-030b). */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDeath(EntityDeathEvent event) {
        event.setDroppedExp(0);
        event.getDrops().clear();
    }

    /** A respawned player is whole again (FR-029). */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        var playerId = event.getPlayer().getUniqueId();
        pipeline.clearDeathMark(playerId);
        stats.findSnapshot(playerId).ifPresent(snapshot -> refill(playerId, snapshot));
    }

    private void refill(java.util.UUID playerId, StatSnapshot snapshot) {
        stats.restoreResources(
                playerId,
                ResourcePool.full(snapshot.get(Attribute.HEALTH), snapshot.get(Attribute.MANA)));
    }

    private boolean apply(World world) {
        Boolean current = world.getGameRuleValue(GameRules.KEEP_INVENTORY);
        if (Boolean.TRUE.equals(current)) {
            return false;
        }
        world.setGameRule(GameRules.KEEP_INVENTORY, true);
        return true;
    }
}
