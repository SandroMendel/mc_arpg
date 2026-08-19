package rpg.platform;

import java.util.Objects;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import rpg.core.message.MessageKey;
import rpg.core.message.Messages;
import rpg.core.module.BootstrapState;

/**
 * Refuses player sessions until the bootstrap has completed successfully (FR-013).
 *
 * <p>Closes the race between server start and the first join: a player connecting while modules are
 * still initialising would otherwise be handed a session backed by half-registered services.
 *
 * <p>Hooks {@link AsyncPlayerPreLoginEvent} rather than {@code PlayerJoinEvent} on purpose - the
 * pre-login stage runs before any player state is created, so a refused connection leaves nothing
 * behind to clean up. It fires off the main thread, which is why {@link BootstrapState} is
 * atomically readable.
 *
 * <p>Priority {@code LOWEST}: decide before any other plugin invests work in this login.
 *
 * <p>The kick texts come from {@link Messages}, not from this file (Constitution V). This class is
 * also where that rule is turned into code: {@code rpg-core} resolves a key to a {@code String},
 * and the conversion into an Adventure {@code Component} happens here, in the one layer allowed to
 * know Paper types.
 */
public final class PreJoinGuard implements Listener {

    private final BootstrapState state;
    private final Messages messages;

    public PreJoinGuard(BootstrapState state, Messages messages) {
        this.state = Objects.requireNonNull(state, "state");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (state.acceptsPlayers()) {
            return;
        }
        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, kickMessage());
    }

    private Component kickMessage() {
        return switch (state.phase()) {
            case FAILED ->
                    coloured(PlatformMessageKeys.KICK_BOOTSTRAP_FAILED, NamedTextColor.RED);
            case SHUTTING_DOWN ->
                    coloured(PlatformMessageKeys.KICK_SHUTTING_DOWN, NamedTextColor.YELLOW);
            default -> coloured(PlatformMessageKeys.KICK_STARTING_UP, NamedTextColor.YELLOW);
        };
    }

    private Component coloured(MessageKey key, NamedTextColor colour) {
        return Component.text(messages.get(key), colour);
    }
}
