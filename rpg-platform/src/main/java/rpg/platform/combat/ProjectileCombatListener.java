package rpg.platform.combat;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileLaunchEvent;

import rpg.core.combat.DamageFormula;
import rpg.core.stats.Attribute;
import rpg.core.stats.StatEngine;

/**
 * Prices a projectile at launch (FR-024a, FR-024b).
 *
 * <p>Without this, bows would be silently useless: FR-016 zeroes every vanilla damage, arrows
 * included, and nothing would replace it. A whole style of combat would stop working on day one
 * without any requirement having said so.
 *
 * <p>The damage is worked out from the shooter's values <b>now</b>, at launch - not on impact. A
 * shooter who swaps their sword mid-flight does not retroactively change the arrow.
 */
public final class ProjectileCombatListener implements Listener {

    private final StatEngine stats;

    public ProjectileCombatListener(StatEngine stats) {
        this.stats = stats;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent event) {
        Projectile projectile = event.getEntity();
        if (!(projectile.getShooter() instanceof LivingEntity shooter)) {
            return;
        }
        stats.findSnapshot(shooter.getUniqueId())
                .ifPresent(
                        snapshot ->
                                ProjectileDamageTag.write(
                                        projectile,
                                        DamageFormula.rawDamage(
                                                snapshot.get(Attribute.PHYSICAL_DAMAGE), 1.0)));
    }
}
