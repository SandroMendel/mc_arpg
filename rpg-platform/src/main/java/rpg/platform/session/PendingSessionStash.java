package rpg.platform.session;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import rpg.core.session.PlayerSession;

/**
 * Carries a session loaded during the pre-login event across to the join.
 *
 * <p>Entries expire. Without that they would accumulate: a login can pass the pre-login stage and
 * still never reach the world - another plugin refuses it, the connection drops, the client gives
 * up - and the loaded session would sit here forever. Expiry writes nothing, because a player who
 * never entered never had a state worth saving.
 */
public final class PendingSessionStash {

    private final ConcurrentHashMap<UUID, Entry> pending = new ConcurrentHashMap<>();
    private final Duration expiry;
    private final Clock clock;
    private final Logger logger;

    public PendingSessionStash(Duration expiry, Clock clock, Logger logger) {
        this.expiry = Objects.requireNonNull(expiry, "expiry");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /** Stores a freshly loaded session for collection at join. */
    public void put(PlayerSession session) {
        Objects.requireNonNull(session, "session");
        pending.put(session.playerId(), new Entry(session, clock.instant()));
    }

    /** Takes the session out; a stash entry is collected exactly once. */
    public Optional<PlayerSession> take(UUID playerId) {
        Entry entry = pending.remove(playerId);
        return entry == null ? Optional.empty() : Optional.of(entry.session());
    }

    /** Drops an entry without collecting it - the connection closed before the join. */
    public void discard(UUID playerId) {
        if (pending.remove(playerId) != null) {
            logger.fine("[session] discarded a preloaded session for " + playerId);
        }
    }

    /**
     * Removes entries that were never collected.
     *
     * <p>Called from the same reconciliation sweep that cleans the registry, so there is one place
     * that guarantees nothing is left behind rather than two that hope so.
     */
    public int expireStale() {
        Instant deadline = clock.instant().minus(expiry);
        int before = pending.size();
        pending.values().removeIf(entry -> entry.storedAt().isBefore(deadline));
        int removed = before - pending.size();
        if (removed > 0) {
            logger.warning(
                    "[session] expired "
                            + removed
                            + " preloaded session(s) that were never collected - the logins did not"
                            + " reach the world");
        }
        return removed;
    }

    /** How many sessions are waiting to be collected. */
    public int size() {
        return pending.size();
    }

    /** The waiting entries, for diagnostics. */
    public Map<UUID, Instant> waiting() {
        return Map.copyOf(
                pending.entrySet().stream()
                        .collect(
                                java.util.stream.Collectors.toMap(
                                        Map.Entry::getKey, e -> e.getValue().storedAt())));
    }

    private record Entry(PlayerSession session, Instant storedAt) {}
}
