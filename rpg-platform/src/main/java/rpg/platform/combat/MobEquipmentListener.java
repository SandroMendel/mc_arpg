package rpg.platform.combat;

import java.util.logging.Logger;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRemoveEvent;

import rpg.core.combat.CombatPipeline;
import rpg.core.combat.MobStatProvider;
import rpg.core.stats.Attribute;
import rpg.core.stats.ResourcePool;
import rpg.core.stats.StatEngine;
import rpg.core.stats.StatSnapshot;

/**
 * Gives hostile creatures stat holders, and takes them away again (FR-019a, FR-019d, FR-019e).
 *
 * <p>Without this the whole pipeline applies to nothing but players: FR-018 leaves creatures without
 * a stat holder alone, and no block hands them out. B05 would be finished, fully tested and invisible
 * in the game - the failure class ADR-012 exists for. The load test the block is judged by (150
 * against 800) would not be possible either.
 *
 * <p>Releasing on death <b>and</b> on removal is not belt and braces: a creature that despawns or
 * whose chunk unloads never dies, and at 800 concurrent mobs a holder that outlives its creature is
 * a leak that grows for as long as the server runs.
 */
public final class MobEquipmentListener implements Listener {

    private final StatEngine stats;
    private final CombatPipeline pipeline;
    private final MobStatProvider provider;
    private final Logger logger;

    public MobEquipmentListener(
            StatEngine stats, CombatPipeline pipeline, MobStatProvider provider, Logger logger) {
        this.stats = stats;
        this.pipeline = pipeline;
        this.provider = provider;
        this.logger = logger;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        equip(event.getEntity());
    }

    /** Public so the plugin can equip creatures that were already loaded when it started. */
    public void equip(LivingEntity entity) {
        if (entity instanceof Player || !isHostile(entity)) {
            // A sheep is not part of the combat system (FR-019e).
            return;
        }
        if (stats.findSnapshot(entity.getUniqueId()).isPresent()) {
            return;
        }
        provider.statsFor(entity.getType().name())
                .ifPresent(
                        set -> {
                            stats.createForEntity(entity.getUniqueId());
                            stats.apply(entity.getUniqueId(), set);
                            StatSnapshot snapshot = stats.recalculateNow(entity.getUniqueId());
                            stats.restoreResources(
                                    entity.getUniqueId(),
                                    ResourcePool.full(
                                            snapshot.get(Attribute.HEALTH),
                                            snapshot.get(Attribute.MANA)));
                        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent event) {
        release(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRemove(EntityRemoveEvent event) {
        if (event.getEntity() instanceof LivingEntity living) {
            release(living);
        }
    }

    private void release(LivingEntity entity) {
        if (entity instanceof Player) {
            return; // players are B03's to unload
        }
        pipeline.forget(entity.getUniqueId());
        stats.remove(entity.getUniqueId());
    }

    /**
     * What counts as hostile.
     *
     * <p>Vanilla's own classification, deliberately: a classification of this block's own would
     * already be a mob definition, and those are B10's.
     */
    private boolean isHostile(Entity entity) {
        return entity instanceof Monster || (entity instanceof Mob mob && mob.getTarget() != null);
    }

    /** Whether this listener would equip the given entity. Exposed for the tests. */
    public boolean wouldEquip(Entity entity) {
        return !(entity instanceof Player) && isHostile(entity);
    }
}
