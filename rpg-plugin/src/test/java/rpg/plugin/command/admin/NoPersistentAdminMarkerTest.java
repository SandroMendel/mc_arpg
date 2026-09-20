package rpg.plugin;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
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

/** T080 — origin is registry-only; after a restart the orphan rule removes the tagged entity. */
class NoPersistentAdminMarkerTest {

    private ServerMock server;
    private WorldMock world;
    private RpgPlugin plugin;
    private Entity mob;

    @BeforeEach
    void setUp() throws Exception {
        rpg.persistence.support.PostgresContainer.resetSchema();
        server = MockBukkit.mock();
        TestServerSetup.useTestDatabase();
        world = server.addSimpleWorld("world");
        plugin = MockBukkit.load(RpgPlugin.class);
        mob = world.spawnEntity(new Location(world, 32.5, 65, 48.5), EntityType.ZOMBIE);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("kein Herkunftsmarker wird persistiert und nach Registry-Verlust greift ADR-050")
    void originIsNotPersistedAndOrphanIsRemoved() throws Exception {
        HordeRegistry registry = mobRegistry(plugin);
        MobKindTag.mark(mob, "greenfields.rotling", "greenfields");
        registry.add(
                new HordeRegistry.Entry(
                        mob.getUniqueId(),
                        "greenfields.rotling",
                        "greenfields",
                        rpg.core.mob.NearbyChunks.packBlock(32, 48),
                        Instant.now(),
                        HordeRegistry.Origin.ADMIN));

        assertThat(mob.getPersistentDataContainer().getKeys())
                .extracting(NamespacedKey::toString)
                .containsExactlyInAnyOrder("rpg:mob_kind", "rpg:mob_zone")
                .doesNotContain("rpg:mob_origin");

        // MobModule.stop() is the restart boundary: the in-memory registry is deliberately not
        // restored, so the next chunk load must treat the tagged entity as an orphan.
        registry.clear();
        server.getPluginManager()
                .callEvent(new EntitiesLoadEvent(mob.getLocation().getChunk(), List.of(mob)));

        assertThat(registry.total()).isZero();
        assertThat(registry.countAdmin()).isZero();
        assertThat(mob.isValid()).isFalse();
    }

    private static HordeRegistry mobRegistry(RpgPlugin plugin) throws Exception {
        Field field = RpgPlugin.class.getDeclaredField("mobModule");
        field.setAccessible(true);
        return ((rpg.core.mob.MobModule) field.get(plugin)).registry();
    }
}
