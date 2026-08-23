package rpg.plugin.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import rpg.core.message.Messages;
import rpg.core.progression.ProgressView;
import rpg.core.progression.Progression;
import rpg.core.progression.ProgressionMessageKeys;
import rpg.core.progression.XpCurve;
import rpg.core.progression.XpResult;
import rpg.core.progression.XpSource;
import rpg.core.session.PlayerCharacter;
import rpg.core.session.PlayerSession;
import rpg.core.session.SessionRegistry;

/**
 * {@code /xp} - give experience, take it away, or set a level outright.
 *
 * <p><b>Provisional, like {@code /coins} before it</b> (ADR-028). Commands, the permission tree and
 * tab completion belong to B14; this exists because a correction that can only be made with a
 * database editor is not a correction anybody will make.
 *
 * <p><b>Giving and taking go different ways, and that is not an inconsistency.</b> Giving is an
 * ordinary grant with {@code XpSource.ADMIN} - the same path a kill takes. Taking cannot be: B06
 * refuses a negative amount outright (FR-015), so that no player can quietly lose what they earned.
 * The way down is {@code setProgress}, which exists for exactly this and writes <b>who did it</b>
 * into B02's audit log with the old and the new state (FR-024b).
 *
 * <p><b>The character is told.</b> Somebody who quietly loses two levels files a bug report, and the
 * report costs more than the line does.
 */
public final class XpCommand implements CommandExecutor, TabCompleter {

    public static final String PERMISSION = "rpg.progression.admin";

    /**
     * Who the audit log names when the console did it.
     *
     * <p>A fixed nil UUID rather than refusing the console: the console <em>is</em> an actor - whoever
     * has access to it - and an entry that says "console" is more use than a command an operator
     * cannot run from where operators actually work. It is a constant so the log is greppable.
     */
    static final UUID CONSOLE = new UUID(0L, 0L);

    private final Server server;
    private final SessionRegistry sessions;
    private final Progression progression;
    private final XpCurve curve;
    private final Messages messages;

    public XpCommand(
            Server server,
            SessionRegistry sessions,
            Progression progression,
            XpCurve curve,
            Messages messages) {
        this.server = Objects.requireNonNull(server, "server");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.progression = Objects.requireNonNull(progression, "progression");
        this.curve = Objects.requireNonNull(curve, "curve");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(messages.get(ProgressionMessageKeys.ADMIN_DENIED));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(messages.get(ProgressionMessageKeys.ADMIN_USAGE));
            return true;
        }

        Optional<Target> target = resolve(args[1]);
        if (target.isEmpty()) {
            sender.sendMessage(messages.get(ProgressionMessageKeys.ADMIN_UNKNOWN_TARGET));
            return true;
        }

        long amount;
        try {
            amount = Long.parseLong(args[2]);
        } catch (NumberFormatException notANumber) {
            sender.sendMessage(messages.get(ProgressionMessageKeys.ADMIN_INVALID_AMOUNT));
            return true;
        }
        if (amount <= 0L && !"set".equals(args[0].toLowerCase(Locale.ROOT))) {
            // A negative "give" would be a "take" written the wrong way round, and the two are
            // audited differently. Refused rather than guessed at.
            sender.sendMessage(messages.get(ProgressionMessageKeys.ADMIN_INVALID_AMOUNT));
            return true;
        }

        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "give" -> give(sender, target.get(), amount);
            case "take" -> take(sender, target.get(), amount);
            case "set" -> set(sender, target.get(), (int) amount, args);
            default -> {
                sender.sendMessage(messages.get(ProgressionMessageKeys.ADMIN_USAGE));
                yield true;
            }
        };
    }

    private boolean give(CommandSender sender, Target target, long amount) {
        XpResult result = progression.grant(target.characterId(), amount, XpSource.ADMIN);
        if (result.rejected()) {
            sender.sendMessage(messages.get(ProgressionMessageKeys.ADMIN_INVALID_AMOUNT));
            return true;
        }
        report(sender, target, "+" + amount);
        return true;
    }

    /**
     * Takes experience away, walking down through levels if it has to.
     *
     * <p>The walk is here rather than in B06 on purpose: it is arithmetic over the curve, which is
     * public, and B06's contract deliberately has no "subtract" - adding one would be a second way to
     * lower progress next to {@code setProgress}, and only one of them would be audited.
     */
    private boolean take(CommandSender sender, Target target, long amount) {
        Optional<ProgressView> current = progression.progressOf(target.characterId());
        if (current.isEmpty()) {
            sender.sendMessage(messages.get(ProgressionMessageKeys.ADMIN_UNKNOWN_TARGET));
            return true;
        }
        int level = current.get().level();
        long inLevel = current.get().xpInLevel();
        long remaining = amount;

        while (remaining > inLevel && level > 1) {
            remaining -= inLevel;
            level--;
            // Dropping into a level puts them at its top: the size of level L is what it costs to
            // leave it, which is the threshold of L+1.
            inLevel = curve.thresholdFor(level + 1);
        }
        long left = Math.max(0L, inLevel - remaining);

        XpResult result =
                progression.setProgress(actorOf(sender), target.characterId(), level, left);
        if (result.rejected()) {
            sender.sendMessage(messages.get(ProgressionMessageKeys.ADMIN_INVALID_AMOUNT));
            return true;
        }
        report(sender, target, "-" + amount);
        return true;
    }

    /** {@code /xp set <player> <level> [xp-in-level]} - the blunt correction. */
    private boolean set(CommandSender sender, Target target, int level, String[] args) {
        long inLevel = 0L;
        if (args.length >= 4) {
            try {
                inLevel = Long.parseLong(args[3]);
            } catch (NumberFormatException notANumber) {
                sender.sendMessage(messages.get(ProgressionMessageKeys.ADMIN_INVALID_AMOUNT));
                return true;
            }
        }
        XpResult result =
                progression.setProgress(actorOf(sender), target.characterId(), level, inLevel);
        if (result.rejected()) {
            // Out of range, unknown character, not ready - B06 decided, and it says which.
            sender.sendMessage(messages.get(ProgressionMessageKeys.ADMIN_INVALID_AMOUNT));
            return true;
        }
        report(sender, target, "level " + level);
        return true;
    }

    /** Tells the operator what the character now stands at, and tells the character it happened. */
    private void report(CommandSender sender, Target target, String change) {
        ProgressView now =
                progression.progressOf(target.characterId()).orElse(new ProgressView(1, 0L, 0L, false));
        Map<String, String> values =
                Map.of(
                        "player", target.player().getName(),
                        "change", change,
                        "level", String.valueOf(now.level()),
                        "xp", String.valueOf(now.xpInLevel()));
        sender.sendMessage(messages.get(ProgressionMessageKeys.ADMIN_XP_CHANGED, values));
        target.player().sendMessage(messages.get(ProgressionMessageKeys.ADMIN_XP_NOTICE, values));
    }

    /**
     * The player and the character they are currently playing.
     *
     * <p>Only an online player, like {@code /coins}: resolving an offline character needs a lookup
     * this provisional command has no business owning.
     */
    private Optional<Target> resolve(String playerName) {
        Player player = server.getPlayerExact(playerName);
        if (player == null) {
            return Optional.empty();
        }
        return sessions
                .find(player.getUniqueId())
                .flatMap(PlayerSession::activeCharacter)
                .map(PlayerCharacter::characterId)
                .map(characterId -> new Target(player, characterId));
    }

    private static UUID actorOf(CommandSender sender) {
        return sender instanceof Player player ? player.getUniqueId() : CONSOLE;
    }

    private record Target(Player player, UUID characterId) {}

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            return List.of();
        }
        if (args.length == 1) {
            return List.of("give", "take", "set");
        }
        if (args.length == 2) {
            List<String> names = new ArrayList<>();
            for (Player online : server.getOnlinePlayers()) {
                names.add(online.getName());
            }
            return names;
        }
        return List.of();
    }
}
