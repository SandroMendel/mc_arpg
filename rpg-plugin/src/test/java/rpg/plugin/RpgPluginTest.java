package rpg.plugin;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import rpg.core.config.ConfigHandle;
import rpg.core.config.ConfigLoader;
import rpg.core.config.ConfigSchema;
import rpg.core.config.FieldType;
import rpg.core.module.BootstrapState;

/**
 * T039 / quickstart sections 2 and 3 against the real plugin class under MockBukkit.
 *
 * <p>Confirms that the wiring in {@link RpgPlugin} actually reaches the behaviour the core tests
 * prove in isolation: the server comes up ready for players, and the reload entry point applies a
 * good configuration while keeping the previous one on a bad one.
 */
class RpgPluginTest {

    private ServerMock server;
    private RpgPlugin plugin;

    @BeforeEach
    void setUp() throws Exception {
        rpg.persistence.support.PostgresContainer.resetSchema();
        server = MockBukkit.mock();
        TestServerSetup.useTestDatabase();
        plugin = MockBukkit.load(RpgPlugin.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void theServerIsReadyForPlayersAfterEnable() {
        assertThat(plugin.bootstrapState().phase()).isEqualTo(BootstrapState.Phase.READY);
        assertThat(plugin.bootstrapState().acceptsPlayers()).isTrue();
    }

    @Test
    void theFourCapabilitiesAreWiredUp() {
        assertThat(plugin.registry()).isNotNull();
        assertThat(plugin.eventBus()).isNotNull();
        assertThat(plugin.scheduler()).isNotNull();
    }

    @Test
    void reloadingWithNoRegisteredConfigurationSucceeds() {
        assertThat(plugin.reloadConfiguration()).isTrue();
    }

    @Test
    void reloadAppliesAGoodFileAndKeepsThePreviousOneOnABadFile() throws Exception {
        Path dataFolder = plugin.getDataFolder().toPath();
        Files.createDirectories(dataFolder);
        Path file = dataFolder.resolve("combat.yml");
        Files.writeString(file, "combat:\n  max-targets: 5\n");

        ConfigSchema<Integer> schema =
                ConfigSchema.<Integer>builder(1)
                        .required("combat.max-targets", FieldType.INTEGER)
                        .boundTo(view -> view.getInt("combat.max-targets"))
                        .build();

        ConfigLoader loader = configLoaderOf(plugin);
        ConfigHandle<Integer> handle = loader.register(Path.of("combat.yml"), schema);
        assertThat(handle.get()).isEqualTo(5);

        Files.writeString(file, "combat:\n  max-targets: 9\n");
        assertThat(plugin.reloadConfiguration()).isTrue();
        assertThat(handle.get()).isEqualTo(9);

        // quickstart section 3, step 4: a broken reload must not crash and must keep the old value
        Files.writeString(file, "combat:\n  max-targets: 'plenty'\n");
        assertThat(plugin.reloadConfiguration()).isFalse();
        assertThat(handle.get()).isEqualTo(9);
    }

    @Test
    void disablingThePluginTerminatesCleanly() {
        server.getPluginManager().disablePlugin(plugin);

        assertThat(plugin.bootstrapState().phase()).isEqualTo(BootstrapState.Phase.SHUTTING_DOWN);
        assertThat(plugin.bootstrapState().acceptsPlayers()).isFalse();
    }

    /** The plugin keeps its loader private; the test reaches it the way B14's reload command will. */
    private static ConfigLoader configLoaderOf(RpgPlugin plugin) {
        return plugin.configLoader();
    }
}
