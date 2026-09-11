package rpg.plugin.command.admin;

import java.lang.reflect.Constructor;
import java.time.Clock;
import java.util.Map;
import java.util.logging.Logger;

import org.bukkit.command.CommandSender;

import rpg.core.message.MapMessages;
import rpg.core.message.Messages;
import rpg.core.scheduler.EntityRef;
import rpg.core.scheduler.Scheduler;
import rpg.core.scheduler.TaskHandle;
import rpg.core.scheduler.WorldPosition;
import rpg.plugin.command.framework.CommandContext;
import rpg.plugin.command.framework.TickReturn;

/** Shared test-only wiring for the four read-only inspection leaves. */
final class InspectionTestSupport {

    static final Logger QUIET = Logger.getLogger("inspection-test");

    private InspectionTestSupport() {}

    static InspectCommand command(org.bukkit.Server server, InspectCommand.Reader reader) {
        return new InspectCommand(server, reader, new ImmediateScheduler(), messages(), QUIET);
    }

    static AuditCommand auditCommand(
            org.bukkit.Server server, Clock clock, AuditCommand.Reader reader) {
        return new AuditCommand(
                clock,
                reader,
                new TickReturn(new ImmediateScheduler(), server, QUIET),
                messages());
    }

    static CommandContext context(CommandSender sender, Map<String, Object> values) {
        try {
            Constructor<CommandContext> constructor =
                    CommandContext.class.getDeclaredConstructor(CommandSender.class, Map.class);
            constructor.setAccessible(true);
            return constructor.newInstance(sender, values);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("could not create command context", failure);
        }
    }

    static Messages messages() {
        return new MapMessages(
                Map.ofEntries(
                        Map.entry("command.inspect.description", "Inspect"),
                        Map.entry("command.inspect.sheet.description", "Inspect sheet"),
                        Map.entry("command.inspect.statistics.description", "Inspect statistics"),
                        Map.entry("command.inspect.inventory.description", "Inspect inventory"),
                        Map.entry("command.inspect.session.description", "Inspect session"),
                        Map.entry("command.inspect.sheet.title", "Character sheet for {player}"),
                        Map.entry("command.inspect.statistics.title", "Statistics for {player}"),
                        Map.entry("command.inspect.inventory.title", "Inventory for {player}"),
                        Map.entry("command.inspect.session.title", "Session for {player}"),
                        Map.entry("command.inspect.line", "{label}: {value}"),
                        Map.entry("command.inspect.anonymous", "anonymous player"),
                        Map.entry("command.inspect.no-data", "No data for {player}"),
                        Map.entry("command.inspect.failed", "Inspection failed"),
                        Map.entry("command.inspect.sheet.level", "Level"),
                        Map.entry("command.audit.description", "Audit"),
                        Map.entry("command.audit.page", "Audit page {page}/{pages} ({entries} entries)"),
                        Map.entry(
                                "command.audit.entry",
                                "{time} | actor={actor} | action={action} | target={target} | {details}"),
                        Map.entry("command.audit.empty", "No audit entries"),
                        Map.entry("command.audit.more", "{remaining} more entries"),
                        Map.entry("command.audit.failed", "Audit failed")));
    }

    private static final class ImmediateScheduler implements Scheduler {
        @Override
        public TaskHandle runSyncAtLocation(WorldPosition position, Runnable task) {
            task.run();
            return Handle.ACTIVE;
        }

        @Override
        public TaskHandle runSyncOnEntity(EntityRef entity, Runnable task) {
            task.run();
            return Handle.ACTIVE;
        }

        @Override
        public TaskHandle runSyncOnEntityDelayed(EntityRef entity, java.time.Duration delay, Runnable task) {
            task.run();
            return Handle.ACTIVE;
        }

        @Override
        public TaskHandle runAsync(Runnable task) {
            task.run();
            return Handle.ACTIVE;
        }

        @Override
        public TaskHandle runAsyncDelayed(java.time.Duration delay, Runnable task) {
            task.run();
            return Handle.ACTIVE;
        }
    }

    private enum Handle implements TaskHandle {
        ACTIVE;

        @Override
        public void cancel() {}

        @Override
        public boolean isCancelled() {
            return false;
        }
    }
}
