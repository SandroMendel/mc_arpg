package rpg.platform.ability;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import rpg.core.ability.Ability;
import rpg.core.stats.StatSnapshot;

/**
 * Waits for a thrown player to touch the ground, and tells whoever asked (FR-045d).
 *
 * <p><b>Why the leap needed this at all.</b> Everything an ability does, it did at the moment of the
 * cast. For a leap that is the wrong moment twice over: the damage hit whatever stood in front of the
 * warrior <em>before</em> he jumped, and the knockback pushed it away from the spot he took off from.
 * What a leap does, it does where it comes down - and where that is, nobody knows until he is there.
 *
 * <p><b>No task and no polling.</b> A countdown would have to guess how long a jump takes, and a jump
 * over a cliff takes as long as the cliff is deep. This rides {@code PlayerMoveEvent}, which fires
 * anyway, and the guard in front is a single {@code int} read - the same bargain
 * {@code DoubleJumpListener} and B09's movement guard make on the same event. With nobody mid-leap,
 * and that is nearly always, the handler is one comparison.
 *
 * <p><b>A pending landing is dropped when the player leaves</b>, not kept in the hope they come back:
 * they would come back standing on the ground, and firing a leap's impact into the middle of a login
 * is not what anybody asked for.
 */
public final class LandingWatcher implements Listener {

    /** Told the moment a watched player is back on the ground. */
    @FunctionalInterface
    public interface OnLanding {
        void landed(Ability ability, UUID holderId, int rank, StatSnapshot snapshot);
    }

    /** What is owed to one player who is currently in the air. */
    private record Pending(Ability ability, int rank, StatSnapshot snapshot) {}

    private final Map<UUID, Pending> airborne = new ConcurrentHashMap<>();

    /**
     * The guard in front of the map lookup.
     *
     * <p>{@code ConcurrentHashMap.isEmpty()} is cheap but not free, and this runs several times a
     * second per player. A counter read is one field.
     */
    private final AtomicInteger waiting = new AtomicInteger();

    private final OnLanding onLanding;

    public LandingWatcher(OnLanding onLanding) {
        this.onLanding = Objects.requireNonNull(onLanding, "onLanding");
    }

    /**
     * Remembers that this player is on their way down and something is owed when they arrive.
     *
     * <p>A second leap before the first landed replaces the first. It cannot happen with the shipped
     * cooldown, and if it ever can, the newer jump is the one whose impact the player is expecting.
     */
    public void expect(UUID holderId, Ability ability, int rank, StatSnapshot snapshot) {
        if (airborne.put(holderId, new Pending(ability, rank, snapshot)) == null) {
            waiting.incrementAndGet();
        }
    }

    /** Forgets a player who left mid-air. Called by the session observer. */
    public void forget(UUID holderId) {
        if (airborne.remove(holderId) != null) {
            waiting.decrementAndGet();
        }
    }

    /** How many are in the air right now. For the bootstrap test. */
    public int waitingCount() {
        return waiting.get();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (waiting.get() == 0) {
            // Nobody is mid-leap. One int read, and this is where almost every call ends.
            return;
        }
        Player player = event.getPlayer();
        if (!player.isOnGround()) {
            return;
        }
        Pending pending = airborne.remove(player.getUniqueId());
        if (pending == null) {
            return;
        }
        waiting.decrementAndGet();
        onLanding.landed(pending.ability(), player.getUniqueId(), pending.rank(), pending.snapshot());
    }
}
