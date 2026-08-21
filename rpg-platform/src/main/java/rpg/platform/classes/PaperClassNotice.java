package rpg.platform.classes;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.Server;
import org.bukkit.entity.Player;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import rpg.core.classes.ClassNotice;
import rpg.core.message.MessageKey;
import rpg.core.message.Messages;

/**
 * Shows a notice as a title with a sound - the seam {@link ClassNotice} names, until B13 takes it over.
 *
 * <p>Title and sound because that is what the notice is for: a full inventory is a state the player has
 * to act on, and a chat line scrolls away unread while items keep failing to be picked up. The sound is
 * what makes it land when the player is looking at the world rather than at the screen centre.
 *
 * <p>The sound is addressed by key rather than through a {@code Sound} constant. The constant set has
 * changed shape more than once across versions, and a key that does not resolve is a silent notice -
 * not a startup failure or an exception in an event handler.
 */
public final class PaperClassNotice implements ClassNotice {

    /**
     * Timing of the title: brief, because it repeats.
     *
     * <p>{@code InventoryFullNoticeListener} already limits the notice to once every fifteen seconds; a
     * title that lingered would still be on screen when the next one arrived.
     */
    private static final Title.Times TIMES =
            Title.Times.times(
                    Duration.ofMillis(200), Duration.ofSeconds(2), Duration.ofMillis(400));

    private static final Key SOUND = Key.key("minecraft", "block.note_block.bass");

    private final Server server;
    private final Messages messages;

    public PaperClassNotice(Server server, Messages messages) {
        this.server = Objects.requireNonNull(server, "server");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    @Override
    public void show(UUID playerId, MessageKey key) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(key, "key");
        Player player = server.getPlayer(playerId);
        if (player == null) {
            // Gone between the event and this call. A notice is not worth an exception (Constitution
            // VI) and there is nobody left to show it to.
            return;
        }
        player.showTitle(
                Title.title(
                        Component.text(messages.get(key), NamedTextColor.RED),
                        Component.empty(),
                        TIMES));
        player.playSound(Sound.sound(SOUND, Sound.Source.MASTER, 1.0f, 1.0f));
    }
}
