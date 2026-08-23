package rpg.platform.hud;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Logger;

import org.bukkit.Server;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import rpg.core.combat.CombatMessageKeys;
import rpg.core.event.EventBus;
import rpg.core.message.MessageKey;
import rpg.core.message.Messages;
import rpg.core.scheduler.EntityRef;
import rpg.core.scheduler.Scheduler;
import rpg.core.stats.ResourceChangedEvent;
import rpg.core.stats.ResourceKind;
import rpg.core.stats.StatsRecalculatedEvent;

/**
 * The player's own health, mana and defence, on the action bar.
 *
 * <p>Named for what it is rather than {@code HudRenderer}: Constitution III reserves that name for
 * B13, which will own bossbars, scoreboards and the layout of all of it. This is one line, and taking
 * the bigger name would force B13 to reconcile two abstractions instead of widening one.
 *
 * <p><b>Two triggers.</b> The line is redrawn whenever a resource changes or the stats are
 * recalculated - the moments the numbers actually differ - and on a steady refresh besides.
 *
 * <p>The refresh is not optional and cannot be avoided: Minecraft fades an action bar after about two
 * seconds, so a permanent readout means resending it. That is scheduled work while the server is
 * otherwise idle, which Constitution II discourages - and it is the price of the feature, named here
 * rather than hidden. One packet per player per second; the pass itself is a map read.
 */
public final class StatusActionBar {

    /**
     * How often the line is resent.
     *
     * <p>A second is under the roughly two seconds Minecraft takes to fade the bar, so it never
     * visibly blinks, and it is the longest interval for which that is true - anything shorter is extra
     * packets for no gain.
     */
    static final Duration REFRESH = Duration.ofSeconds(1);

    private final Server server;
    private final CombatStatusSource status;
    private final Scheduler scheduler;
    private final Messages messages;
    private final Logger logger;

    public StatusActionBar(
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

    /** Redraws on every health change and every recalculation. */
    public void subscribeTo(EventBus eventBus) {
        Objects.requireNonNull(eventBus, "eventBus");
        // Both resources, not just health: mana is on the line now, and filtering it out would let a
        // spell cost show up to a second late - long enough to look like the cast did not register.
        //
        // This does not make the bar chatty. A change event is only published when the value actually
        // moved (DefaultStatEngine returns early otherwise), so a player at full health and mana
        // produces none at all - and one who is regenerating is exactly the player whose bar should
        // be moving.
        eventBus.subscribe(ResourceChangedEvent.class, event -> show(event.holderId()));
        // Defence and maximum health only move on a recalculation - a tier advance, a level, a buff.
        eventBus.subscribe(StatsRecalculatedEvent.class, event -> show(event.holderId()));
    }

    /**
     * Starts the refresh that keeps the line on screen.
     *
     * <p>Re-schedules itself rather than using a repeating task, because the scheduler has none by
     * design (ADR-007). It stops on its own when the plugin is disabled: the scheduler then returns a
     * cancelled handle and never runs the body that would schedule the next pass.
     */
    public void startRefresh(Supplier<List<UUID>> players) {
        Objects.requireNonNull(players, "players");
        scheduler.runAsyncDelayed(
                REFRESH,
                () -> {
                    players.get().forEach(this::show);
                    startRefresh(players);
                });
    }

    /**
     * Draws the line for one holder, if it is a player with values.
     *
     * <p>Does nothing for a mob: they go through the same engine and raise the same events, and a
     * zombie has no action bar.
     */
    public void show(UUID holderId) {
        Objects.requireNonNull(holderId, "holderId");
        Optional<CombatStatusSource.Status> current = status.statusOf(holderId);
        if (current.isEmpty()) {
            return;
        }
        scheduler.runSyncOnEntity(
                new EntityRef(holderId),
                () -> {
                    Player player = server.getPlayer(holderId);
                    if (player == null) {
                        return;
                    }
                    try {
                        player.sendActionBar(line(current.get()));
                    } catch (RuntimeException failure) {
                        // A readout must never cost a tick (Constitution VI).
                        logger.warning(
                                "[hud] could not draw the action bar of " + holderId + ": " + failure);
                    }
                });
    }

    /**
     * The line a player reads.
     *
     * <p><b>Two texts, not one with an empty gap.</b> A mob has no mana, and printing {@code 0/0} for
     * it would take space from three numbers that mean something. Which of the two is used follows
     * from the holder, not from a flag somebody has to remember to pass.
     *
     * <p>The colour still comes from <em>health</em> alone. It is the number that decides whether to
     * run, and a second colour source would make the line say two things at once.
     */
    private Component line(CombatStatusSource.Status current) {
        int percent = current.percent();
        Map<String, String> values = new java.util.HashMap<>();
        values.put("health", whole(current.health()));
        values.put("max", whole(current.maxHealth()));
        values.put("percent", Integer.toString(percent));
        values.put("defense", whole(current.defense()));
        if (current.hasMana()) {
            values.put("mana", whole(current.mana()));
            values.put("maxMana", whole(current.maxMana()));
        }
        if (current.hasMeter()) {
            values.put("meter", whole(current.meter()));
        }
        // Drei Zeilen, nicht eine mit Luecken. Welche gilt, folgt aus dem Traeger und nicht aus einem
        // Schalter, den jemand zu setzen vergessen kann.
        MessageKey key;
        if (!current.hasMana()) {
            key = CombatMessageKeys.STATUS_ACTION_BAR_NO_MANA;
        } else if (current.hasMeter()) {
            key = CombatMessageKeys.STATUS_ACTION_BAR_WITH_METER;
        } else {
            key = CombatMessageKeys.STATUS_ACTION_BAR;
        }
        return Component.text(messages.get(key, values)).color(colourFor(percent));
    }

    /**
     * Colour by how much is left - the part a player reads before the numbers.
     *
     * <p>Three thresholds rather than a gradient: three states are distinguishable in the corner of the
     * eye, a smooth ramp is not.
     */
    private static NamedTextColor colourFor(int percent) {
        if (percent <= 25) {
            return NamedTextColor.RED;
        }
        return percent <= 60 ? NamedTextColor.YELLOW : NamedTextColor.GREEN;
    }

    /** No decimals: health of 1234.7 reads as 1235, and the fraction is noise at this scale. */
    static String whole(double value) {
        return Long.toString(Math.round(value));
    }
}
