package rpg.platform.mob;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import rpg.core.message.MessageKey;
import rpg.core.mob.Budget;
import rpg.core.mob.HordeRegistry;
import rpg.core.mob.HordeSpec;
import rpg.core.mob.MobConfig;
import rpg.core.mob.MobKind;
import rpg.core.stats.Attribute;
import rpg.core.zone.Area;
import rpg.core.zone.Cuboid;
import rpg.core.zone.LevelBand;
import rpg.core.zone.SpawnArea;
import rpg.core.zone.Zone;

/**
 * FR-023: nach dem Abschalten haelt der Bestand nichts mehr - sonst stuende beim naechsten Start
 * eine Zaehlung ohne Kreaturen, oder schlimmer: eine Kreatur, die keinem Durchlauf mehr gehoert.
 */
class ShutdownLeavesNothingTest {

    private ServerMock server;
    private WorldMock world;
    private FakeMobScheduler scheduler;
    private HordeRegistry registry;
    private MobConfig config;
    private HordeSweep sweep;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        scheduler = new FakeMobScheduler();
        registry = new HordeRegistry();
        FakeZones zones = new FakeZones(zoneFixture());
        config = configFixture();
        PaperMobPlacer placer = new PaperMobPlacer(Logger.getLogger("test"));
        sweep =
                new HordeSweep(
                        server,
                        scheduler,
                        () -> zones,
                        () -> config,
                        registry,
                        holderId -> false,
                        placer,
                        Clock.fixed(Instant.parse("2026-08-24T20:00:00Z"), java.time.ZoneOffset.UTC),
                        Logger.getLogger("test"));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("shutdown entfernt jede gesetzte Kreatur und leert den Bestand")
    void shutdownRemovesEveryPlacedCreatureAndClearsTheRegistry() {
        PlayerMock player = server.addPlayer();
        player.teleport(new org.bukkit.Location(world, 200, 70, 0));
        sweep.ensureScheduledForPopulatedZones();
        assertThat(registry.total()).as("etwas wurde gesetzt, bevor wir abschalten") .isEqualTo(1);
        var placedId = registry.all().iterator().next().entityId();

        sweep.shutdown();

        assertThat(registry.total()).isZero();
        assertThat(server.getEntity(placedId)).as("die Entitaet ist wirklich aus der Welt weg").isNull();
    }

    @Test
    @DisplayName("shutdown auf einem leeren Bestand ist kein Fehler")
    void shutdownOnAnEmptyRegistryIsNotAnError() {
        org.assertj.core.api.Assertions.assertThatCode(() -> sweep.shutdown())
                .doesNotThrowAnyException();
        assertThat(registry.total()).isZero();
    }

    private Zone zoneFixture() {
        SpawnArea east = new SpawnArea("greenfields-east", Area.of(Cuboid.of(100, -50, 300, 50)));
        return new Zone(
                "greenfields",
                world.getUID(),
                Area.of(Cuboid.of(-1000, -1000, 1000, 1000)),
                new LevelBand(1, 10),
                Optional.empty(),
                List.of(east),
                Optional.empty(),
                false,
                false);
    }

    private MobConfig configFixture() {
        MobKind rotling =
                new MobKind(
                        "greenfields.rotling",
                        "ZOMBIE",
                        3,
                        Map.of(Attribute.HEALTH, 40.0),
                        24.0,
                        MessageKey.of("mob.greenfields.rotling.name"),
                        12L,
                        4L,
                        false);
        HordeSpec horde =
                new HordeSpec(
                        "greenfields",
                        List.of(new HordeSpec.Entry("greenfields-east", "greenfields.rotling", 1)),
                        null);
        return new MobConfig(
                new Budget(10, 5, 5, 5),
                Duration.ofMillis(100),
                0.0,
                Duration.ofSeconds(60),
                96.0,
                Duration.ofMillis(500),
                Map.of(rotling.key(), rotling),
                Map.of("greenfields", horde));
    }
}
