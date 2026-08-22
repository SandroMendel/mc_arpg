package rpg.platform.ability;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import net.kyori.adventure.text.Component;
import rpg.core.ability.AbilityMessageKeys;
import rpg.core.ability.BehindTargetCheck;
import rpg.core.ability.PassiveInterceptors;
import rpg.core.ability.effect.StatusEffectEffect;
import rpg.core.message.Messages;

/**
 * The Paper side of three things the passive rules describe but cannot do themselves: reading where
 * two entities are facing, showing a rescue, and applying a vanilla status effect.
 *
 * <p>Each is a small hook the core declares and this installs - the same split as
 * {@code TargetResolver}. Every one of them degrades to doing nothing rather than throwing: an
 * ability that looks like nothing is a smaller problem than one that takes a damage event down.
 */
public final class PaperPassiveHooks {

    private final Server server;
    private final Messages messages;
    private final Logger logger;

    /** Where each character last stood alive, for Second Life to put them back (FR-052c). */
    private final java.util.Map<UUID, Location> lastPosition = new java.util.concurrent.ConcurrentHashMap<>();

    public PaperPassiveHooks(Server server, Messages messages, Logger logger) {
        this.server = Objects.requireNonNull(server, "server");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * Whether the attacker stood behind the target (FR-052a).
     *
     * <p>Only the horizontal direction counts. Standing above or below somebody is not standing behind
     * them, and including the vertical would make a backstab land from a rooftop.
     */
    public BehindTargetCheck behindTarget() {
        return (attackerId, targetId) -> {
            Entity attacker = server.getEntity(attackerId);
            Entity target = server.getEntity(targetId);
            if (attacker == null || target == null) {
                return false;
            }
            Location targetAt = target.getLocation();
            Vector facing = targetAt.getDirection();
            Vector towards = attacker.getLocation().toVector().subtract(targetAt.toVector());
            return BehindTargetCheck.isBehind(
                    facing.getX(),
                    facing.getZ(),
                    towards.getX(),
                    towards.getZ(),
                    BehindTargetCheck.DEFAULT_ANGLE);
        };
    }

    /**
     * What a saved character sees: a title, a sound, and the ground they were standing on.
     *
     * <p>The teleport is the part that needs the remembered position - by the time the blow lands they
     * may be mid-fall off the cliff that would have killed them, and standing them back up there would
     * only postpone it by a second.
     */
    public PassiveInterceptors.SecondLifeHandler secondLife() {
        return characterId -> {
            Player player = server.getPlayer(characterId);
            if (player == null) {
                // A creature with the ability, or a player already gone. The save itself has happened
                // either way - this is only the presentation.
                return;
            }
            Location back = lastPosition.get(characterId);
            if (back != null) {
                player.teleport(back);
            }
            player.showTitle(
                    net.kyori.adventure.title.Title.title(
                            Component.text(messages.get(AbilityMessageKeys.SECOND_LIFE_TITLE)),
                            Component.text(messages.get(AbilityMessageKeys.SECOND_LIFE_SUBTITLE))));
            player.playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 1.0f, 1.0f);
        };
    }

    /** Remembers where a character stood. Called from the move listener, cheaply. */
    public void remember(UUID characterId, Location location) {
        lastPosition.put(characterId, location);
    }

    /** Drops a character's remembered position - on logout and on a character switch. */
    public void forget(UUID characterId) {
        lastPosition.remove(characterId);
    }

    /** Applies a vanilla status effect by name. */
    public StatusEffectEffect.Applier statusEffects() {
        return (holderId, effectName, duration, amplifier) -> {
            Entity entity = server.getEntity(holderId);
            if (!(entity instanceof org.bukkit.entity.LivingEntity living)) {
                return;
            }
            PotionEffectType type = resolve(effectName);
            if (type == null) {
                logger.warning(() -> "[abilities] unknown status effect '" + effectName + "'");
                return;
            }
            living.addPotionEffect(
                    new PotionEffect(type, ticks(duration), amplifier, true, false));
        };
    }

    private static PotionEffectType resolve(String name) {
        org.bukkit.NamespacedKey key =
                org.bukkit.NamespacedKey.fromString(name.toLowerCase(java.util.Locale.ROOT));
        return key == null ? null : org.bukkit.Registry.EFFECT.get(key);
    }

    private static int ticks(Duration duration) {
        return (int) Math.max(1L, duration.toMillis() / 50L);
    }
}
