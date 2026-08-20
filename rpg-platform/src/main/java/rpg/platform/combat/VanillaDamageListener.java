package rpg.platform.combat;

import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

import rpg.core.combat.CombatPipeline;
import rpg.core.combat.DeathCause;
import rpg.core.combat.EnvironmentSource;
import rpg.platform.combat.VanillaDamageMapping.Mapping;

/**
 * Catches every vanilla damage event and lets nothing through (FR-016 to FR-019).
 *
 * <p>The order matters and is not obvious: the vanilla damage is set to <b>zero</b> rather than the
 * event being cancelled. A cancelled event skips too much - no hurt animation, no knockback, and on
 * some paths no follow-up at all. Zeroed damage keeps the event flowing while making sure vanilla's
 * number never reaches anyone, and this block then applies its own and triggers the feedback
 * explicitly (FR-017).
 *
 * <p>Runs at {@link EventPriority#HIGHEST} so that anything another plugin might do has already
 * happened, and {@code ignoreCancelled = false} so a cancellation elsewhere does not hide a source
 * from the mapping.
 */
public final class VanillaDamageListener implements Listener {

    private final CombatPipeline pipeline;
    private final VanillaDamageMapping mapping;
    private final Logger logger;

    public VanillaDamageListener(
            CombatPipeline pipeline, VanillaDamageMapping mapping, Logger logger) {
        this.pipeline = pipeline;
        this.mapping = mapping;
        this.logger = logger;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity target)) {
            return;
        }

        Mapping treatment = mapping.resolve(event.getCause());

        // Whatever happens next, vanilla's number is not it (FR-016).
        event.setDamage(0.0);

        switch (treatment.treatment()) {
            case DISABLED -> event.setCancelled(true);

            case LETHAL -> {
                event.setCancelled(true);
                pipeline.kill(
                        target.getUniqueId(),
                        event.getCause() == EntityDamageEvent.DamageCause.VOID
                                ? DeathCause.VOID
                                : DeathCause.ADMIN);
            }

            case MAPPED -> {
                EnvironmentSource source = treatment.environmentSource().orElseThrow();
                if (source == EnvironmentSource.FALL) {
                    pipeline.fallDamage(target.getUniqueId(), target.getFallDistance());
                } else {
                    pipeline.environmentDamage(target.getUniqueId(), source);
                }
                // Vanilla's invulnerability ticks are a second, hidden attack window: they would
                // quietly cap attack speed at two hits per second (research.md E6).
                target.setNoDamageTicks(0);
            }

            case COMBAT -> handleCombat(event, target);
        }
    }

    private void handleCombat(EntityDamageEvent event, LivingEntity target) {
        if (!(event instanceof EntityDamageByEntityEvent byEntity)) {
            // Combat damage without an attacker is not something this block knows how to price.
            event.setCancelled(true);
            return;
        }

        UUID attackerId = attackerOf(byEntity.getDamager());
        if (attackerId == null) {
            event.setCancelled(true);
            return;
        }

        if (byEntity.getDamager() instanceof Projectile projectile) {
            double raw = ProjectileDamageTag.read(projectile);
            if (Double.isNaN(raw)) {
                // Not one of ours - a dispenser arrow, for instance. Neutralised, nothing applied.
                event.setCancelled(true);
                return;
            }
            pipeline.projectileDamage(attackerId, target.getUniqueId(), raw);
        } else {
            pipeline.meleeAttack(attackerId, target.getUniqueId());
        }
        target.setNoDamageTicks(0);
    }

    /** The holder behind a damager: the shooter for a projectile, the entity itself otherwise. */
    private UUID attackerOf(Entity damager) {
        if (damager instanceof Projectile projectile) {
            return projectile.getShooter() instanceof Entity shooter ? shooter.getUniqueId() : null;
        }
        return damager instanceof LivingEntity living ? living.getUniqueId() : null;
    }
}
