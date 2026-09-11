package rpg.plugin.command.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.lang.reflect.Constructor;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import rpg.core.classes.ClassSelection;
import rpg.core.event.DefaultEventBus;
import rpg.core.persistence.AuditEntry;
import rpg.core.persistence.AuditLogRepository;
import rpg.core.progression.ProgressView;
import rpg.core.progression.Progression;
import rpg.core.progression.XpResult;
import rpg.core.scheduler.EntityRef;
import rpg.core.scheduler.Scheduler;
import rpg.core.scheduler.TaskHandle;
import rpg.core.scheduler.WorldPosition;
import rpg.core.session.CharacterClass;
import rpg.core.session.CharacterRepository;
import rpg.core.session.PlayerCharacter;
import rpg.core.session.PlayerSession;
import rpg.core.session.SessionRegistry;
import rpg.core.session.SessionState;
import rpg.core.message.MapMessages;
import rpg.core.message.Messages;
import rpg.plugin.command.framework.AdminAudit;
import rpg.plugin.command.framework.Argument;
import rpg.plugin.command.framework.ArgumentRejected;
import rpg.plugin.command.framework.CommandContext;
import rpg.plugin.command.framework.RpgCommand;
import rpg.plugin.command.framework.TickReturn;

/** T083–T088 — the correction command uses the public progression and class-selection paths. */
class SetCommandTest {

    private static final Instant WHEN = Instant.parse("2026-09-11T12:00:00Z");
    private static final Logger QUIET = quietLogger();

    private ServerMock server;
    private WorldMock world;
    private PlayerMock player;
    private RecordingAuditLog auditLog;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        player = server.addPlayer("Ticoo");
        player.teleport(new Location(world, 8.5, 65, 8.5));
        auditLog = new RecordingAuditLog();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("/rpg set level preserves experience and passes the character id to B06")
    void levelUsesTheCharacterIdAndKeepsExperience() {
        UUID characterId = UUID.randomUUID();
        ProgressView[] current = {new ProgressView(20, 37L, 500L, false)};
        SetProgressCall call = new SetProgressCall();
        Progression progression = progression(current, call);
        PlayerSession session = readySession(player.getUniqueId(), characterId);

        SetCommand command = command(progression, session, newClassSelection());
        RpgCommand level = child(command.definition(), "level");
        Argument<?> levelValue = level.arguments().get(1);

        level.action().accept(context(player, Map.of("player", player, levelValue.name(), 35)));

        assertThat(call.actorId).isEqualTo(AdminAudit.actorOf(player));
        assertThat(call.characterId).isEqualTo(characterId);
        assertThat(call.level).isEqualTo(35);
        assertThat(call.xpInLevel).isEqualTo(37L);
        assertThat(current[0].level()).isEqualTo(35);
        assertThat(player.nextMessage()).contains("35");
    }

    @Test
    @DisplayName("/rpg set xp preserves the level and uses the public setter")
    void xpUsesTheCharacterIdAndKeepsLevel() {
        UUID characterId = UUID.randomUUID();
        ProgressView[] current = {new ProgressView(20, 37L, 500L, false)};
        SetProgressCall call = new SetProgressCall();
        Progression progression = progression(current, call);
        PlayerSession session = readySession(player.getUniqueId(), characterId);

        SetCommand command = command(progression, session, newClassSelection());
        RpgCommand xp = child(command.definition(), "xp");
        Argument<?> xpValue = xp.arguments().get(1);

        xp.action().accept(context(player, Map.of("player", player, xpValue.name(), 91L)));

        assertThat(call.characterId).isEqualTo(characterId);
        assertThat(call.level).isEqualTo(20);
        assertThat(call.xpInLevel).isEqualTo(91L);
        assertThat(current[0].level()).isEqualTo(20);
        assertThat(current[0].xpInLevel()).isEqualTo(91L);
    }

    @Test
    @DisplayName("a level outside the configured range is rejected by the argument declaration")
    void outOfRangeLevelIsRejectedBeforeExecution() throws Exception {
        Progression progression =
                progression(new ProgressView[] {new ProgressView(20, 37L, 500L, false)}, new SetProgressCall());
        PlayerSession session = readySession(player.getUniqueId(), UUID.randomUUID());
        RpgCommand level = child(command(progression, session, newClassSelection()).definition(), "level");
        Argument<?> levelValue = level.arguments().get(1);

        try {
            levelValue.type().parse("999");
            fail("an out-of-range level must be rejected");
        } catch (ArgumentRejected rejected) {
            assertThat(rejected.key().value()).isEqualTo("command.error.argument-out-of-range");
            assertThat(rejected.placeholders()).containsEntry("argument", "level");
        }
    }

    @Test
    @DisplayName("class correction uses ClassSelection asynchronously and audits the result")
    void classUsesThePublicSelectionPathAndIsAudited() {
        PlayerSession session = readySessionWithoutCharacter(player.getUniqueId());
        RecordingCharacterRepository characters = new RecordingCharacterRepository();
        ClassSelection selection = new ClassSelection(characters, new DefaultEventBus(QUIET), QUIET);
        SetCommand command =
                command(
                        progression(
                                new ProgressView[] {new ProgressView(1, 0L, 100L, false)},
                                new SetProgressCall()),
                        session,
                        selection);
        RpgCommand classCommand = child(command.definition(), "class");
        Argument<?> classValue = classCommand.arguments().get(1);

        classCommand.action()
                .accept(context(player, Map.of("player", player, classValue.name(), CharacterClass.ROGUE)));

        assertThat(characters.created).containsExactly(CharacterClass.ROGUE);
        assertThat(auditLog.entries)
                .singleElement()
                .satisfies(
                        entry -> {
                            assertThat(entry.action()).isEqualTo("class_changed");
                            assertThat(entry.targetPlayerId()).contains(player.getUniqueId());
                            assertThat(entry.details()).containsEntry("toClass", "rogue");
                        });
        assertThat(player.nextMessage()).contains("rogue");
    }

    private SetCommand command(
            Progression progression, PlayerSession session, ClassSelection selection) {
        SessionRegistry sessions = sessionRegistry(session);
        Scheduler scheduler = new ImmediateScheduler();
        TickReturn tickReturn = new TickReturn(scheduler, server, QUIET);
        return new SetCommand(
                server,
                sessions,
                progression,
                selection,
                tickReturn,
                new AdminAudit(auditLog, Clock.fixed(WHEN, ZoneOffset.UTC)),
                messages());
    }

    private static RpgCommand child(RpgCommand root, String name) {
        return root.children().stream()
                .filter(command -> command.name().equals(name))
                .findFirst()
                .orElseThrow();
    }

    private static Progression progression(ProgressView[] current, SetProgressCall call) {
        return (Progression)
                Proxy.newProxyInstance(
                        Progression.class.getClassLoader(),
                        new Class<?>[] {Progression.class},
                        (proxy, method, args) -> {
                            switch (method.getName()) {
                                case "maxLevel":
                                    return 60;
                                case "progressOf":
                                    return Optional.of(current[0]);
                                case "setProgress":
                                    call.actorId = (UUID) args[0];
                                    call.characterId = (UUID) args[1];
                                    call.level = (Integer) args[2];
                                    call.xpInLevel = (Long) args[3];
                                    current[0] = new ProgressView(call.level, call.xpInLevel, 500L, false);
                                    return XpResult.granted(0L);
                                default:
                                    return defaultValue(method.getReturnType());
                            }
                        });
    }

    private static SessionRegistry sessionRegistry(PlayerSession session) {
        return (SessionRegistry)
                Proxy.newProxyInstance(
                        SessionRegistry.class.getClassLoader(),
                        new Class<?>[] {SessionRegistry.class},
                        (proxy, method, args) -> {
                            switch (method.getName()) {
                                case "find":
                                    return Optional.of(session);
                                case "require":
                                    return session;
                                case "isReady":
                                    return true;
                                case "activeSessionCount":
                                    return 1;
                                default:
                                    return defaultValue(method.getReturnType());
                            }
                        });
    }

    private static PlayerSession readySession(UUID playerId, UUID characterId) {
        PlayerCharacter character =
                new PlayerCharacter(
                        characterId,
                        playerId,
                        CharacterClass.WARRIOR,
                        PlayerCharacter.CURRENT_DATA_VERSION,
                        0L,
                        WHEN,
                        WHEN);
        PlayerSession session = new PlayerSession(playerId, character, List.of(character));
        session.transitionTo(SessionState.READY, WHEN);
        return session;
    }

    private static PlayerSession readySessionWithoutCharacter(UUID playerId) {
        PlayerSession session = new PlayerSession(playerId, null, List.of());
        session.transitionTo(SessionState.READY, WHEN);
        return session;
    }

    private static ClassSelection newClassSelection() {
        return new ClassSelection(new RecordingCharacterRepository(), new DefaultEventBus(QUIET), QUIET);
    }

    private static Messages messages() {
        return new MapMessages(
                Map.ofEntries(
                        Map.entry("command.set.description", "Set character data"),
                        Map.entry("command.set.level.description", "Set a level"),
                        Map.entry("command.set.xp.description", "Set experience"),
                        Map.entry("command.set.class.description", "Set a class"),
                        Map.entry("command.set.done", "{player}: {property} changed from {from} to {to}."),
                        Map.entry("command.set.notice", "An operator changed your {property} to {to}."),
                        Map.entry("command.set.target-unavailable", "The target is not in a ready session."),
                        Map.entry("command.set.rejected", "The value was rejected."),
                        Map.entry("command.set.class.done", "{player}: class set to {class}."),
                        Map.entry("command.set.class.notice", "An operator selected {class} for you."),
                        Map.entry("command.set.class.failed", "The class could not be set."),
                        Map.entry("class.selection.error.class-taken", "That class is already taken."),
                        Map.entry("class.selection.error.unknown-class", "That class is unknown."),
                        Map.entry(
                                "class.selection.error.already-has-character",
                                "The player already has a character.")));
    }

    private static CommandContext context(CommandSender sender, Map<String, Object> values) {
        try {
            Constructor<CommandContext> constructor =
                    CommandContext.class.getDeclaredConstructor(CommandSender.class, Map.class);
            constructor.setAccessible(true);
            return constructor.newInstance(sender, values);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("test context could not be built", failure);
        }
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == double.class) return 0.0;
        if (type == float.class) return 0.0F;
        if (type == short.class) return (short) 0;
        if (type == byte.class) return (byte) 0;
        if (type == Optional.class) return Optional.empty();
        if (type == CompletableFuture.class) return CompletableFuture.completedFuture(null);
        return null;
    }

    private static Logger quietLogger() {
        Logger logger = Logger.getLogger(SetCommandTest.class.getName());
        logger.setLevel(Level.OFF);
        return logger;
    }

    private static final class SetProgressCall {
        UUID actorId;
        UUID characterId;
        int level;
        long xpInLevel;
    }

    private static final class RecordingCharacterRepository implements CharacterRepository {
        final List<CharacterClass> created = new ArrayList<>();

        @Override
        public CompletableFuture<List<PlayerCharacter>> findByPlayer(UUID playerId) {
            return CompletableFuture.completedFuture(List.of());
        }

        @Override
        public CompletableFuture<Optional<PlayerCharacter>> find(UUID characterId) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        @Override
        public CompletableFuture<PlayerCharacter> create(UUID playerId, CharacterClass id) {
            created.add(id);
            return CompletableFuture.completedFuture(PlayerCharacter.create(playerId, id, WHEN));
        }

        @Override
        public void markDirty(UUID characterId) {}
    }

    private static final class RecordingAuditLog implements AuditLogRepository {
        final List<AuditEntry> entries = new ArrayList<>();

        @Override
        public void append(AuditEntry entry) {
            entries.add(entry);
        }

        @Override
        public CompletableFuture<List<AuditEntry>> between(Instant from, Instant to) {
            return CompletableFuture.completedFuture(List.copyOf(entries));
        }
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
        public TaskHandle runSyncOnEntityDelayed(
                EntityRef entity, java.time.Duration delay, Runnable task) {
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
