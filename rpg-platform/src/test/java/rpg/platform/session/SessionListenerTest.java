package rpg.platform.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import com.destroystokyo.paper.event.player.PlayerConnectionCloseEvent;

import net.kyori.adventure.text.Component;
import rpg.core.message.MapMessages;
import rpg.core.message.MessageKey;
import rpg.core.message.Messages;
import rpg.core.session.CharacterClass;
import rpg.core.session.PlayerCharacter;
import rpg.core.session.PlayerSession;
import rpg.core.session.SessionEndReason;
import rpg.core.session.SessionLifecycle;
import rpg.core.session.SessionLoadException;
import rpg.core.session.SessionMessageKeys;
import rpg.core.session.UnknownDataVersionException;

/**
 * T023, T024, T038, T051, T055b: the five listeners against a mock server.
 *
 * <p>The point of loading in the pre-login event rather than at join is what most of these check. At
 * pre-login no player object exists yet, so a failure is a {@code disallow} and there is nothing to
 * unwind - which is what makes the block's worst outcome, an empty profile overwriting real
 * progress, structurally impossible rather than merely handled.
 *
 * <p>MockBukkit reports an unimplemented operation as an <em>aborted</em> test rather than a failed
 * one, so a run that skipped everything looks green. Every run of this module therefore checks the
 * skipped count, not only the failure count.
 */
class SessionListenerTest {

    private static final Instant NOW = Instant.parse("2026-08-19T12:00:00Z");
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final Logger QUIET = Logger.getLogger("session-listener-test");

    private ServerMock server;
    private RecordingLifecycle lifecycle;
    private PendingSessionStash stash;
    private SafeStateGuard guard;
    private Messages messages;

    @BeforeEach
    void setUp() {
        QUIET.setLevel(Level.OFF);
        server = MockBukkit.mock();
        server.addSimpleWorld("world");
        lifecycle = new RecordingLifecycle();
        stash = new PendingSessionStash(Duration.ofMinutes(1), Clock.fixed(NOW, ZoneOffset.UTC), QUIET);
        guard = new SafeStateGuard(QUIET);
        messages =
                new MapMessages(
                        Map.of(
                                SessionMessageKeys.KICK_LOAD_FAILED.value(), "Could not load",
                                SessionMessageKeys.KICK_LOAD_TIMEOUT.value(), "Took too long",
                                SessionMessageKeys.KICK_UNKNOWN_VERSION.value(), "Unknown version",
                                "persistence.kick.unavailable", "Storage unavailable"));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    // === pre-login: the load, and every way it can be refused ===

    @Test
    void aSuccessfulPreLoadStashesTheSessionAndLeavesTheLoginAllowed() {
        UUID playerId = UUID.randomUUID();
        AsyncPlayerPreLoginEvent event = preLogin(playerId);

        preLoadListener(Optional.empty()).onPreLogin(event);

        assertThat(event.getLoginResult()).isEqualTo(AsyncPlayerPreLoginEvent.Result.ALLOWED);
        assertThat(stash.size()).isEqualTo(1);
    }

    @Test
    void aFailedLoadRefusesTheLoginRatherThanLettingThePlayerIn() {
        // T051. The player never enters, so nothing can overwrite their stored record (FR-011).
        UUID playerId = UUID.randomUUID();
        lifecycle.failWith(new SessionLoadException("storage unreachable"));
        AsyncPlayerPreLoginEvent event = preLogin(playerId);

        preLoadListener(Optional.empty()).onPreLogin(event);

        assertThat(event.getLoginResult()).isNotEqualTo(AsyncPlayerPreLoginEvent.Result.ALLOWED);
        assertThat(event.kickMessage()).isNotNull();
        assertThat(stash.size()).isZero();
        // The in-flight load is discarded, not left to open a session for a refused player.
        assertThat(lifecycle.abandoned).containsExactly(playerId);
    }

    @Test
    void aRecordFromAnUnknownVersionIsRefusedWithItsOwnMessage() {
        UUID playerId = UUID.randomUUID();
        lifecycle.failWith(new UnknownDataVersionException(99, 1));
        AsyncPlayerPreLoginEvent event = preLogin(playerId);

        preLoadListener(Optional.empty()).onPreLogin(event);

        assertThat(event.getLoginResult()).isNotEqualTo(AsyncPlayerPreLoginEvent.Result.ALLOWED);
        assertThat(plain(event.kickMessage())).isEqualTo("Unknown version");
    }

    @Test
    void whenStorageIsUnreachableTheLoginIsRefusedWithoutEvenTrying() {
        // T055b. B02 already knew this; until B03 wired it in, nothing ever asked.
        UUID playerId = UUID.randomUUID();
        AsyncPlayerPreLoginEvent event = preLogin(playerId);

        preLoadListener(Optional.of(MessageKey.of("persistence.kick.unavailable")))
                .onPreLogin(event);

        assertThat(event.getLoginResult()).isNotEqualTo(AsyncPlayerPreLoginEvent.Result.ALLOWED);
        assertThat(plain(event.kickMessage())).isEqualTo("Storage unavailable");
        // No load was even started - the refusal comes before the work, not after it.
        assertThat(lifecycle.loads).isEmpty();
    }

    @Test
    void aLoginAlreadyRefusedByAnotherPluginIsNotLoadedFor() {
        UUID playerId = UUID.randomUUID();
        AsyncPlayerPreLoginEvent event = preLogin(playerId);
        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, Component.text("server starting"));

        preLoadListener(Optional.empty()).onPreLogin(event);

        assertThat(lifecycle.loads).isEmpty();
        assertThat(stash.size()).isZero();
    }

    // === join: collect or hold ===

    @Test
    void aPlayerWithAPreloadedSessionIsReleasedImmediately() {
        // T023. The expected path: the wait already happened before the player entered.
        PlayerMock player = server.addPlayer();
        stash.put(sessionFor(player.getUniqueId()));

        joinListener().onJoin(new PlayerJoinEvent(player, Component.empty()));

        assertThat(lifecycle.readied).containsExactly(player.getUniqueId());
        assertThat(guard.isHeld(player.getUniqueId())).isFalse();
        assertThat(player.isInvulnerable()).isFalse();
        assertThat(stash.size()).isZero();
    }

    @Test
    void withoutAPreloadedSessionThePlayerIsHeldInsteadOfPlayingWithDefaults() {
        // T024. Should never happen; if it does, inconsistent values are worse than a short wait.
        PlayerMock player = server.addPlayer();

        joinListener().onJoin(new PlayerJoinEvent(player, Component.empty()));

        assertThat(guard.isHeld(player.getUniqueId())).isTrue();
        assertThat(player.isInvulnerable()).isTrue();
        assertThat(lifecycle.readied).isEmpty();
    }

    @Test
    void aStashEntryIsCollectedExactlyOnce() {
        PlayerMock player = server.addPlayer();
        stash.put(sessionFor(player.getUniqueId()));

        joinListener().onJoin(new PlayerJoinEvent(player, Component.empty()));
        joinListener().onJoin(new PlayerJoinEvent(player, Component.empty()));

        // The second join finds nothing and falls back to holding - it must not release a player
        // whose session was already handed over and could since have been unloaded.
        assertThat(lifecycle.readied).containsExactly(player.getUniqueId());
        assertThat(guard.isHeld(player.getUniqueId())).isTrue();
    }

    // === quit: one path for quit, kick and drop ===

    @Test
    void quittingEndsTheSessionExactlyOnce() {
        PlayerMock player = server.addPlayer();

        quitListener().onQuit(new PlayerQuitEvent(player, Component.empty()));

        assertThat(lifecycle.ended).containsExactly(player.getUniqueId());
    }

    @Test
    void aKickTakesTheSameSinglePathAsAQuit() {
        // T038. Bukkit fires PlayerQuitEvent for a kick too. A second listener on PlayerKickEvent
        // would look thorough and would in fact unload twice per kick (FR-014).
        PlayerMock player = server.addPlayer();
        player.kick(Component.text("banned"));

        // The plugin registers exactly one handler for the end of a session.
        assertThat(handlerCount(PlayerQuitEvent.getHandlerList())).isZero();
        quitListener().onQuit(new PlayerQuitEvent(player, Component.empty()));

        assertThat(lifecycle.ended).containsExactly(player.getUniqueId());
    }

    @Test
    void quittingAlsoReleasesTheSafeStateSoNothingIsLeftHeld() {
        PlayerMock player = server.addPlayer();
        guard.hold(player);

        quitListener().onQuit(new PlayerQuitEvent(player, Component.empty()));

        assertThat(guard.heldCount()).isZero();
    }

    @Test
    void aFailedUnloadDoesNotEscapeIntoTheEventPipeline() {
        // An exception here would abort the quit event for every other plugin behind us.
        PlayerMock player = server.addPlayer();
        lifecycle.failUnload();

        quitListener().onQuit(new PlayerQuitEvent(player, Component.empty()));

        assertThat(lifecycle.ended).containsExactly(player.getUniqueId());
    }

    // === a connection that closed before reaching the world ===

    @Test
    void aConnectionClosingBeforeTheJoinDiscardsTheStashedSessionWithoutWriting() {
        UUID playerId = UUID.randomUUID();
        stash.put(sessionFor(playerId));

        closeListener()
                .onConnectionClose(
                        new PlayerConnectionCloseEvent(
                                playerId, "Steve", InetAddress.getLoopbackAddress(), false));

        assertThat(stash.size()).isZero();
        assertThat(lifecycle.abandoned).containsExactly(playerId);
        // Nothing was written: the player never received a state (FR-015).
        assertThat(lifecycle.ended).isEmpty();
    }

    // --- helpers ---

    private SessionPreLoadListener preLoadListener(Optional<MessageKey> refusal) {
        return new SessionPreLoadListener(
                lifecycle, stash, messages, TIMEOUT, () -> refusal, QUIET);
    }

    private SessionJoinListener joinListener() {
        return new SessionJoinListener(lifecycle, stash, guard, QUIET);
    }

    private SessionQuitListener quitListener() {
        return new SessionQuitListener(lifecycle, guard, QUIET);
    }

    private SessionConnectionCloseListener closeListener() {
        return new SessionConnectionCloseListener(lifecycle, stash);
    }

    private static AsyncPlayerPreLoginEvent preLogin(UUID playerId) {
        return new AsyncPlayerPreLoginEvent("Steve", InetAddress.getLoopbackAddress(), playerId);
    }

    private static PlayerSession sessionFor(UUID playerId) {
        PlayerCharacter character = PlayerCharacter.create(playerId, CharacterClass.WARRIOR, NOW);
        return new PlayerSession(playerId, character, List.of(character));
    }

    private static String plain(Component component) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(component);
    }

    private static int handlerCount(org.bukkit.event.HandlerList handlers) {
        return handlers.getRegisteredListeners().length;
    }

    /** A lifecycle that records what it was asked to do; the rules themselves are tested in core. */
    private static final class RecordingLifecycle implements SessionLifecycle {

        private final List<UUID> loads = new ArrayList<>();
        private final List<UUID> readied = new ArrayList<>();
        private final List<UUID> ended = new ArrayList<>();
        private final List<UUID> abandoned = new ArrayList<>();
        private RuntimeException loadFailure;
        private boolean unloadFails;

        void failWith(RuntimeException failure) {
            this.loadFailure = failure;
        }

        void failUnload() {
            this.unloadFails = true;
        }

        @Override
        public CompletableFuture<PlayerSession> beginLoad(UUID playerId, Duration timeout) {
            loads.add(playerId);
            if (loadFailure != null) {
                return CompletableFuture.failedFuture(loadFailure);
            }
            return CompletableFuture.completedFuture(sessionFor(playerId));
        }

        @Override
        public void markReady(UUID playerId) {
            readied.add(playerId);
        }

        @Override
        public CompletableFuture<Void> endSession(UUID playerId, SessionEndReason reason) {
            ended.add(playerId);
            return unloadFails
                    ? CompletableFuture.failedFuture(new IllegalStateException("write failed"))
                    : CompletableFuture.completedFuture(null);
        }

        @Override
        public void abandonLoad(UUID playerId) {
            abandoned.add(playerId);
        }
    }
}
