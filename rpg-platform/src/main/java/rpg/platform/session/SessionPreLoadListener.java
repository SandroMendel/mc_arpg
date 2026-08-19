package rpg.platform.session;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import rpg.core.message.MessageKey;
import rpg.core.message.Messages;
import rpg.core.session.PlayerSession;
import rpg.core.session.SessionLifecycle;
import rpg.core.session.SessionMessageKeys;
import rpg.core.session.UnknownDataVersionException;

/**
 * Loads a player's session before they enter the world.
 *
 * <p>This is where the block's worst failure is made impossible rather than merely handled. Loading
 * at join means a failure has to unwind a player who is already in the world, and every such
 * unwinding is a chance to leave an empty profile behind that later overwrites real progress.
 * Loading here means a failure is a {@code disallow} - at that moment no player object exists, so
 * there is nothing to overwrite and nothing to clean up.
 *
 * <p>The event is asynchronous, so blocking on the database is expected and correct here
 * (Constitution I.2).
 *
 * <p><strong>Priority {@code LOW}, not {@code LOWEST}.</strong> B01's {@code PreJoinGuard} sits at
 * {@code LOWEST} and refuses connections until the bootstrap has finished. At equal priority the
 * order between them would be undefined, and this listener could try to load before the modules it
 * needs are up. It also returns early if the event was already disallowed.
 */
public final class SessionPreLoadListener implements Listener {

    private final SessionLifecycle lifecycle;
    private final PendingSessionStash stash;
    private final Messages messages;
    private final Duration loadTimeout;
    private final Supplier<Optional<MessageKey>> persistenceRefusal;
    private final Logger logger;

    public SessionPreLoadListener(
            SessionLifecycle lifecycle,
            PendingSessionStash stash,
            Messages messages,
            Duration loadTimeout,
            Supplier<Optional<MessageKey>> persistenceRefusal,
            Logger logger) {
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        this.stash = Objects.requireNonNull(stash, "stash");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.loadTimeout = Objects.requireNonNull(loadTimeout, "loadTimeout");
        this.persistenceRefusal = Objects.requireNonNull(persistenceRefusal, "persistenceRefusal");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            // Already refused - by B01's bootstrap guard or another plugin. Loading now would do
            // work for a session nobody will collect.
            return;
        }

        UUID playerId = event.getUniqueId();

        // B02 knows whether storage can be reached at all, and whether its write buffer is
        // exhausted. Both mean a session must not be granted (B02/FR-005a, FR-009b). Until now
        // those checks existed but nothing consulted them.
        Optional<MessageKey> refusal = persistenceRefusal.get();
        if (refusal.isPresent()) {
            disallow(event, refusal.get());
            return;
        }

        try {
            PlayerSession session =
                    lifecycle
                            .beginLoad(playerId, loadTimeout)
                            .get(loadTimeout.toMillis(), TimeUnit.MILLISECONDS);
            stash.put(session);
        } catch (java.util.concurrent.TimeoutException timeout) {
            lifecycle.abandonLoad(playerId);
            disallow(event, SessionMessageKeys.KICK_LOAD_TIMEOUT);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            lifecycle.abandonLoad(playerId);
            disallow(event, SessionMessageKeys.KICK_LOAD_FAILED);
        } catch (Exception failure) {
            lifecycle.abandonLoad(playerId);
            // Nothing has been written and nothing will be: the session never left FAILED.
            logger.log(Level.WARNING, "[session] refusing login for " + playerId, failure);
            disallow(event, keyFor(failure));
        }
    }

    private static MessageKey keyFor(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof UnknownDataVersionException) {
                return SessionMessageKeys.KICK_UNKNOWN_VERSION;
            }
        }
        return SessionMessageKeys.KICK_LOAD_FAILED;
    }

    private void disallow(AsyncPlayerPreLoginEvent event, MessageKey key) {
        event.disallow(
                AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                Component.text(messages.get(key), NamedTextColor.RED));
    }
}
