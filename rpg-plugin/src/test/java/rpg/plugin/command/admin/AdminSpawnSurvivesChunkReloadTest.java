package rpg.plugin;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import rpg.core.mob.HordeRegistry;
import rpg.platform.mob.MobKindTag;

/** T079 — ADR-050 keeps a live admin spawn because the shared registry still knows it. */
class AdminSpawnSurvivesChunkReloadTest {

    private ServerMock server;
    private WorldMock world;
    private RpgPlugin plugin;
    private PlayerMock player;

    @BeforeEach
    void setUp() throws Exception {
        rpg.persistence.support.PostgresContainer.resetSchema();
        server = MockBukkit.mock();
        TestServerSetup.useTestDatabase();
        world = server.addSimpleWorld("world");
        plugin = MockBukkit.load(RpgPlugin.class);
        player = server.addPlayer("Ticoo");
        player.teleport(new Location(world, 32.5, 65, 48.5));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("eine registrierte Admin-Kreatur wird beim Chunk-Laden nicht als Waisen entfernt")
    void aRegisteredAdminSpawnSurvivesEntityLoad() throws Exception {
        HordeRegistry registry = mobRegistry(plugin);
        MobKindTag.mark(player, "greenfields.rotling", "greenfields");
        registry.add(
                new HordeRegistry.Entry(
                        player.getUniqueId(),
                        "greenfields.rotling",
                        "greenfields",
                        rpg.core.mob.NearbyChunks.packBlock(32, 48),
                        Instant.now(),
                        HordeRegistry.Origin.ADMIN));

        server.getPluginManager()
                .callEvent(new EntitiesLoadEvent(player.getLocation().getChunk(), List.of(player)));

        assertThat(player.isValid()).isTrue();
        assertThat(registry.holds(player.getUniqueId())).isTrue();
        assertThat(registry.countAdmin()).isEqualTo(1);
    }

    private static HordeRegistry mobRegistry(RpgPlugin plugin) throws Exception {
        Field field = RpgPlugin.class.getDeclaredField("mobModule");
        field.setAccessible(true);
        return ((rpg.core.mob.MobModule) field.get(plugin)).registry();
    }
}
