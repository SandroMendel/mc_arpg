package rpg.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import rpg.core.message.MapMessages;
import rpg.core.message.MessageKey;
import rpg.core.message.Messages;
import rpg.core.module.BootstrapState;

/**
 * FR-013 / quickstart section 2: a player must not receive a session before the bootstrap has
 * completed successfully.
 *
 * <p>Covers the race the spec calls out explicitly - a player connecting while the server is still
 * starting - as well as the failed-bootstrap and shutdown cases, where letting anyone in would be
 * worse still.
 */
class PreJoinGuardTest {

    private BootstrapState state;
    private PreJoinGuard guard;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        state = new BootstrapState();
        guard = new PreJoinGuard(state, testMessages());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    /** Minimal message store covering exactly the keys the guard declares. */
    private static Messages testMessages() {
        Map<String, String> texts = new LinkedHashMap<>();
        for (MessageKey key : PlatformMessageKeys.all()) {
            texts.put(key.value(), "text for " + key.value());
        }
        return new MapMessages(texts);
    }

    private static AsyncPlayerPreLoginEvent newLoginAttempt() throws Exception {
        return new AsyncPlayerPreLoginEvent(
                "Tester", InetAddress.getLoopbackAddress(), UUID.randomUUID(), true);
    }

    @Test
    void aJoinBeforeTheBootstrapStartedIsRefused() throws Exception {
        AsyncPlayerPreLoginEvent event = newLoginAttempt();

        guard.onPreLogin(event);

        assertThat(event.getLoginResult()).isNotEqualTo(AsyncPlayerPreLoginEvent.Result.ALLOWED);
    }

    @Test
    void aJoinDuringTheBootstrapIsRefused() throws Exception {
        state.markInProgress();
        AsyncPlayerPreLoginEvent event = newLoginAttempt();

        guard.onPreLogin(event);

        assertThat(event.getLoginResult()).isNotEqualTo(AsyncPlayerPreLoginEvent.Result.ALLOWED);
    }

    @Test
    void aJoinAfterASuccessfulBootstrapIsAllowed() throws Exception {
        state.markInProgress();
        state.markReady();
        AsyncPlayerPreLoginEvent event = newLoginAttempt();

        guard.onPreLogin(event);

        assertThat(event.getLoginResult()).isEqualTo(AsyncPlayerPreLoginEvent.Result.ALLOWED);
    }

    @Test
    void aJoinAfterAFailedBootstrapIsRefused() throws Exception {
        state.markFailed("stat-engine could not read its configuration");
        AsyncPlayerPreLoginEvent event = newLoginAttempt();

        guard.onPreLogin(event);

        assertThat(event.getLoginResult()).isNotEqualTo(AsyncPlayerPreLoginEvent.Result.ALLOWED);
        assertThat(state.failureReason()).contains("stat-engine");
    }

    @Test
    void aJoinDuringShutdownIsRefused() throws Exception {
        state.markInProgress();
        state.markReady();
        state.markShuttingDown();
        AsyncPlayerPreLoginEvent event = newLoginAttempt();

        guard.onPreLogin(event);

        assertThat(event.getLoginResult()).isNotEqualTo(AsyncPlayerPreLoginEvent.Result.ALLOWED);
    }

    @Test
    void theRefusalCarriesAMessageTheOperatorCanActOn() throws Exception {
        state.markFailed("boom");
        AsyncPlayerPreLoginEvent event = newLoginAttempt();

        guard.onPreLogin(event);

        assertThat(event.kickMessage()).isNotNull();
    }
}
