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

    /**
     * Blocks that hang their own state off a session (B04 onwards).
     *
     * <p>Copy-on-write because it is written once at startup and read on every login.
     */
    private final java.util.List<SessionAttachment> attachments =
            new java.util.concurrent.CopyOnWriteArrayList<>();

    /**
     * What the login read, kept until the session ends.
     *
     * <p>The character is chosen after the session is ready, and the blocks that then build its state
     * need the rows belonging to it. Holding one bundle per online player is a few hundred bytes and
     * saves a query on the tick at the moment a player enters the world.
     */
    private final ConcurrentHashMap<UUID, SessionBundle> loaded = new ConcurrentHashMap<>();

    /**
     * The ids of everything hooked into the lifecycle.
     *
     * <p>Exists so a bootstrap test can assert that a block actually registered. ADR-012 was written
     * because B02 and B03 were once fully implemented, fully tested, and not wired - an attachment
     * that nobody added is the same failure one layer down: {@code load} and {@code release} would
     * simply never be called.
     */
    public java.util.List<String> attachmentIds() {
        return attachments.stream().map(SessionAttachment::id).toList();
    }

    /**
     * Registers an attachment. Called during module start, before any player can connect.
     *
     * <p>Inserted according to {@link SessionAttachment#order()} rather than appended, so the order is
     * settled once here instead of being re-derived on every login. Equal orders keep their
     * registration sequence, which is the module start order.
     */
    public void addAttachment(SessionAttachment attachment) {
        Objects.requireNonNull(attachment, "attachment");
        int at = attachments.size();
        while (at > 0 && attachments.get(at - 1).order() > attachment.order()) {
            at--;
        }
        attachments.add(at, attachment);
    }

    /**
     * What the login read for this player, while the session lasts.
     *
     * <p>For the selection: it shows every character of the account with what each has reached, and
     * those rows are in here. Reading them from the database instead would be a query per menu build,
     * and the menu is rebuilt every time a player tries to close it.
     */
    public Optional<SessionBundle> loadedBundle(UUID playerId) {
        return Optional.ofNullable(loaded.get(playerId));
    }

    /**
     * Drops the session and everything held alongside it.
     *
     * <p>One method rather than two calls at five sites: the bundle outlives the load on purpose, so
     * the one place it must not outlive is the session, and a site that forgot it would leak a bundle
     * per login without anything failing.
     */
    private void forget(UUID playerId) {
        registry.remove(playerId);
        loaded.remove(playerId);
    }

    /**
     * The attachments in teardown order - the reverse of build-up.
     *
     * <p>So nothing is calculated from state that was already released: B04 hands over its resources
     * while the level and the class it computed them from are still there.
     */
    private java.util.List<SessionAttachment> teardownOrder() {
        java.util.List<SessionAttachment> reversed = new java.util.ArrayList<>(attachments);
        java.util.Collections.reverse(reversed);
        return reversed;
    }

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
    public boolean activateCharacter(UUID playerId, PlayerCharacter character) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(character, "character");

        Optional<PlayerSession> held = registry.peek(playerId);
        if (held.isEmpty()) {
            // The player left between creating the character and this call. The character is stored and
            // will be picked up by the next login; there is nothing here to attach it to.
            logger.fine(
                    () -> "[session] no session left to activate " + character.characterId() + " in");
            return false;
        }
        PlayerSession session = held.get();
        if (!session.activate(character)) {
            // Already playing someone. Not this block's business to decide what went wrong - the
            // caller is told it lost, and B07 keeps its menu open.
            logger.warning(
                    "[session] "
                            + playerId
                            + " already has an active character; refused to activate "
                            + character.characterId());
            return false;
        }

        // What the login read. Empty only if the session was opened without going through the load -
        // the attachments then get an empty bundle and start the character from scratch, which is the
        // right answer for a character that was created a moment ago anyway.
        SessionBundle bundle =
                loaded.getOrDefault(playerId, SessionBundle.empty(playerId));

        // Same confinement as the open path: a broken attachment must not cost the player the
        // character they just chose.
        for (SessionAttachment attachment : attachments) {
            try {
                attachment.onCharacterActivated(session, character, bundle);
            } catch (RuntimeException failure) {
                logger.log(
                        Level.WARNING,
                        "[session] attachment '"
                                + attachment.id()
                                + "' failed while activating the character of "
                                + playerId
                                + "; continuing without it",
                        failure);
            }
        }
        logger.fine(
                () -> "[session] " + playerId + " entered play as " + character.characterId());
        return true;
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
            forget(playerId);
            return CompletableFuture.completedFuture(null);
        }

        session.transitionTo(SessionState.UNLOADING, clock.instant());

        // Before the final write, so an attachment can hand over anything still to be persisted, and in
        // teardown order, so nobody hands over a value computed from state that is already gone.
        for (SessionAttachment attachment : teardownOrder()) {
            try {
                attachment.onSessionClosing(playerId);
            } catch (RuntimeException failure) {
                logger.log(
                        Level.WARNING,
                        "[session] attachment '"
                                + attachment.id()
                                + "' failed while closing the session of "
                                + playerId
                                + "; continuing with the write",
                        failure);
            }
        }

        if (!session.mayBeWritten()) {
            forget(playerId);
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
                            forget(playerId);
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
                            forget(playerId);
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
        // No character is picked here, not even when the account has exactly one. Which character is
        // played is the selection's decision on every join (ADR-020), and choosing one in advance would
        // mean the menu had to undo a character that four blocks had already built state for.
        PlayerSession session = new PlayerSession(playerId, null, migrated.characters());

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

        // Kept until a character is chosen. Since the session no longer picks one, everything a block
        // needs to build that character's state - resources, level, tiers - is read at selection time,
        // and it was all in this one read (FR-005). Fetching it again then would be a second query on
        // the player's tick for rows that are already in hand.
        loaded.put(playerId, migrated);

        // Later blocks build their per-session state here, while the player is still held. B04
        // needs this: a stat holder has to be calculated before release (FR-019b).
        for (SessionAttachment attachment : attachments) {
            try {
                attachment.onSessionOpened(session, migrated);
            } catch (RuntimeException failure) {
                logger.log(
                        Level.WARNING,
                        "[session] attachment '"
                                + attachment.id()
                                + "' failed while opening the session of "
                                + playerId
                                + "; continuing without it",
                        failure);
            }
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
                            forget(playerId);
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
