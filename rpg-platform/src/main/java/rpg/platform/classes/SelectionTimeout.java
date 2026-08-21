package rpg.platform.classes;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Server;
import org.bukkit.entity.Player;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import rpg.core.classes.ClassMessageKeys;
import rpg.core.message.Messages;
import rpg.core.scheduler.EntityRef;
import rpg.core.scheduler.Scheduler;
import rpg.core.scheduler.TaskHandle;

/**
 * The selection does not wait forever: a warning, then a disconnect.
 *
 * <p>Without this a player can park in the menu indefinitely. That is not merely untidy - the menu is
 * shown on every join now, and a player in it holds a session, a database connection's worth of loaded
 * state and a slot on the server while being unable to be interacted with, damaged or moved. An idle
 * client left overnight would hold all of it.
 *
 * <p>The disconnect is the mildest thing that actually resolves it. Picking a character for them would
 * put someone into the world who is not at their keyboard, wearing equipment and holding a session that
 * then has to be saved.
 */
public final class SelectionTimeout {

    /**
     * How long a player may think about it, and when they are told.
     *
     * <p>Constants rather than configuration, like the notice cooldown in
     * {@code InventoryFullNoticeListener}: these are the timings of one interaction, not a knob an
     * operator tunes per server. If that turns out to be wrong they move to {@code classes.yml}, which
     * is a schema change and not worth doing on speculation.
     */
    static final Duration LIMIT = Duration.ofMinutes(2);

    static final Duration WARN_AFTER = Duration.ofMinutes(1);

    private static final Key WARNING_SOUND = Key.key("minecraft", "block.note_block.pling");

    private final Server server;
    private final Scheduler scheduler;
    private final Messages messages;

    /** The two pending tasks per player, so a choice can call them off. */
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    public SelectionTimeout(Server server, Scheduler scheduler, Messages messages) {
        this.server = Objects.requireNonNull(server, "server");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    /**
     * Starts the clock for a player who has just been shown the selection.
     *
     * <p>Idempotent: the menu reopens on every attempt to close it (FR-033), and restarting the clock
     * there would make the limit unreachable by pressing escape.
     */
    public void start(Player player) {
        Objects.requireNonNull(player, "player");
        UUID playerId = player.getUniqueId();
        pending.computeIfAbsent(
                playerId,
                id ->
                        new Pending(
                                delayed(WARN_AFTER, id, this::warn),
                                delayed(LIMIT, id, this::disconnect)));
    }

    /** Calls the clock off - a character was chosen, or the player is gone. */
    public void cancel(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        Pending stopped = pending.remove(playerId);
        if (stopped != null) {
            stopped.cancel();
        }
    }

    /** Whether this player is currently on the clock. For tests and diagnostics. */
    public boolean isRunning(UUID playerId) {
        return pending.containsKey(playerId);
    }

    /**
     * Waits off the tick, then acts on the player's own.
     *
     * <p>There is no delayed entity-bound scheduling in the abstraction, and adding one for this would
     * be a wider change than the case needs. The async delay touches no Bukkit API - it only hops back
     * (Constitution I.1).
     */
    private TaskHandle delayed(Duration delay, UUID playerId, java.util.function.Consumer<UUID> action) {
        return scheduler.runAsyncDelayed(
                delay,
                () ->
                        scheduler.runSyncOnEntity(
                                new EntityRef(playerId),
                                () -> {
                                    // Still waiting? A choice between the delay and this task cancels
                                    // the handle, but a task already handed to the tick still runs.
                                    if (pending.containsKey(playerId)) {
                                        action.accept(playerId);
                                    }
                                }));
    }

    private void warn(UUID playerId) {
        Player player = server.getPlayer(playerId);
        if (player == null) {
            return;
        }
        long secondsLeft = LIMIT.minus(WARN_AFTER).toSeconds();
        player.sendMessage(
                Component.text(
                                messages.get(
                                        ClassMessageKeys.SELECTION_TIMEOUT_WARNING,
                                        Map.of("seconds", Long.toString(secondsLeft))))
                        .color(NamedTextColor.RED));
        player.playSound(Sound.sound(WARNING_SOUND, Sound.Source.MASTER, 1.0f, 1.0f));
    }

    private void disconnect(UUID playerId) {
        Player player = server.getPlayer(playerId);
        pending.remove(playerId);
        if (player == null) {
            return;
        }
        // With a reason: a disconnect without one reads as a crash, and the player would try again and
        // hit the same wall without knowing why.
        player.kick(
                Component.text(messages.get(ClassMessageKeys.SELECTION_TIMEOUT_KICK))
                        .color(NamedTextColor.RED));
    }

    private record Pending(TaskHandle warning, TaskHandle kick) {

        void cancel() {
            warning.cancel();
            kick.cancel();
        }
    }
}
