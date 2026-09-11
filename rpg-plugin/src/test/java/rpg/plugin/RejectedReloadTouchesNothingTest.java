package rpg.plugin;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import rpg.core.config.ConfigHandle;
import rpg.core.config.ConfigSchema;
import rpg.core.config.FieldType;
import rpg.plugin.command.admin.ReloadResult;

/** T068 — a rejected source does not publish a new handle or run any module hook. */
class RejectedReloadTouchesNothingTest {

    private ServerMock server;
    private RpgPlugin plugin;
    private List<LogRecord> records;
    private Handler handler;

    @BeforeEach
    void setUp() throws Exception {
        rpg.persistence.support.PostgresContainer.resetSchema();
        server = MockBukkit.mock();
        TestServerSetup.useTestDatabase();
        server.addSimpleWorld("world");
        plugin = MockBukkit.load(RpgPlugin.class);

        records = new ArrayList<>();
        handler =
                new Handler() {
                    @Override
                    public void publish(LogRecord record) {
                        records.add(record);
                    }

                    @Override
                    public void flush() {}

                    @Override
                    public void close() {}
                };
        plugin.getLogger().addHandler(handler);
    }

    @AfterEach
    void tearDown() {
        if (plugin != null && handler != null) {
            plugin.getLogger().removeHandler(handler);
        }
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("eine abgelehnte Quelle bleibt aktiv und startet keinen Nachladehaken")
    void aRejectedSourceKeepsThePreviousConfigurationAndRunsNoHook() throws Exception {
        Path file = plugin.getDataFolder().toPath().resolve("reload-test.yml");
        Files.writeString(file, "example:\n  max-targets: 5\n");
        ConfigHandle<Integer> handle =
                plugin.configLoader()
                        .register(
                                Path.of("reload-test.yml"),
                                ConfigSchema.<Integer>builder(1)
                                        .required("example.max-targets", FieldType.INTEGER)
                                        .boundTo(view -> view.getInt("example.max-targets"))
                                        .build());

        Files.writeString(file, "example:\n  max-targets: 'broken'\n");

        ReloadResult result = plugin.reloadConfigurationResult();

        assertThat(result.applied()).isFalse();
        assertThat(result.rejection()).isNotNull();
        assertThat(result.rejection().sourceFile()).isEqualTo(Path.of("reload-test.yml"));
        assertThat(result.rejection().documentPath()).isEqualTo("example.max-targets");
        assertThat(result.rejection().expected()).contains("integer");
        assertThat(handle.get()).isEqualTo(5);
        assertThat(plugin.bootstrapState().acceptsPlayers()).isTrue();
        assertThat(records.stream().map(LogRecord::getMessage))
                .noneMatch(message -> message.contains("state=APPLIED"));
    }
}
