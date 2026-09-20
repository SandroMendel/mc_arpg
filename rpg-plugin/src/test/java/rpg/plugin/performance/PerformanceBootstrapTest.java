package rpg.plugin;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Set;

import org.bukkit.event.HandlerList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import com.destroystokyo.paper.event.server.ServerTickEndEvent;
import com.destroystokyo.paper.event.server.ServerTickStartEvent;

import rpg.core.performance.PerformanceRegistry;
import rpg.core.performance.SubsystemId;
import rpg.persistence.support.PostgresContainer;

/** T019: the B15 registry and Paper listener are present in the real plugin bootstrap. */
class PerformanceBootstrapTest {

    private ServerMock server;
    private RpgPlugin plugin;

    @BeforeEach
    void setUp() throws Exception {
        PostgresContainer.resetSchema();
        server = MockBukkit.mock();
        server.addSimpleWorld("world");
        TestServerSetup.useTestDatabase();
        plugin = MockBukkit.load(RpgPlugin.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void publishesTheRegistryAndRegistersBothPaperTickEvents() {
        assertThat(plugin.registry().findService(PerformanceRegistry.class)).isPresent();
        assertThat(plugin.performanceRegistry()).isNotNull();
        assertThat(handlerCount(ServerTickStartEvent.getHandlerList())).isEqualTo(1);
        assertThat(handlerCount(ServerTickEndEvent.getHandlerList())).isEqualTo(1);
    }

    @Test
    void shipsValidatedConfigurationAndRegistersEveryExpectedSource() {
        assertThat(plugin.getDataFolder().toPath().resolve("performance.yml")).exists();
        assertThat(plugin.performanceConfig()).isNotNull();
        assertThat(plugin.performanceConfig().windowSamples()).isEqualTo(256);
        assertThat(plugin.performanceRegistry().snapshot().subsystems().keySet())
                .containsExactlyInAnyOrderElementsOf(
                        Set.of(
                                new SubsystemId("b03-session-load"),
                                new SubsystemId("b05-combat"),
                                new SubsystemId("b08b-coin-drops"),
                                new SubsystemId("b09-zone-movement"),
                                new SubsystemId("b10-hordes"),
                                new SubsystemId("b12-statistics"),
                                new SubsystemId("b13-hud"),
                                new SubsystemId("b14-admin")));
    }

    @Test
    void cleanShutdownRemovesTheListenerAndService() {
        server.getPluginManager().disablePlugin(plugin);

        assertThat(handlerCount(ServerTickStartEvent.getHandlerList())).isZero();
        assertThat(handlerCount(ServerTickEndEvent.getHandlerList())).isZero();
        assertThat(plugin.registry().findService(PerformanceRegistry.class)).isEmpty();
    }

    private static int handlerCount(HandlerList handlers) {
        return (int)
                Arrays.stream(handlers.getRegisteredListeners())
                        .filter(listener -> listener.getPlugin() instanceof RpgPlugin)
                        .count();
    }
}
