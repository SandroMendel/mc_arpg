package rpg.plugin.command.admin;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.bukkit.Server;
import org.bukkit.command.CommandSender;

import rpg.core.message.Messages;
import rpg.core.persistence.AuditEntry;
import rpg.core.persistence.AuditLogRepository;
import rpg.core.scheduler.Scheduler;
import rpg.plugin.command.framework.Argument;
import rpg.plugin.command.framework.Arguments;
import rpg.plugin.command.framework.CommandContext;
import rpg.plugin.command.framework.RpgCommand;
import rpg.plugin.command.framework.TickReturn;

/** {@code /rpg audit [duration]} — the paged, read-only audit-log view (US8). */
public final class AuditCommand {

    public static final String PERMISSION = "rpg.admin.audit";
    public static final Duration DEFAULT_RANGE = Duration.ofDays(1);
    public static final Duration MAX_RANGE = Duration.ofDays(7);
    public static final int PAGE_SIZE = 10;

    @FunctionalInterface
    interface Reader {
        CompletableFuture<List<AuditEntry>> between(Instant from, Instant to);
    }

    private final Clock clock;
    private final Reader reader;
    private final TickReturn tickReturn;
    private final Messages messages;

    public AuditCommand(
            AuditLogRepository repository,
            Clock clock,
            Server server,
            Scheduler scheduler,
            Messages messages,
            Logger logger) {
        this(
                clock,
                repository::between,
                new TickReturn(scheduler, server, logger),
                messages);
    }

    AuditCommand(Clock clock, Reader reader, TickReturn tickReturn, Messages messages) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.reader = Objects.requireNonNull(reader, "reader");
        this.tickReturn = Objects.requireNonNull(tickReturn, "tickReturn");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    /** The single audit leaf; its optional duration defaults to the last 24 hours. */
    public RpgCommand definition(Duration rateLimit) {
        Objects.requireNonNull(rateLimit, "rateLimit");
        Argument<Duration> duration = Argument.optional("duration", Arguments.duration(MAX_RANGE));
        return RpgCommand.leaf(
                        "audit",
                        AuditMessageKeys.DESCRIPTION,
                        PERMISSION,
                        List.of(duration),
                        context -> read(context, duration))
                .throttled(rateLimit);
    }

    private void read(CommandContext context, Argument<Duration> durationArgument) {
        Duration range = context.find(durationArgument).orElse(DEFAULT_RANGE);
        Instant to = clock.instant();
        Instant from = to.minus(range);
        CompletableFuture<Outcome> pending;
        try {
            pending =
                    reader.between(from, to)
                            .handle(
                                    (entries, failure) ->
                                            new Outcome(
                                                    entries == null ? List.of() : List.copyOf(entries),
                                                    failure));
        } catch (RuntimeException failure) {
            pending = CompletableFuture.completedFuture(new Outcome(List.of(), failure));
        }
        tickReturn.deliver(context.sender(), pending, outcome -> render(context.sender(), outcome));
    }

    private void render(CommandSender sender, Outcome outcome) {
        if (outcome.failure() != null) {
            sender.sendMessage(messages.get(AuditMessageKeys.FAILED));
            return;
        }
        List<AuditEntry> entries = outcome.entries();
        if (entries.isEmpty()) {
            sender.sendMessage(messages.get(AuditMessageKeys.EMPTY));
            return;
        }

        int pages = (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE;
        int shown = Math.min(PAGE_SIZE, entries.size());
        sender.sendMessage(
                messages.get(
                        AuditMessageKeys.PAGE,
                        Map.of(
                                "page", "1",
                                "pages", String.valueOf(pages),
                                "entries", String.valueOf(entries.size()))));
        entries.stream()
                .limit(PAGE_SIZE)
                .forEach(entry -> sender.sendMessage(messages.get(AuditMessageKeys.ENTRY, valuesOf(entry))));
        if (entries.size() > shown) {
            sender.sendMessage(
                    messages.get(
                            AuditMessageKeys.MORE,
                            Map.of("remaining", String.valueOf(entries.size() - shown))));
        }
    }

    private Map<String, String> valuesOf(AuditEntry entry) {
        return Map.of(
                "time", entry.occurredAt().toString(),
                "actor", entry.actor(),
                "action", entry.action(),
                "target",
                        entry.targetPlayerId().map(Object::toString).orElse("-"),
                "details",
                        entry.details().entrySet().stream()
                                .sorted(Comparator.comparing(Map.Entry::getKey))
                                .map(detail -> detail.getKey() + "=" + detail.getValue())
                                .collect(Collectors.joining(", ")));
    }

    private record Outcome(List<AuditEntry> entries, Throwable failure) {
        private Outcome {
            entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
        }
    }
}
