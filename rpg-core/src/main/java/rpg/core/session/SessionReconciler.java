package rpg.core.session;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Removes sessions whose player is no longer connected.
 *
 * <p>This is the mechanism behind FR-009 and SC-008, and it exists because care is not a guarantee.
 * A session can be left behind on paths nobody thought about while writing the unload path: a
 * plugin cancelling the quit event, a failure midway through cleanup, a change made two blocks from
 * now. The requirement asks for an assurance, not an intention.
 *
 * <p>So rather than trying to be exhaustive about the ways a session might leak, the sweep compares
 * what is held against who is actually connected and removes the difference - regardless of how it
 * came about. The same pass expires stale entries in the preload stash, which can otherwise linger
 * when a login succeeded but the player never entered the world.
 */
public final class SessionReconciler {

    private final DefaultSessionRegistry registry;
    private final Supplier<Collection<UUID>> connectedPlayers;
    private final SessionLifecycle lifecycle;
    private final Runnable stashSweep;
    private final Logger logger;

    public SessionReconciler(
            DefaultSessionRegistry registry,
            Supplier<Collection<UUID>> connectedPlayers,
            SessionLifecycle lifecycle,
            Runnable stashSweep,
            Logger logger) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.connectedPlayers = Objects.requireNonNull(connectedPlayers, "connectedPlayers");
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        this.stashSweep = Objects.requireNonNull(stashSweep, "stashSweep");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * Runs one sweep.
     *
     * @return how many orphaned sessions were ended
     */
    public int reconcileOnce() {
        stashSweep.run();

        Collection<UUID> connected = connectedPlayers.get();
        List<PlayerSession> orphaned = registry.orphanedAgainst(connected);
        if (orphaned.isEmpty()) {
            return 0;
        }

        for (PlayerSession session : orphaned) {
            // Ended rather than dropped: an orphan may still hold unwritten progress, and ending it
            // routes through the normal write path.
            logger.warning(
                    "[session] reconciling orphaned session for "
                            + session.playerId()
                            + " (state "
                            + session.state()
                            + ") - the player is no longer connected");
            lifecycle.endSession(session.playerId(), SessionEndReason.RECONCILED);
        }
        return orphaned.size();
    }
}
