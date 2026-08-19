package rpg.core.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * T047, T048, T049, T034, T035 - the lifecycle rules, without a server and without a database.
 *
 * <p>The first two tests here are the most important in the block. A write from a failed or
 * abandoned load replaces a player's real record with nothing, and the loss surfaces hours later
 * when it can no longer be undone. Everything else in B03 exists to make those two situations
 * survivable; these tests are what prove they are.
 */
class SessionLifecycleTest {

    private static final Instant NOW = Instant.parse("2026-08-19T12:00:00Z");
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final Logger QUIET = Logger.getLogger(SessionLifecycleTest.class.getName());

    /** Runs work on the calling thread, so no test needs to sleep or poll. */
    private static final Executor DIRECT = Runnable::run;

    private DefaultSessionRegistry registry;
    private RecordingWriter writer;

    @BeforeEach
    void setUp() {
        QUIET.setLevel(Level.OFF);
        registry = new DefaultSessionRegistry();
        writer = new RecordingWriter();
    }

    private DefaultSessionLifecycle lifecycleLoading(java.util.function.Function<UUID, SessionBundle> loader) {
        return new DefaultSessionLifecycle(
                registry,
                loader,
                writer,
                new StateVersionMigrator(QUIET),
                DIRECT,
                Clock.fixed(NOW, ZoneOffset.UTC),
                QUIET);
    }

    private static SessionBundle bundleWithOneCharacter(UUID playerId) {
        PlayerCharacter character = PlayerCharacter.create(playerId, CharacterClass.WARRIOR, NOW);
        return new SessionBundle(
                playerId,
                Optional.of(rpg.core.persistence.PlayerState.initial(playerId, NOW)),
                List.of(character),
                List.of());
    }

    // === the two guarantees ===

    @Test
    void aFailedLoadWritesNothing() {
        // FR-012. If this ever regresses, a login failure destroys the player's saved progress.
        DefaultSessionLifecycle lifecycle =
                lifecycleLoading(
                        playerId -> {
                            throw new SessionLoadException("storage unreachable");
                        });
        UUID playerId = UUID.randomUUID();

        assertThatThrownBy(() -> lifecycle.beginLoad(playerId, TIMEOUT).join()).isNotNull();

        assertThat(writer.writes()).isZero();
        assertThat(registry.peek(playerId)).isEmpty();
        assertThat(registry.activeSessionCount()).isZero();
    }

    @Test
    void anAbandonedLoadWritesNothing() {
        // FR-015. The player disconnected while the read was still running, so there is no state
        // they ever received. The gate is what makes "while it was running" reproducible: the load
        // is held between beginLoad and the disconnect, exactly as it would be in production.
        UUID playerId = UUID.randomUUID();
        GatedExecutor gate = new GatedExecutor();
        DefaultSessionLifecycle lifecycle =
                new DefaultSessionLifecycle(
                        registry,
                        SessionLifecycleTest::bundleWithOneCharacter,
                        writer,
                        new StateVersionMigrator(QUIET),
                        gate,
                        Clock.fixed(NOW, ZoneOffset.UTC),
                        QUIET);

        CompletableFuture<PlayerSession> loading = lifecycle.beginLoad(playerId, TIMEOUT);
        lifecycle.abandonLoad(playerId); // the disconnect, mid-load
        gate.runQueued(); // the read returns - too late

        assertThatThrownBy(loading::join).hasCauseInstanceOf(SessionLoadException.class);
        assertThat(writer.writes()).isZero();
        // The session was never published either - FR-004.
        assertThat(registry.peek(playerId)).isEmpty();
    }

    @Test
    void endingAFailedSessionStillWritesNothing() {
        // Even if something calls endSession on a failed session, the state predicate blocks it.
        UUID playerId = UUID.randomUUID();
        PlayerCharacter character = PlayerCharacter.create(playerId, CharacterClass.MAGE, NOW);
        PlayerSession session = new PlayerSession(playerId, character, List.of(character));
        registry.open(session);
        session.transitionTo(SessionState.FAILED, NOW);

        DefaultSessionLifecycle lifecycle = lifecycleLoading(SessionLifecycleTest::bundleWithOneCharacter);
        lifecycle.endSession(playerId, SessionEndReason.QUIT).join();

        assertThat(writer.writes()).isZero();
        assertThat(registry.peek(playerId)).isEmpty();
    }

    // === the load path ===

    @Test
    void aSuccessfulLoadOpensASessionThatIsNotYetReady() {
        UUID playerId = UUID.randomUUID();
        DefaultSessionLifecycle lifecycle = lifecycleLoading(SessionLifecycleTest::bundleWithOneCharacter);

        PlayerSession session = lifecycle.beginLoad(playerId, TIMEOUT).join();

        assertThat(session.state()).isEqualTo(SessionState.LOADING);
        // FR-004: not queryable until explicitly released.
        assertThat(registry.find(playerId)).isEmpty();
    }

    @Test
    void markingReadyReleasesTheSession() {
        UUID playerId = UUID.randomUUID();
        DefaultSessionLifecycle lifecycle = lifecycleLoading(SessionLifecycleTest::bundleWithOneCharacter);
        lifecycle.beginLoad(playerId, TIMEOUT).join();

        lifecycle.markReady(playerId);

        assertThat(registry.find(playerId)).isPresent();
        assertThat(registry.require(playerId).readyAt()).contains(NOW);
    }

    @Test
    void aSecondLoadForTheSamePlayerIsRejected() {
        UUID playerId = UUID.randomUUID();
        DefaultSessionLifecycle lifecycle = lifecycleLoading(SessionLifecycleTest::bundleWithOneCharacter);
        lifecycle.beginLoad(playerId, TIMEOUT).join();

        assertThatThrownBy(() -> lifecycle.beginLoad(playerId, TIMEOUT).join())
                .hasCauseInstanceOf(DuplicateSessionException.class);
    }

    @Test
    void aRecordFromANewerBuildIsRefusedRatherThanInterpreted() {
        // FR-027: guessing at a future format corrupts it silently.
        UUID playerId = UUID.randomUUID();
        PlayerCharacter fromFuture =
                new PlayerCharacter(
                        UUID.randomUUID(),
                        playerId,
                        CharacterClass.ROGUE,
                        PlayerCharacter.CURRENT_DATA_VERSION + 1,
                        0L,
                        NOW,
                        NOW);
        DefaultSessionLifecycle lifecycle =
                lifecycleLoading(
                        id ->
                                new SessionBundle(
                                        id,
                                        Optional.of(
                                                rpg.core.persistence.PlayerState.initial(id, NOW)),
                                        List.of(fromFuture),
                                        List.of()));

        assertThatThrownBy(() -> lifecycle.beginLoad(playerId, TIMEOUT).join())
                .hasCauseInstanceOf(UnknownDataVersionException.class);
        assertThat(writer.writes()).isZero();
    }

    @Test
    void aPlayerWithoutCharactersGetsASessionWithoutOne() {
        // FR-021: no character is silently invented for them.
        UUID playerId = UUID.randomUUID();
        DefaultSessionLifecycle lifecycle =
                lifecycleLoading(
                        id ->
                                new SessionBundle(
                                        id,
                                        Optional.of(
                                                rpg.core.persistence.PlayerState.initial(id, NOW)),
                                        List.of(),
                                        List.of()));

        PlayerSession session = lifecycle.beginLoad(playerId, TIMEOUT).join();

        assertThat(session.activeCharacter()).isEmpty();
    }

    // === the unload path ===

    @Test
    void endingAReadySessionWritesOnceAndThenRemovesIt() {
        UUID playerId = UUID.randomUUID();
        DefaultSessionLifecycle lifecycle = lifecycleLoading(SessionLifecycleTest::bundleWithOneCharacter);
        lifecycle.beginLoad(playerId, TIMEOUT).join();
        lifecycle.markReady(playerId);

        lifecycle.endSession(playerId, SessionEndReason.QUIT).join();

        assertThat(writer.writes()).isEqualTo(1);
        // FR-008: removed only after the write finished.
        assertThat(registry.peek(playerId)).isEmpty();
    }

    @Test
    void allThreeSessionEndsTakeTheSamePathAndWriteExactlyOnce() {
        // FR-007 with FR-014: quit, kick and timeout are one event, not three. A separate kick
        // listener would double the write.
        for (SessionEndReason reason :
                List.of(SessionEndReason.QUIT, SessionEndReason.KICK, SessionEndReason.TIMEOUT)) {
            registry = new DefaultSessionRegistry();
            writer = new RecordingWriter();
            UUID playerId = UUID.randomUUID();
            DefaultSessionLifecycle lifecycle =
                    lifecycleLoading(SessionLifecycleTest::bundleWithOneCharacter);
            lifecycle.beginLoad(playerId, TIMEOUT).join();
            lifecycle.markReady(playerId);

            lifecycle.endSession(playerId, reason).join();

            assertThat(writer.writes()).as("reason %s", reason).isEqualTo(1);
        }
    }

    @Test
    void endingTwiceWritesOnlyOnce() {
        UUID playerId = UUID.randomUUID();
        DefaultSessionLifecycle lifecycle = lifecycleLoading(SessionLifecycleTest::bundleWithOneCharacter);
        lifecycle.beginLoad(playerId, TIMEOUT).join();
        lifecycle.markReady(playerId);

        lifecycle.endSession(playerId, SessionEndReason.QUIT).join();
        lifecycle.endSession(playerId, SessionEndReason.KICK).join();

        assertThat(writer.writes()).isEqualTo(1);
    }

    @Test
    void endingAnUnknownSessionIsHarmless() {
        DefaultSessionLifecycle lifecycle = lifecycleLoading(SessionLifecycleTest::bundleWithOneCharacter);

        lifecycle.endSession(UUID.randomUUID(), SessionEndReason.QUIT).join();

        assertThat(writer.writes()).isZero();
    }

    @Test
    void aSessionWhoseFinalWriteFailedIsKeptRatherThanDropped() {
        // Dropping it would discard the only copy of those changes.
        UUID playerId = UUID.randomUUID();
        writer.failNext();
        DefaultSessionLifecycle lifecycle = lifecycleLoading(SessionLifecycleTest::bundleWithOneCharacter);
        lifecycle.beginLoad(playerId, TIMEOUT).join();
        lifecycle.markReady(playerId);

        lifecycle.endSession(playerId, SessionEndReason.QUIT).exceptionally(t -> null).join();

        assertThat(registry.peek(playerId)).isPresent();
    }

    /** Holds submitted work until the test releases it, so "mid-load" is reproducible. */
    private static final class GatedExecutor implements Executor {

        private final java.util.List<Runnable> queued = new java.util.ArrayList<>();

        @Override
        public void execute(Runnable command) {
            queued.add(command);
        }

        void runQueued() {
            java.util.List<Runnable> pending = java.util.List.copyOf(queued);
            queued.clear();
            pending.forEach(Runnable::run);
        }
    }

    /** Records what the lifecycle asked the persistence side to do. */
    private static final class RecordingWriter implements DefaultSessionLifecycle.SessionWriter {

        private final AtomicInteger writes = new AtomicInteger();
        private final AtomicInteger dirtyMarks = new AtomicInteger();
        private volatile boolean failNext;

        int writes() {
            return writes.get();
        }

        int dirtyMarks() {
            return dirtyMarks.get();
        }

        void failNext() {
            failNext = true;
        }

        @Override
        public CompletableFuture<Void> writeAndAwait(UUID playerId) {
            writes.incrementAndGet();
            if (failNext) {
                failNext = false;
                return CompletableFuture.failedFuture(new IllegalStateException("write failed"));
            }
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void markCharactersDirty(List<PlayerCharacter> characters) {
            dirtyMarks.incrementAndGet();
        }
    }
}
