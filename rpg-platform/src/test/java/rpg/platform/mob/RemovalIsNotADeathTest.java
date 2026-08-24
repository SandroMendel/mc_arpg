package rpg.platform.mob;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

/**
 * FR-021: ein Entfernen loest weder Erfahrung noch Coins noch ein Todesereignis aus.
 *
 * <p>Das ist die Stelle, an der dieser Block etwas verschenken koennte: raeumt er ueber Schaden
 * statt ueber {@link org.bukkit.entity.Entity#remove()} auf, wuerde B06 Erfahrung und B08b Coins
 * fuer eine Kreatur ausschuetten, die niemand getoetet hat.
 */
class RemovalIsNotADeathTest {

    private ServerMock server;
    private WorldMock world;
    private PaperMobPlacer placer;
    private Plugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        plugin = MockBukkit.createMockPlugin("RemovalProbe");
        placer = new PaperMobPlacer(Logger.getLogger("test"));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("entfernen loest KEIN EntityDeathEvent aus")
    void removingFiresNoEntityDeathEvent() {
        LivingEntity zombie =
                (LivingEntity) world.spawnEntity(world.getSpawnLocation(), EntityType.ZOMBIE);
        AtomicInteger deaths = new AtomicInteger();
        server.getPluginManager()
                .registerEvents(
                        new Listener() {
                            @EventHandler
                            public void onDeath(EntityDeathEvent event) {
                                deaths.incrementAndGet();
                            }
                        },
                        plugin);

        placer.remove(zombie);

        assertThat(deaths.get()).as("kein Todesereignis fuer ein Aufraeumen").isZero();
    }

    @Test
    @DisplayName("entfernen macht die Entitaet ungueltig, so wie ein normales Despawn")
    void removingInvalidatesTheEntity() {
        LivingEntity zombie =
                (LivingEntity) world.spawnEntity(world.getSpawnLocation(), EntityType.ZOMBIE);

        placer.remove(zombie);

        assertThat(zombie.isValid()).isFalse();
    }

    @Test
    @DisplayName("eine bereits entfernte Entitaet ist kein Fehler")
    void anAlreadyRemovedEntityIsNotAnError() {
        LivingEntity zombie =
                (LivingEntity) world.spawnEntity(world.getSpawnLocation(), EntityType.ZOMBIE);
        placer.remove(zombie);

        org.assertj.core.api.Assertions.assertThatCode(() -> placer.remove(zombie))
                .doesNotThrowAnyException();
    }
}
