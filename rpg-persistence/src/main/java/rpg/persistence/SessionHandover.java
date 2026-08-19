package rpg.persistence;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import rpg.core.persistence.FlushReason;
import rpg.core.persistence.PersistenceException;
import rpg.core.persistence.PlayerState;
import rpg.persistence.jdbc.JdbcPlayerStateRepository;

/**
 * Hands a player over from one session to the next without losing or rolling back progress
 * (FR-019a, FR-019c).
 *
 * <p>The problem this solves is the ghost session: a player drops, their unwritten changes sit in
 * the buffer, and they reconnect immediately. If the new session read storage right away it would
 * get the older state, and the previous session's flush would then land on top of it - progress
 * rolled back, or items duplicated. Both are the failures Minecraft servers are notorious for.
 *
 * <p>So the order is: flush what the previous session owes, <em>then</em> read. Waiting has a
 * bound, and exceeding it refuses the login rather than leaving the player hanging - a refusal is
 * recoverable, stale state is not.
 */
public final class SessionHandover {

    /** How long a login may wait for the previous session's writes (FR-019c). */
    public static final Duration DEFAULT_WAIT = Duration.ofSeconds(5);

    private final JdbcPlayerStateRepository repository;
    private final FlushCycle flushCycle;
    private final Duration maxWait;
    private final Logger logger;

    public SessionHandover(
            JdbcPlayerStateRepository repository,
            FlushCycle flushCycle,
            Duration maxWait,
            Logger logger) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.flushCycle = Objects.requireNonNull(flushCycle, "flushCycle");
        this.maxWait = Objects.requireNonNull(maxWait, "maxWait");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * Loads a player's state, first draining anything the previous session still owes.
     *
     * @return the stored state, or empty for a player connecting for the first time. Fails
     *     exceptionally when the wait times out or storage cannot be read - the caller must then
     *     refuse the login and must never substitute a default state (FR-005a).
     */
    public CompletableFuture<Optional<PlayerState>> loadForNewSession(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");

        return flushCycle
                .flushNow(FlushReason.SESSION_END)
                .thenCompose(
                        result -> {
                            if (!result.complete()) {
                                // Reading now would hand over state the failed writes were about to
                                // change. Refusing is the safe direction.
                                logger.warning(
                                        "[persistence] refusing login for "
                                                + playerId
                                                + ": "
                                                + result.failed()
                                                + " pending write(s) could not be flushed");
                                return CompletableFuture.failedFuture(
                                        new PersistenceException(
                                                "pending writes for a previous session could not be"
                                                        + " flushed; refusing the login rather than"
                                                        + " serving stale state"));
                            }
                            return repository.load(playerId);
                        })
                .orTimeout(maxWait.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    /** Writes a leaving player's state immediately, independent of the interval (FR-004). */
    public CompletableFuture<Void> onSessionEnd(UUID playerId) {
        repository.markDirty(playerId);
        return flushCycle
                .flushNow(FlushReason.SESSION_END)
                .thenAccept(
                        result -> {
                            if (result.complete()) {
                                repository.evict(playerId);
                            } else {
                                // Keep the cache entry: the state is still the only copy of those
                                // changes until a later flush succeeds.
                                logger.warning(
                                        "[persistence] session-end flush for "
                                                + playerId
                                                + " left "
                                                + result.failed()
                                                + " change(s) pending - keeping them in memory");
                            }
                        });
    }
}
