package rpg.core.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * T065: reading a player without opening a session (FR-022, FR-024).
 *
 * <p>Two properties, and both are easy to get wrong in the direction that looks harmless:
 *
 * <ul>
 *   <li>Reading must not open a session. A leaderboard that touches 100 players would otherwise
 *       create 100 sessions that nothing ever unloads - a leak produced by a read.
 *   <li>For a connected player the answer must come from the live session, not from storage. The
 *       stored copy is up to one autosave interval behind, and a player looking at a leaderboard
 *       knows exactly what they did in the last minute.
 * </ul>
 */
class OfflinePlayerReaderTest {

    private static final Instant NOW = Instant.parse("2026-08-19T12:00:00Z");

    private DefaultSessionRegistry registry;
    private StubCharacterRepository stored;
    private DefaultOfflinePlayerReader reader;

    @BeforeEach
    void setUp() {
        registry = new DefaultSessionRegistry();
        stored = new StubCharacterRepository();
        reader = new DefaultOfflinePlayerReader(registry, stored);
    }

    @Test
    void readingAnOfflinePlayerGoesToStorage() throws Exception {
        UUID playerId = UUID.randomUUID();
        stored.put(playerId, List.of(character(playerId, CharacterClass.MAGE)));

        PlayerSnapshot snapshot = reader.read(playerId).get().orElseThrow();

        assertThat(snapshot.online()).isFalse();
        assertThat(snapshot.characters()).hasSize(1);
        assertThat(stored.reads()).isEqualTo(1);
    }

    @Test
    void readingAnOfflinePlayerOpensNoSession() throws Exception {
        // FR-022. A read that changes what it observes is the leak nobody looks for.
        UUID playerId = UUID.randomUUID();
        stored.put(playerId, List.of(character(playerId, CharacterClass.ROGUE)));

        reader.read(playerId).get();

        assertThat(registry.activeSessionCount()).isZero();
        assertThat(registry.peek(playerId)).isEmpty();
    }

    @Test
    void readingAConnectedPlayerUsesTheLiveSessionAndNeverTouchesStorage() throws Exception {
        // FR-024. The stored copy can be up to one autosave interval old.
        UUID playerId = UUID.randomUUID();
        PlayerCharacter live = character(playerId, CharacterClass.WARRIOR);
        openReadySession(playerId, live);
        stored.put(playerId, List.of()); // storage does not know about them yet

        PlayerSnapshot snapshot = reader.read(playerId).get().orElseThrow();

        assertThat(snapshot.online()).isTrue();
        assertThat(snapshot.characters()).containsExactly(live);
        assertThat(stored.reads()).isZero();
    }

    @Test
    void aPlayerStillLoadingIsReadFromStorageRatherThanFromAHalfBuiltSession() throws Exception {
        // The registry reports a non-ready session as absent (FR-004), so the read falls through to
        // storage instead of returning values the player has not been given yet.
        UUID playerId = UUID.randomUUID();
        PlayerCharacter character = character(playerId, CharacterClass.MAGE);
        registry.open(new PlayerSession(playerId, character, List.of(character)));
        stored.put(playerId, List.of(character));

        PlayerSnapshot snapshot = reader.read(playerId).get().orElseThrow();

        assertThat(snapshot.online()).isFalse();
        assertThat(stored.reads()).isEqualTo(1);
    }

    @Test
    void aPlayerNobodyHasEverSeenIsReportedAsAbsent() throws Exception {
        // Empty, not an invented snapshot: "no record" and "a record with nothing in it" mean
        // different things to a tool.
        Optional<PlayerSnapshot> snapshot = reader.read(UUID.randomUUID()).get();

        assertThat(snapshot).isEmpty();
    }

    @Test
    void charactersOfFollowsTheSameOrder() throws Exception {
        UUID online = UUID.randomUUID();
        PlayerCharacter live = character(online, CharacterClass.WARRIOR);
        openReadySession(online, live);

        UUID offline = UUID.randomUUID();
        stored.put(offline, List.of(character(offline, CharacterClass.ROGUE)));

        assertThat(reader.charactersOf(online).get()).containsExactly(live);
        assertThat(stored.reads()).isZero();

        assertThat(reader.charactersOf(offline).get()).hasSize(1);
        assertThat(stored.reads()).isEqualTo(1);
    }

    @Test
    void thereIsNoWayToWriteThroughThisPath() {
        // A tool that must change something goes through the block that owns the data. If a write
        // method ever appears here, it bypasses the session layer for every connected player.
        assertThat(OfflinePlayerReader.class.getMethods())
                .extracting(java.lang.reflect.Method::getName)
                .containsExactlyInAnyOrder("read", "charactersOf");
    }

    private void openReadySession(UUID playerId, PlayerCharacter character) {
        PlayerSession session = new PlayerSession(playerId, character, List.of(character));
        registry.open(session);
        session.transitionTo(SessionState.READY, NOW);
    }

    private static PlayerCharacter character(UUID playerId, CharacterClass characterClass) {
        return PlayerCharacter.create(playerId, characterClass, NOW);
    }

    /** Storage that counts how often it was asked - the point of several tests above. */
    private static final class StubCharacterRepository implements CharacterRepository {

        private final java.util.Map<UUID, List<PlayerCharacter>> byPlayer =
                new java.util.HashMap<>();
        private final AtomicInteger reads = new AtomicInteger();

        void put(UUID playerId, List<PlayerCharacter> characters) {
            byPlayer.put(playerId, List.copyOf(characters));
        }

        int reads() {
            return reads.get();
        }

        @Override
        public CompletableFuture<List<PlayerCharacter>> findByPlayer(UUID playerId) {
            reads.incrementAndGet();
            return CompletableFuture.completedFuture(byPlayer.getOrDefault(playerId, List.of()));
        }

        @Override
        public CompletableFuture<Optional<PlayerCharacter>> find(UUID characterId) {
            throw new UnsupportedOperationException("not used by this test");
        }

        @Override
        public CompletableFuture<PlayerCharacter> create(
                UUID playerId, CharacterClass characterClass) {
            throw new UnsupportedOperationException("not used by this test");
        }

        @Override
        public void markDirty(UUID characterId) {
            throw new UnsupportedOperationException("not used by this test");
        }
    }
}
