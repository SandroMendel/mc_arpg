package rpg.core.session;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Drives sessions from load to removal.
 *
 * <p>Everything about <em>when</em> a session exists lives here; nothing about <em>how</em> data is
 * written. Persisting is B02's, and this class only triggers it. That separation is the single most
 * important property of this block: a second write path here could break B02's guarantee that at
 * most one autosave interval is ever lost, and no B03 test would notice.
 *
 * <p>Two paths deliberately write nothing at all:
 *
 * <ul>
 *   <li>a load that failed ({@link SessionState#FAILED}) - the player never got a state, so writing
 *       would replace their real record with nothing (FR-012)
 *   <li>a load abandoned because the player disconnected first (FR-015) - same reason
 * </ul>
 *
 * <p>Bukkit-free and testable without a server: the load is supplied as a function, the writing as
 * a callback, the clock as a parameter.
 */
public final class DefaultSessionLifecycle implements SessionLifecycle {

    private final DefaultSessionRegistry registry;
    private final Function<UUID, SessionBundle> loader;
    private final SessionWriter writer;
    private final StateVersionMigrator migrator;
    private final Executor asyncExecutor;
    private final Clock clock;
    private final Logger logger;

    /** Loads currently in flight, so a disconnect mid-load can be noticed. */
    private final ConcurrentHashMap<UUID, CompletableFuture<PlayerSession>> inFlight =
            new ConcurrentHashMap<>();

    /** Players who disconnected while their load was running; their result is discarded. */
    private final ConcurrentHashMap<UUID, Boolean> abandoned = new ConcurrentHashMap<>();

    public DefaultSessionLifecycle(
            DefaultSessionRegistry registry,
            Function<UUID, SessionBundle> loader,
            SessionWriter writer,
            StateVersionMigrator migrator,
            Executor asyncExecutor,
            Clock clock,
            Logger logger) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.loader = Objects.requireNonNull(loader, "loader");
        this.writer = Objects.requireNonNull(writer, "writer");
        this.migrator = Objects.requireNonNull(migrator, "migrator");
        this.asyncExecutor = Objects.requireNonNull(asyncExecutor, "asyncExecutor");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public CompletableFuture<PlayerSession> beginLoad(UUID playerId, Duration timeout) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(timeout, "timeout");

        if (registry.peek(playerId).isPresent()) {
            return CompletableFuture.failedFuture(new DuplicateSessionException(playerId));
        }

        abandoned.remove(playerId);
        CompletableFuture<PlayerSession> future =
                CompletableFuture.supplyAsync(() -> loadAndOpen(playerId), asyncExecutor)
                        .orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
                        .whenComplete(
                                (session, failure) -> {
                                    inFlight.remove(playerId);
                                    if (failure != null) {
                                        // Any failure - including the timeout - ends in FAILED,
                                        // which is the state that writes nothing.
                                        failLoad(playerId, failure);
                                    }
                                });
        inFlight.put(playerId, future);
        return future;
    }

    @Override
    public void markReady(UUID playerId) {
        registry.peek(playerId)
                .ifPresent(
                        session -> {
                            session.transitionTo(SessionState.READY, clock.instant());
                            logger.fine("[session] " + playerId + " ready");
                        });
    }

    @Override
    public CompletableFuture<Void> endSession(UUID playerId, SessionEndReason reason) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(reason, "reason");

        Optional<PlayerSession> held = registry.peek(playerId);
        if (held.isEmpty()) {
            // Nothing to end. A load may still be running - discard it rather than let it open a
            // session for a player who is already gone.
            abandonLoad(playerId);
            return CompletableFuture.completedFuture(null);
        }

        PlayerSession session = held.get();
        if (session.state().isTerminal()) {
            // UNLOADING: already ending. Firing a second unload - as a separate kick listener would
            // - is what produces the duplicate write FR-014 rules out.
            // FAILED: the load never produced a usable state, so there is nothing to write and no
            // transition left to make (FR-012). Normally such a session is already gone; this is
            // the guard for the case where it is not.
            registry.remove(playerId);
            return CompletableFuture.completedFuture(null);
        }

        session.transitionTo(SessionState.UNLOADING, clock.instant());

        if (!session.mayBeWritten()) {
            registry.remove(playerId);
            return CompletableFuture.completedFuture(null);
        }

        // B02 does the writing. The session is removed only once that write finished (FR-008) -
        // removing first would drop the last changes on the floor.
        return writer.writeAndAwait(playerId)
                .whenComplete(
                        (ignored, failure) -> {
                            if (failure != null) {
                                logger.log(
                                        Level.SEVERE,
                                        "[session] final write for " + playerId + " failed ("
                                                + reason + ") - keeping the session so a later"
                                                + " flush can still save it",
                                        failure);
                                return;
                            }
                            registry.remove(playerId);
                            logger.fine("[session] " + playerId + " unloaded (" + reason + ")");
                        });
    }

    @Override
    public void abandonLoad(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        abandoned.put(playerId, Boolean.TRUE);
        CompletableFuture<PlayerSession> running = inFlight.get(playerId);
        if (running != null && !running.isDone()) {
            logger.fine("[session] abandoning in-flight load for " + playerId);
        }
        // Nothing is written: the player never received a state, so there is none to save.
        registry.peek(playerId)
                .filter(session -> session.state() == SessionState.LOADING)
                .ifPresent(
                        session -> {
                            session.transitionTo(SessionState.UNLOADING, clock.instant());
                            registry.remove(playerId);
                        });
    }

    /** Whether a load for this player was abandoned; the loader checks before publishing. */
    public boolean wasAbandoned(UUID playerId) {
        return abandoned.containsKey(playerId);
    }

    // --- internals ---

    private PlayerSession loadAndOpen(UUID playerId) {
        SessionBundle bundle = loader.apply(playerId);

        // A record from a newer build cannot be interpreted; refusing beats corrupting (FR-027).
        Optional<PlayerCharacter> fromFuture = bundle.anyFromFutureVersion();
        if (fromFuture.isPresent()) {
            throw new UnknownDataVersionException(
                    fromFuture.get().dataVersion(), PlayerCharacter.CURRENT_DATA_VERSION);
        }

        SessionBundle migrated = migrator.migrate(bundle);
        PlayerCharacter active = migrated.preferredCharacter().orElse(null);
        PlayerSession session = new PlayerSession(playerId, active, migrated.characters());

        if (abandoned.remove(playerId) != null) {
            // The player disconnected while this load ran. Do not publish it and do not write it.
            throw new SessionLoadException(
                    "load for " + playerId + " was abandoned - the player disconnected first");
        }

        registry.open(session);

        // A record that had to be migrated is marked so the new format is written back (FR-026).
        if (migrator.migratedAnything(bundle, migrated)) {
            writer.markCharactersDirty(migrated.characters());
        }
        return session;
    }

    private void failLoad(UUID playerId, Throwable failure) {
        registry.peek(playerId)
                .ifPresent(
                        session -> {
                            if (session.state() == SessionState.LOADING) {
                                session.transitionTo(SessionState.FAILED, clock.instant());
                            }
                            // Removed without ever being written - the guarantee of FR-012.
                            registry.remove(playerId);
                        });
        logger.log(Level.WARNING, "[session] load for " + playerId + " failed", failure);
    }

    /**
     * What this lifecycle needs from the persistence side.
     *
     * <p>An interface rather than a direct dependency, so the rules above stay testable in
     * {@code rpg-core} without a database - and so it stays visible that B03 only ever triggers
     * B02's writing.
     */
    public interface SessionWriter {

        /** Triggers B02's immediate write for this player and completes when it finished. */
        CompletableFuture<Void> writeAndAwait(UUID playerId);

        /** Marks migrated characters so the current format is persisted (FR-026). */
        void markCharactersDirty(java.util.List<PlayerCharacter> characters);
    }
}
