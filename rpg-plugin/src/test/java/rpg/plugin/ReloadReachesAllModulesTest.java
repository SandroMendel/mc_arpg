package rpg.plugin;

import static org.assertj.core.api.Assertions.assertThat;

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

/** T067 — the production reload path reaches every module hook, not only the historic two. */
class ReloadReachesAllModulesTest {

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
    @DisplayName("ein erfolgreiches Neuladen nennt alle sechs ausgeführten Modulhaken")
    void aSuccessfulReloadReachesAndNamesEveryModule() {
        assertThat(plugin.reloadConfigurationResult().applied()).isTrue();

        String reloadLog =
                records.stream()
                        .map(LogRecord::getMessage)
                        .filter(message -> message.contains("phase=RELOAD"))
                        .reduce("", (all, message) -> all + "\n" + message);

        assertThat(reloadLog)
                .contains("state=APPLIED")
                .contains("CombatModule")
                .contains("ItemModule")
                .contains("MobModule")
                .contains("StatisticsModule")
                .contains("UiModule")
                .contains("ZoneModule");
    }
}
