package rpg.plugin.command.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.lang.reflect.Constructor;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import rpg.core.message.MapMessages;
import rpg.core.message.Messages;
import rpg.core.scheduler.EntityRef;
import rpg.core.scheduler.Scheduler;
import rpg.core.scheduler.TaskHandle;
import rpg.core.scheduler.WorldPosition;
import rpg.plugin.command.framework.Argument;
import rpg.plugin.command.framework.ArgumentRejected;
import rpg.plugin.command.framework.CommandContext;
import rpg.plugin.command.framework.RpgCommand;

/** T091–T096 — foreign inspection is a read-only, anonymisation-safe command tree. */
class InspectCommandTest {

    private static final Logger QUIET = Logger.getLogger("inspect-test");

    private ServerMock server;
    private WorldMock world;
    private PlayerMock moderator;
    private PlayerMock target;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        moderator = server.addPlayer("Moderator");
        target = server.addPlayer("Target");
        moderator.teleport(new Location(world, 0.5, 65, 0.5));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("each inspection view has its own permission, player argument and read rate")
    void eachViewIsIndependentlyDeclared() {
        InspectCommand command = command((view, playerId) -> CompletableFuture.completedFuture(InspectCommand.Report.empty(false)));

        assertThat(command.definition(Duration.ofSeconds(3)).children())
                .extracting(RpgCommand::name)
                .containsExactly("sheet", "statistics", "inventory", "session");
        assertThat(command.definition(Duration.ofSeconds(3)).children())
                .extracting(RpgCommand::permission)
                .containsExactly(
                        InspectCommand.SHEET_PERMISSION,
                        InspectCommand.STATISTICS_PERMISSION,
                        InspectCommand.INVENTORY_PERMISSION,
                        InspectCommand.SESSION_PERMISSION);
        assertThat(command.definition(Duration.ofSeconds(3)).children())
                .allSatisfy(
                        leaf -> {
                            assertThat(leaf.arguments()).singleElement().extracting(Argument::name).isEqualTo("player");
                            assertThat(leaf.rateLimit()).isEqualTo(Duration.ofSeconds(3));
                        });
    }

    @Test
    @DisplayName("the PLAYER argument rejects a typo instead of creating an empty profile")
    void typoIsRejectedByTheSharedPlayerArgument() throws Exception {
        RpgCommand sheet = command((view, playerId) -> CompletableFuture.completedFuture(InspectCommand.Report.empty(false)))
                .definition(Duration.ZERO)
                .children()
                .get(0);
        Argument<?> player = sheet.arguments().get(0);

        try {
            player.type().parse("TypoThatNeverPlayed");
            fail("an unknown player must be rejected");
        } catch (ArgumentRejected rejected) {
            assertThat(rejected.placeholders()).containsEntry("name", "TypoThatNeverPlayed");
        }
    }

    @Test
    @DisplayName("an offline-capable target is passed to the reader and rendered")
    void targetIsReadWithoutChangingIt() {
        AtomicReference<UUID> requested = new AtomicReference<>();
        AtomicInteger state = new AtomicInteger(17);
        InspectCommand command =
                command(
                        (view, playerId) -> {
                            requested.set(playerId);
                            return CompletableFuture.completedFuture(
                                    new InspectCommand.Report(
                                            false,
                                            java.util.List.of(
                                                    new InspectCommand.Line(
                                                            InspectMessageKeys.SHEET_LEVEL,
                                                            String.valueOf(state.get())))));
                        });
        RpgCommand sheet = command.definition(Duration.ZERO).children().get(0);
        Argument<?> player = sheet.arguments().get(0);

        sheet.action().accept(context(moderator, Map.of(player.name(), (OfflinePlayer) target)));

        assertThat(requested).hasValue(target.getUniqueId());
        assertThat(state).hasValue(17);
        assertThat(moderator.nextMessage()).contains("Character sheet for Target");
        assertThat(moderator.nextMessage()).contains("Level: 17");
    }

    @Test
    @DisplayName("an anonymised player never gets its clear name put back into the report")
    void anonymisedTargetStaysAnonymous() {
        InspectCommand command =
                command(
                        (view, playerId) ->
                                CompletableFuture.completedFuture(
                                        new InspectCommand.Report(
                                                true,
                                                java.util.List.of(
                                                        new InspectCommand.Line(
                                                                InspectMessageKeys.SHEET_LEVEL,
                                                                "42")))));
        RpgCommand sheet = command.definition(Duration.ZERO).children().get(0);
        Argument<?> player = sheet.arguments().get(0);

        sheet.action().accept(context(moderator, Map.of(player.name(), (OfflinePlayer) target)));

        assertThat(moderator.nextMessage()).contains("anonymous player").doesNotContain("Target");
        assertThat(moderator.nextMessage()).contains("Level: 42");
    }

    private InspectCommand command(InspectCommand.Reader reader) {
        return new InspectCommand(server, reader, new ImmediateScheduler(), messages(), QUIET);
    }

    private static CommandContext context(CommandSender sender, Map<String, Object> values) {
        try {
            Constructor<CommandContext> constructor =
                    CommandContext.class.getDeclaredConstructor(CommandSender.class, Map.class);
            constructor.setAccessible(true);
            return constructor.newInstance(sender, values);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("could not create command context", failure);
        }
    }

    private static Messages messages() {
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
                        Map.entry("command.inspect.sheet.level", "Level")));
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
        public TaskHandle runSyncOnEntityDelayed(EntityRef entity, Duration delay, Runnable task) {
            task.run();
            return Handle.ACTIVE;
        }

        @Override
        public TaskHandle runAsync(Runnable task) {
            task.run();
            return Handle.ACTIVE;
        }

        @Override
        public TaskHandle runAsyncDelayed(Duration delay, Runnable task) {
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
