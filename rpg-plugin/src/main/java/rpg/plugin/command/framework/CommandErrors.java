package rpg.plugin.command.framework;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

import org.bukkit.command.CommandSender;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import rpg.core.message.MessageKey;
import rpg.core.message.Messages;
import rpg.plugin.command.CommandMessageKeys;

/**
 * Macht aus einer Ablehnung einen Satz (T023, FR-004, FR-009).
 *
 * <p>Die Trennung ist Absicht: ein {@link ArgumentType} weiß, <em>was</em> falsch ist, und wirft
 * {@link ArgumentRejected} mit Schlüssel und Platzhaltern. Erst hier kommen die {@code Messages}
 * dazu. Ein Typ, der selbst eine Zeichenkette baute, hätte Prinzip V an der Stelle gebrochen, an
 * der es am wenigsten auffällt — in einer Meldung, die nur im Fehlerfall erscheint.
 *
 * <p><b>Was hier nicht passiert: die Nutzungszeile wiederholen.</b> Wer sie schon vor sich hatte
 * und trotzdem falsch getippt hat, liest sie kein zweites Mal richtig. Die Meldung nennt das
 * betroffene Argument und was dort hingehört.
 */
public final class CommandErrors implements CommandTree.Rejections {

    /** {@code &} und nicht {@code §} — dieselbe Wahl wie in {@code ItemText} und B13s UI. */
    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.legacyAmpersand();

    private final Messages messages;

    public CommandErrors(Messages messages) {
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    @Override
    public void tell(CommandSender sender, ArgumentRejected rejected) {
        send(sender, rejected.key(), rejected.placeholders());
    }

    /** Ein Pflichtargument fehlt (FR-004). */
    public void missingArgument(CommandSender sender, Argument<?> argument) {
        send(
                sender,
                CommandMessageKeys.ARGUMENT_MISSING,
                Map.of("argument", argument.name(), "expected", argument.type().expected()));
    }

    /**
     * Von der Konsole abgesetzt, aber das Blatt braucht einen Spieler (FR-008).
     *
     * <p>Eine Meldung und <b>keine Ausnahme</b>. Eine Ausnahme im Log sieht aus wie ein Fehler des
     * Servers, obwohl der Betreiber nur etwas getippt hat, was von dort nicht geht.
     */
    public void needsPlayer(CommandSender sender) {
        send(sender, CommandMessageKeys.NEEDS_PLAYER, Map.of());
    }

    /**
     * Das Recht fehlt (FR-013).
     *
     * <p>Selten zu sehen: ohne Recht erscheint der Zweig gar nicht erst in der Vervollständigung.
     * Wer ihn volltippt, bekommt trotzdem eine Antwort — Schweigen liest sich wie ein kaputtes
     * Kommando.
     */
    public void denied(CommandSender sender) {
        send(sender, CommandMessageKeys.DENIED, Map.of());
    }

    /** Zu schnell hintereinander — <b>mit Restzeit</b> (FR-032). */
    public void rateLimited(CommandSender sender, Duration remaining) {
        long seconds = Math.max(1L, (remaining.toMillis() + 999L) / 1000L);
        send(sender, CommandMessageKeys.RATE_LIMITED, Map.of("seconds", String.valueOf(seconds)));
    }

    private void send(CommandSender sender, MessageKey key, Map<String, String> placeholders) {
        if (!messages.contains(key)) {
            // Kann nach der Startpruefung nicht vorkommen - CommandMessageKeys.all() steht in der
            // Liste, gegen die loadMessages prueft. Schweigen waere hier trotzdem das Falsche:
            // eine fehlende Meldung darf nicht wie ein wirkungsloses Kommando aussehen.
            sender.sendMessage(Component.text(key.value()));
            return;
        }
        sender.sendMessage(LEGACY.deserialize(messages.get(key, placeholders)));
    }
}
