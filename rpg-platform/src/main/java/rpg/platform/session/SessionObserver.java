package rpg.platform.session;

import java.util.UUID;

import org.bukkit.entity.Player;

/**
 * What other blocks may learn about the session lifecycle without owning a piece of it.
 *
 * <p>This exists because {@code NoCompetingSessionListenersTest} forbids any module outside this
 * package from handling {@code PlayerJoinEvent} or {@code PlayerQuitEvent}: the lifecycle has exactly
 * one entry and one exit (FR-007, FR-014), because a second unload path was a real bug once. That test
 * points at "B01's event bus, which fires once the session is ready" - and no such event existed. B07
 * was the first block that needed it.
 *
 * <p>Deliberately a Bukkit-facing interface in <b>this</b> package rather than an event on the core
 * bus. The listeners here already have the {@link Player}, which is what a block needs to open a menu
 * or apply equipment; routing that through a bukkit-free event would mean looking the player up again
 * on the other side. A core-level event can still be added later for blocks that only need the id.
 *
 * <p>Implementations run <b>on the tick</b>, inside the join and quit handlers. Anything slow belongs
 * behind the scheduler, not here.
 */
public interface SessionObserver {

    /** Does nothing - for a server assembled without any observer, and for tests. */
    SessionObserver NONE =
            new SessionObserver() {
                @Override
                public void onSessionReady(Player player) {
                    // no-op
                }

                @Override
                public void onSessionEnded(UUID playerId) {
                    // no-op
                }
            };

    /**
     * The session is loaded and the player may act.
     *
     * <p>Called after the lifecycle was marked ready and after B03 released its own hold, so an
     * observer that wants to hold the player again - B07 does, until a class is chosen - is not fighting
     * a release that comes afterwards.
     */
    void onSessionReady(Player player);

    /** The player is gone. Called before the unload is started, so observers can still read state. */
    void onSessionEnded(UUID playerId);
}
