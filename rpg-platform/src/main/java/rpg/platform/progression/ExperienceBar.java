package rpg.platform.progression;

import java.util.Objects;
import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.Server;
import org.bukkit.entity.Player;

import rpg.core.event.EventBus;
import rpg.core.progression.ProgressChangedEvent;
import rpg.core.scheduler.EntityRef;
import rpg.core.scheduler.Scheduler;

/**
 * Puts B06's level and experience onto the vanilla experience bar.
 *
 * <p>The bar belongs to the <em>player</em> in Minecraft, and one player has up to three characters -
 * the same trap as the inventory and the ender chest. The answer here is different, though, and
 * simpler: the value is not stored a second time. B06 already keeps level and experience per character;
 * the bar only shows them, exactly as the health bar shows what B04 keeps (ADR-003).
 *
 * <p>Vanilla experience is therefore <b>not</b> a resource of its own. Nothing in the game grants it -
 * B05 suppresses the vanilla drop - and treating the bar as storage would create a second progression
 * beside the one that is designed, balanced and persisted.
 *
 * <p>Every call touches Bukkit and hops to the owning player's tick first.
 */
public final class ExperienceBar {

    private final Server server;
    private final Scheduler scheduler;
    private final Logger logger;

    public ExperienceBar(Server server, Scheduler scheduler, Logger logger) {
        this.server = Objects.requireNonNull(server, "server");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /** Follows every gain and every level-up from B06. */
    public void subscribeTo(EventBus eventBus) {
        Objects.requireNonNull(eventBus, "eventBus")
                .subscribe(
                        ProgressChangedEvent.class,
                        event ->
                                show(
                                        event.playerId(),
                                        event.level(),
                                        fractionOf(event.xpInLevel(), event.xpForNextLevel())));
    }

    /** Shows a level and how far into it the character is, as a fraction from 0 to 1. */
    public void show(UUID playerId, int level, double fraction) {
        Objects.requireNonNull(playerId, "playerId");
        onTick(
                playerId,
                player -> {
                    player.setLevel(Math.max(0, level));
                    player.setExp(clampToBar(fraction));
                });
    }

    /**
     * Empties the bar.
     *
     * <p>For the moment a session becomes ready: what the client shows then was saved for the player
     * and belongs to whichever character was last played. Leaving it there would mean choosing a
     * character while looking at another one's level.
     */
    public void reset(Player player) {
        Objects.requireNonNull(player, "player");
        player.setLevel(0);
        player.setExp(0.0f);
        // Both of the above are derived from the total on some paths, so it is zeroed as well -
        // otherwise the bar reappears the next time anything nudges it.
        player.setTotalExperience(0);
    }

    private void onTick(UUID playerId, java.util.function.Consumer<Player> action) {
        scheduler.runSyncOnEntity(
                new EntityRef(playerId),
                () -> {
                    Player player = server.getPlayer(playerId);
                    if (player == null) {
                        return;
                    }
                    try {
                        action.accept(player);
                    } catch (RuntimeException failure) {
                        // A bar that fails to draw must not take the tick with it (Constitution VI).
                        logger.warning(
                                "[progression] could not update the experience bar of "
                                        + playerId
                                        + ": "
                                        + failure);
                    }
                });
    }

    /** At the maximum level {@code xpForNextLevel} is 0, and a full bar is the honest picture. */
    private static double fractionOf(long xpInLevel, long xpForNextLevel) {
        if (xpForNextLevel <= 0L) {
            return 1.0;
        }
        return (double) xpInLevel / (double) xpForNextLevel;
    }

    /** Bukkit throws for anything outside 0..1, and a rounding error must not cost a player anything. */
    private static float clampToBar(double fraction) {
        if (Double.isNaN(fraction) || fraction < 0.0) {
            return 0.0f;
        }
        return fraction > 1.0 ? 1.0f : (float) fraction;
    }
}
