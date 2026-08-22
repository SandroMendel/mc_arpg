package rpg.platform.ability;

import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * The mage's Rise &amp; Fall: a double jump without a keybind (FR-052d, research.md R7).
 *
 * <p><b>{@link PlayerToggleFlightEvent} is the only way</b> to see a second jump on a vanilla client
 * (ADR-005). Given {@code allowFlight} without actually being allowed to fly, the client sends a
 * flight toggle when the jump key is pressed in mid-air - and that is the signal. The event is
 * cancelled so the player does not start flying, and an upward impulse is applied instead.
 *
 * <p><b>Only a double jump brings slow fall.</b> A normal jump and a step off a cliff stay exactly as
 * they are for every other class, which is what the design asks for: falling off something must not
 * be safer for the mage than for the warrior.
 *
 * <p>The state hangs on the ground, not on a timer: {@code allowFlight} is given back on landing and
 * taken on use, so "no third jump before touching down" is a property rather than a checked rule.
 */
public final class DoubleJumpListener implements Listener {

    /** Whether this player has Rise &amp; Fall unlocked and switched on. */
    private final Predicate<Player> enabled;

    /** Whether slow fall is part of it - the middle toggle setting turns it off. */
    private final Predicate<Player> withSlowFall;

    /** The upward push, from the ability's configuration. */
    private final Supplier<Double> strength;

    /** How long the slow fall lasts, in ticks. */
    private final Supplier<Integer> slowFallTicks;

    public DoubleJumpListener(
            Predicate<Player> enabled,
            Predicate<Player> withSlowFall,
            Supplier<Double> strength,
            Supplier<Integer> slowFallTicks) {
        this.enabled = Objects.requireNonNull(enabled, "enabled");
        this.withSlowFall = Objects.requireNonNull(withSlowFall, "withSlowFall");
        this.strength = Objects.requireNonNull(strength, "strength");
        this.slowFallTicks = Objects.requireNonNull(slowFallTicks, "slowFallTicks");
    }

    /**
     * Hands the ability back on landing.
     *
     * <p>On {@code PlayerMoveEvent}, which is one of the busiest events the server has - so the very
     * first thing is a check that returns for everybody who is not a mage with this unlocked, and the
     * second is a check that returns for everybody who is already able to jump again. Both are field
     * reads, no allocation.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            // Creative flight is theirs, not ours - taking allowFlight away would ground an admin.
            return;
        }
        if (!enabled.test(player)) {
            if (player.getAllowFlight()) {
                // Lost the ability while airborne - take the permission back rather than leaving a
                // player able to toggle flight forever.
                player.setAllowFlight(false);
            }
            return;
        }
        if (player.isOnGround() && !player.getAllowFlight()) {
            player.setAllowFlight(true);
        }
    }

    /** The double jump itself. */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        if (!event.isFlying() || !enabled.test(player)) {
            return;
        }

        // Cancel first: without this the player actually starts flying, which is the one outcome
        // nobody wants and the reason this event is usable at all.
        event.setCancelled(true);
        player.setAllowFlight(false);
        player.setFlying(false);

        Vector push = player.getVelocity().setY(strength.get());
        player.setVelocity(push);

        if (withSlowFall.test(player)) {
            player.addPotionEffect(
                    new PotionEffect(PotionEffectType.SLOW_FALLING, slowFallTicks.get(), 0, true, false));
        }
    }
}
