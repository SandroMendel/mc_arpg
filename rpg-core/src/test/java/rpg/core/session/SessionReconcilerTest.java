package rpg.core.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * T036, T037: the assurance that no session is left behind (FR-009, SC-008).
 *
 * <p>The unload path is careful, and careful is not the same as guaranteed. A plugin can cancel the
 * quit event, a cleanup can fail midway, a block written two years from now can take a path nobody
 * anticipated. The reconciliation sweep does not try to enumerate those cases - it compares what is
 * held against who is actually connected and removes the difference, whatever produced it.
 *
 * <p>The long-run test is the one that matters for a server that stays up for weeks: after 10,000
 * connect/disconnect cycles the registry must be empty, not merely small.
 */
class SessionReconcilerTest {

    private static final Instant NOW = Instant.parse("2026-08-19T12:00:00Z");
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final Logger QUIET = Logger.getLogger("session-reconciler-test");

    private DefaultSessionRegistry registry;
    private DefaultSessionLifecycle lifecycle;
    private AtomicInteger writes;
    private AtomicInteger stashSweeps;
    private final List<UUID> connected = new ArrayList<>();

    @BeforeEach
    void setUp() {
        QUIET.setLevel(Level.OFF);
        registry = new DefaultSessionRegistry();
        writes = new AtomicInteger();
        stashSweeps = new AtomicInteger();
        connected.clear();
        lifecycle =
                new DefaultSessionLifecycle(
                        registry,
                        SessionReconcilerTest::bundleFor,
                        new CountingWriter(writes),
                        new StateVersionMigrator(QUIET),
                        Runnable::run,
                        Clock.fixed(NOW, ZoneOffset.UTC),
                        QUIET);
    }

    private SessionReconciler reconciler() {
        return new SessionReconciler(
                registry,
                () -> List.copyOf(connected),
                lifecycle,
                stashSweeps::incrementAndGet,
                QUIET);
    }

    @Test
    void aSessionWithoutAConnectedPlayerIsEndedAndRemoved() {
        UUID playerId = openReady();
        // The player is not in the connected list: their quit never reached the unload path.

        int reconciled = reconciler().reconcileOnce();

        assertThat(reconciled).isEqualTo(1);
        assertThat(registry.peek(playerId)).isEmpty();
        assertThat(registry.activeSessionCount()).isZero();
    }

    @Test
    void anOrphanIsEndedThroughTheNormalWritePathRatherThanDropped() {
        // It may still hold unwritten progress. Dropping it silently would lose exactly what the
        // sweep is meant to protect.
        openReady();

        reconciler().reconcileOnce();

        assertThat(writes.get()).isEqualTo(1);
    }

    @Test
    void aSessionWhosePlayerIsStillConnectedIsLeftAlone() {
        UUID playerId = openReady();
        connected.add(playerId);

        int reconciled = reconciler().reconcileOnce();

        assertThat(reconciled).isZero();
        assertThat(registry.find(playerId)).isPresent();
        assertThat(writes.get()).isZero();
    }

    @Test
    void aSessionStillLoadingIsNotMistakenForAnOrphan() {
        // The player is connected but has not been released yet. Ending them here would unload a
        // session that is still being set up.
        UUID playerId = UUID.randomUUID();
        lifecycle.beginLoad(playerId, TIMEOUT).join();
        connected.add(playerId);

        assertThat(reconciler().reconcileOnce()).isZero();
        assertThat(registry.peek(playerId)).isPresent();
    }

    @Test
    void everySweepAlsoExpiresStaleStashEntries() {
        // One pass that guarantees nothing is left behind, rather than two that each hope so.
        reconciler().reconcileOnce();
        reconciler().reconcileOnce();

        assertThat(stashSweeps.get()).isEqualTo(2);
    }

    @Test
    void aSweepWithNothingToDoIsCheap() {
        assertThat(reconciler().reconcileOnce()).isZero();
        assertThat(writes.get()).isZero();
    }

    @Test
    void afterTenThousandConnectionsAndDisconnectionsNothingIsLeftBehind() {
        // SC-008. The number is what makes this meaningful: a leak of one session per thousand
        // logins is invisible in a short test and fills the heap of a server that runs for a month.
        SessionReconciler reconciler = reconciler();
        for (int i = 0; i < 10_000; i++) {
            UUID playerId = UUID.randomUUID();
            lifecycle.beginLoad(playerId, TIMEOUT).join();
            lifecycle.markReady(playerId);
            lifecycle.endSession(playerId, SessionEndReason.QUIT).join();
        }

        assertThat(registry.activeSessionCount()).isZero();
        assertThat(registry.heldPlayerIds()).isEmpty();
        // And the sweep confirms it rather than the test taking the registry's word for it.
        assertThat(reconciler.reconcileOnce()).isZero();
    }

    @Test
    void evenWhenEveryUnloadIsSkippedTheSweepStillEmptiesTheRegistry() {
        // The pessimistic case: 1,000 sessions whose quit never fired at all. This is what the
        // assurance in FR-009 actually has to cover.
        Set<UUID> opened = new java.util.HashSet<>();
        for (int i = 0; i < 1_000; i++) {
            opened.add(openReady());
        }
        assertThat(registry.activeSessionCount()).isEqualTo(1_000);

        int reconciled = reconciler().reconcileOnce();

        assertThat(reconciled).isEqualTo(1_000);
        assertThat(registry.activeSessionCount()).isZero();
        assertThat(writes.get()).isEqualTo(opened.size());
    }

    private UUID openReady() {
        UUID playerId = UUID.randomUUID();
        lifecycle.beginLoad(playerId, TIMEOUT).join();
        lifecycle.markReady(playerId);
        return playerId;
    }

    private static SessionBundle bundleFor(UUID playerId) {
        PlayerCharacter character = PlayerCharacter.create(playerId, CharacterClass.WARRIOR, NOW);
        return new SessionBundle(
                playerId,
                Optional.of(rpg.core.persistence.PlayerState.initial(playerId, NOW)),
                List.of(character),
                List.of(),
                List.of(),
                List.of());
    }

    private record CountingWriter(AtomicInteger writes)
            implements DefaultSessionLifecycle.SessionWriter {

        @Override
        public CompletableFuture<Void> writeAndAwait(UUID playerId) {
            writes.incrementAndGet();
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void markCharactersDirty(List<PlayerCharacter> characters) {
            // Nothing to record: the migration path has its own test.
        }
    }
}
