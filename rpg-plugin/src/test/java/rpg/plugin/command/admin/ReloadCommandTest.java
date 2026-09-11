package rpg.plugin.command.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import rpg.core.config.ConfigValidationException;
import rpg.core.message.MapMessages;
import rpg.core.message.Messages;
import rpg.core.persistence.AuditEntry;
import rpg.core.persistence.AuditLogRepository;
import rpg.plugin.command.framework.AdminAudit;
import rpg.plugin.command.framework.CommandContext;
import rpg.plugin.command.framework.RpgCommand;

/** T063/T064/T066 — the reload leaf reports its outcome and audits successful changes. */
class ReloadCommandTest {

    private ServerMock server;
    private RecordingAuditLog auditLog;
    private ReloadCommand command;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        auditLog = new RecordingAuditLog();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("/rpg reload ist geschützt, meldet Erfolg und schreibt config_reloaded")
    void successIsReportedAndAudited() {
        command = new ReloadCommand(ReloadResult::success, audit(), messages());
        RpgCommand definition = command.definition();

        assertThat(definition.name()).isEqualTo("reload");
        assertThat(definition.permissionOrNone()).contains(ReloadCommand.PERMISSION);
        definition.action().accept(context(server.getConsoleSender(), Map.of()));

        assertThat(server.getConsoleSender().nextMessage()).contains("reloaded successfully");
        assertThat(auditLog.entries).singleElement().extracting(AuditEntry::action)
                .isEqualTo("config_reloaded");
    }

    @Test
    @DisplayName("eine Ablehnung nennt Datei, Dokumentpfad und Grund und schreibt kein Audit")
    void rejectionIsExplainedWithoutAnAuditEntry() {
        ConfigValidationException failure =
                new ConfigValidationException(
                        java.nio.file.Path.of("zones.yml"),
                        "zones.greenfields.world",
                        "a known world",
                        "'nowhere'");
        command = new ReloadCommand(() -> ReloadResult.rejected(failure), audit(), messages());

        command.definition().action().accept(context(server.getConsoleSender(), Map.of()));

        assertThat(server.getConsoleSender().nextMessage())
                .contains("zones.yml")
                .contains("zones.greenfields.world")
                .contains("a known world")
                .contains("'nowhere'");
        assertThat(auditLog.entries).isEmpty();
    }

    private AdminAudit audit() {
        return new AdminAudit(
                auditLog, Clock.fixed(Instant.parse("2026-09-11T12:00:00Z"), ZoneOffset.UTC));
    }

    private static Messages messages() {
        return new MapMessages(
                Map.of(
                        "command.reload.description", "Reload configuration",
                        "command.reload.done", "Configuration reloaded successfully.",
                        "command.reload.rejected",
                                "Configuration rejected in {file} at {path}: expected {expected}, but was {actual}."));
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

    private static final class RecordingAuditLog implements AuditLogRepository {

        private final List<AuditEntry> entries = new ArrayList<>();

        @Override
        public void append(AuditEntry entry) {
            entries.add(entry);
        }

        @Override
        public CompletableFuture<List<AuditEntry>> between(
                Instant from, Instant to) {
            return CompletableFuture.completedFuture(List.copyOf(entries));
        }
    }
}
