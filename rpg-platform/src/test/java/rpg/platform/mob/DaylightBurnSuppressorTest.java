package rpg.platform.mob;

import static org.assertj.core.api.Assertions.assertThat;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.entity.EntityCombustByBlockEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

/**
 * Gefunden auf dem echten Server bei T112: der Boss verbrannte in der Sonne, bevor ein Spieler ihn
 * erreichen konnte - derselbe Mechanismus fraess unbemerkt an der ganzen Horde.
 */
class DaylightBurnSuppressorTest {

    private ServerMock server;
    private WorldMock world;
    private DaylightBurnSuppressor suppressor;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        suppressor = new DaylightBurnSuppressor();
        server.getPluginManager()
                .registerEvents(suppressor, MockBukkit.createMockPlugin("DaylightBurnProbe"));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("eine eigene Kreatur wird nicht von der Sonne entzuendet")
    void ownCreatureIsNotIgnitedBySunlight() {
        Entity zombie = world.spawnEntity(new Location(world, 0, 70, 0), EntityType.ZOMBIE);
        MobKindTag.mark(zombie, "greenfields.rotling", "greenfields");

        var event = new EntityCombustEvent(zombie, 8);
        server.getPluginManager().callEvent(event);

        assertThat(event.isCancelled()).isTrue();
    }

    @Test
    @DisplayName("eine Vanilla-Kreatur ohne Vermerk bleibt von der Sonne entzuendbar")
    void aVanillaCreatureWithoutTheTagStillBurns() {
        Entity zombie = world.spawnEntity(new Location(world, 0, 70, 0), EntityType.ZOMBIE);

        var event = new EntityCombustEvent(zombie, 8);
        server.getPluginManager().callEvent(event);

        assertThat(event.isCancelled()).isFalse();
    }

    @Test
    @DisplayName(
            "Lava-Entzuendung einer eigenen Kreatur wird ebenfalls unterdrueckt - dieselbe"
                    + " Handler-Liste wie die Sonne, der eigentliche Schaden laeuft ueber B05")
    void lavaIgnitionOfAnOwnCreatureIsAlsoSuppressed() {
        Entity zombie = world.spawnEntity(new Location(world, 0, 70, 0), EntityType.ZOMBIE);
        MobKindTag.mark(zombie, "greenfields.rotling", "greenfields");

        var event = new EntityCombustByBlockEvent(null, zombie, 8);
        server.getPluginManager().callEvent(event);

        assertThat(event.isCancelled()).isTrue();
    }
}
