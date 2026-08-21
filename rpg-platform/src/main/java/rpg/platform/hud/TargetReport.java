package rpg.platform.hud;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.Server;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import rpg.core.combat.CombatMessageKeys;
import rpg.core.combat.DamageDealtEvent;
import rpg.core.event.EventBus;
import rpg.core.message.Messages;
import rpg.core.scheduler.EntityRef;
import rpg.core.scheduler.Scheduler;

/**
 * A chat line about what the player just hit: how much is left of it, and how well it is armoured.
 *
 * <p>Hangs off {@link DamageDealtEvent}, which B05 already aggregates over a window of half a second.
 * That matters more than it looks: without the aggregation a fast weapon would write several lines per
 * second into the chat, and the readout would be the reason players turn it off. One line per window
 * reports the summed damage and the hit count behind it.
 *
 * <p>Named for what it is rather than {@code HudRenderer}, for the same reason as
 * {@link StatusActionBar}: that name belongs to B13.
 */
public final class TargetReport {

    private final Server server;
    private final CombatStatusSource status;
    private final Scheduler scheduler;
    private final Messages messages;
    private final Logger logger;

    public TargetReport(
            Server server,
            CombatStatusSource status,
            Scheduler scheduler,
            Messages messages,
            Logger logger) {
        this.server = Objects.requireNonNull(server, "server");
        this.status = Objects.requireNonNull(status, "status");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public void subscribeTo(EventBus eventBus) {
        Objects.requireNonNull(eventBus, "eventBus").subscribe(DamageDealtEvent.class, this::report);
    }

    private void report(DamageDealtEvent event) {
        UUID attackerId = event.attackerId();
        if (attackerId == null || attackerId.equals(event.targetId())) {
            // No attacker to tell - environment damage - or something that hurt itself.
            return;
        }
        // Only reported to a player, and only about someone else. Taking a hit is already answered by
        // the action bar; this line is about the other side.
        scheduler.runSyncOnEntity(
                new EntityRef(attackerId),
                () -> {
                    Player attacker = server.getPlayer(attackerId);
                    if (attacker == null) {
                        return;
                    }
                    try {
                        attacker.sendMessage(line(event));
                    } catch (RuntimeException failure) {
                        logger.warning(
                                "[hud] could not report the target of " + attackerId + ": " + failure);
                    }
                });
    }

    private Component line(DamageDealtEvent event) {
        String name = nameOf(event.targetId());
        String damage = StatusActionBar.whole(event.totalDamage());

        if (event.lethal()) {
            // No health left to report, so the line says what happened instead of showing 0/0.
            return Component.text(
                            messages.get(
                                    CombatMessageKeys.TARGET_SLAIN,
                                    Map.of("target", name, "damage", damage)))
                    .color(NamedTextColor.GRAY);
        }

        java.util.Optional<CombatStatusSource.Status> target = status.statusOf(event.targetId());
        if (target.isEmpty()) {
            // Hit something outside the stat system. Nothing to report about it, and inventing zeroes
            // would be worse than saying nothing.
            return Component.empty();
        }
        CombatStatusSource.Status current = target.get();
        return Component.text(
                        messages.get(
                                CombatMessageKeys.TARGET_REPORT,
                                Map.of(
                                        "target", name,
                                        "health", StatusActionBar.whole(current.health()),
                                        "max", StatusActionBar.whole(current.maxHealth()),
                                        "percent", Integer.toString(current.percent()),
                                        "defense", StatusActionBar.whole(current.defense()),
                                        "damage", damage,
                                        // How many blows the window summed up - available for a
                                        // server that wants to word the line differently.
                                        "hits", Integer.toString(event.hitCount()))))
                .color(NamedTextColor.GRAY);
    }

    /**
     * A name for the thing that was hit.
     *
     * <p>Bukkit's own, so a named mob shows its name and an unnamed one its type. Falls back to the id
     * only if the entity is already gone, which is the normal case for the killing blow.
     */
    private String nameOf(UUID targetId) {
        Entity entity = server.getEntity(targetId);
        return entity == null ? "?" : entity.getName();
    }
}
