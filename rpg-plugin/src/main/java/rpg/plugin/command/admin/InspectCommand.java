package rpg.plugin.command.admin;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;

import rpg.core.message.MessageKey;
import rpg.core.message.Messages;
import rpg.core.scheduler.Scheduler;
import rpg.plugin.command.framework.Argument;
import rpg.plugin.command.framework.Arguments;
import rpg.plugin.command.framework.CommandContext;
import rpg.plugin.command.framework.RpgCommand;
import rpg.plugin.command.framework.TickReturn;

/**
 * {@code /rpg inspect sheet|statistics|inventory|session <player>} (US7).
 *
 * <p>The command is deliberately only an orchestration layer. A reader receives a player id and
 * returns a finished, read-only report. It has no audit dependency and no API capable of creating a
 * session or changing a character. This keeps the negative promise of US7 visible in the type shape
 * instead of relying on every future view implementation to remember it.
 */
public final class InspectCommand {

    public static final String SHEET_PERMISSION = "rpg.admin.inspect.sheet";
    public static final String STATISTICS_PERMISSION = "rpg.admin.inspect.statistics";
    public static final String INVENTORY_PERMISSION = "rpg.admin.inspect.inventory";
    public static final String SESSION_PERMISSION = "rpg.admin.inspect.session";

    public enum View {
        SHEET,
        STATISTICS,
        INVENTORY,
        SESSION
    }

    /** The only dependency the command needs from the data-owning blocks. */
    @FunctionalInterface
    public interface Reader {
        CompletableFuture<Report> read(View view, UUID playerId);
    }

    /** One already read value, labelled through the normal message catalogue. */
    public record Line(MessageKey label, String value) {
        public Line {
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(value, "value");
        }
    }

    /** A complete snapshot; reading it cannot cause a second lookup from the command. */
    public record Report(boolean anonymized, List<Line> lines) {
        public Report {
            lines = List.copyOf(Objects.requireNonNull(lines, "lines"));
        }

        public static Report empty(boolean anonymized) {
            return new Report(anonymized, List.of());
        }

        public Report withAnonymized(boolean value) {
            return value == anonymized ? this : new Report(value, lines);
        }
    }

    private final Server server;
    private final Reader reader;
    private final TickReturn tickReturn;
    private final Messages messages;

    public InspectCommand(
            Server server,
            Reader reader,
            Scheduler scheduler,
            Messages messages,
            Logger logger) {
        this(server, reader, new TickReturn(scheduler, server, logger), messages);
    }

    InspectCommand(Server server, Reader reader, TickReturn tickReturn, Messages messages) {
        this.server = Objects.requireNonNull(server, "server");
        this.reader = Objects.requireNonNull(reader, "reader");
        this.tickReturn = Objects.requireNonNull(tickReturn, "tickReturn");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    /** The four read-only leaves under the {@code inspect} branch. */
    public RpgCommand definition(Duration rateLimit) {
        Objects.requireNonNull(rateLimit, "rateLimit");
        return RpgCommand.branch(
                "inspect",
                InspectMessageKeys.DESCRIPTION,
                null,
                List.of(
                        leaf(
                                "sheet",
                                InspectMessageKeys.SHEET_DESCRIPTION,
                                SHEET_PERMISSION,
                                View.SHEET,
                                rateLimit),
                        leaf(
                                "statistics",
                                InspectMessageKeys.STATISTICS_DESCRIPTION,
                                STATISTICS_PERMISSION,
                                View.STATISTICS,
                                rateLimit),
                        leaf(
                                "inventory",
                                InspectMessageKeys.INVENTORY_DESCRIPTION,
                                INVENTORY_PERMISSION,
                                View.INVENTORY,
                                rateLimit),
                        leaf(
                                "session",
                                InspectMessageKeys.SESSION_DESCRIPTION,
                                SESSION_PERMISSION,
                                View.SESSION,
                                rateLimit)));
    }

    private RpgCommand leaf(
            String name,
            MessageKey description,
            String permission,
            View view,
            Duration rateLimit) {
        Argument<OfflinePlayer> player =
                Argument.required("player", Arguments.player(server));
        return RpgCommand.leaf(
                        name,
                        description,
                        permission,
                        List.of(player),
                        context -> inspect(context, player, view))
                .throttled(rateLimit);
    }

    private void inspect(
            CommandContext context, Argument<OfflinePlayer> playerArgument, View view) {
        OfflinePlayer target = context.get(playerArgument);
        CompletableFuture<Outcome> pending;
        try {
            pending =
                    reader.read(view, target.getUniqueId())
                            .handle(
                                    (report, failure) ->
                                            new Outcome(
                                                    report == null
                                                            ? Report.empty(false)
                                                            : report,
                                                    failure));
        } catch (RuntimeException failure) {
            pending = CompletableFuture.completedFuture(new Outcome(Report.empty(false), failure));
        }

        tickReturn.deliver(
                context.sender(), pending, outcome -> render(context.sender(), target, view, outcome));
    }

    private void render(
            CommandSender sender, OfflinePlayer target, View view, Outcome outcome) {
        if (outcome.failure() != null) {
            sender.sendMessage(messages.get(InspectMessageKeys.FAILED, Map.of()));
            return;
        }

        Report report = outcome.report();
        String displayName = displayName(target, report.anonymized());
        sender.sendMessage(messages.get(titleOf(view), Map.of("player", displayName)));
        if (report.lines().isEmpty()) {
            sender.sendMessage(messages.get(InspectMessageKeys.NO_DATA, Map.of("player", displayName)));
            return;
        }
        for (Line line : report.lines()) {
            sender.sendMessage(
                    messages.get(
                            InspectMessageKeys.LINE,
                            Map.of(
                                    "label", messages.get(line.label()),
                                    "value", line.value())));
        }
    }

    private String displayName(OfflinePlayer target, boolean anonymized) {
        if (anonymized) {
            return messages.get(InspectMessageKeys.ANONYMOUS, Map.of());
        }
        return Optional.ofNullable(target.getName()).orElse(target.getUniqueId().toString());
    }

    private static MessageKey titleOf(View view) {
        return switch (view) {
            case SHEET -> InspectMessageKeys.SHEET_TITLE;
            case STATISTICS -> InspectMessageKeys.STATISTICS_TITLE;
            case INVENTORY -> InspectMessageKeys.INVENTORY_TITLE;
            case SESSION -> InspectMessageKeys.SESSION_TITLE;
        };
    }

    private record Outcome(Report report, Throwable failure) {}
}
