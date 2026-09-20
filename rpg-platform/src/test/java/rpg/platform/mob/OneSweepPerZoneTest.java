package rpg.platform.mob;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
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
 * FR-024, FR-015, Prinzip II: bei 130 Kreaturen in drei bevoelkerten Zonen sind drei Aufgaben
 * eingeplant, nicht 133 - und in einer leeren Zone keine.
 *
 * <p>Nach dem Muster von B08s SC-005 - die Zusage "keine wiederkehrende Aufgabe je Entitaet" ist
 * nur so viel wert, wie sie gezaehlt wird.
 */
class OneSweepPerZoneTest {

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
        config = configWithFourZones();
        FakeZones zones =
                new FakeZones(
                        zone("zone-a", 0),
                        zone("zone-b", 2000),
                        zone("zone-c", 4000),
                        zone("zone-d", 6000));
        PaperMobPlacer placer = new PaperMobPlacer(Logger.getLogger("test"));
        sweep =
                new HordeSweep(
                        server,
                        scheduler,
                        () -> zones,
                        new FakeZonePresence(server, zones),
                        () -> config,
                        registry,
                        new java.util.HashMap<>(),
                        holderId -> false,
                        placer,
                        Clock.fixed(Instant.parse("2026-08-24T20:00:00Z"), ZoneOffset.UTC),
                        Logger.getLogger("test"));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("130 Kreaturen in drei bevoelkerten Zonen bedeuten drei Aufgaben, nicht 133")
    void oneHundredThirtyCreaturesInThreePopulatedZonesMeanThreeTasksNotOneThirtyThree() {
        // Ein grosser vorbestehender Bestand aendert nichts an der Aufgabenzahl - sie folgt der
        // Zahl der bevoelkerten ZONEN, nie der Zahl der Kreaturen.
        for (int i = 0; i < 130; i++) {
            registry.add(
                    new HordeRegistry.Entry(
                            UUID.randomUUID(),
                            "probe.kind",
                            "zone-a",
                            rpg.core.mob.NearbyChunks.pack(i, 0),
                            Instant.now(),
                            HordeRegistry.Origin.BUDGET));
        }
        addPlayerIn("zone-a", 100, 0);
        addPlayerIn("zone-b", 2100, 0);
        addPlayerIn("zone-c", 4100, 0);
        // zone-d bleibt leer - kein Spieler dort.

        sweep.ensureScheduledForPopulatedZones();

        assertThat(scheduler.asyncKickoffs)
                .as("drei Kickoffs - eine je bevoelkerter Zone, nicht eine je Kreatur")
                .isEqualTo(3);
        assertThat(scheduler.delayedQueue).as("und drei eingeplante Folgedurchlaeufe").hasSize(3);
    }

    @Test
    @DisplayName("eine leere Zone bekommt keine Aufgabe eingeplant")
    void anEmptyZoneGetsNoTaskAtAll() {
        addPlayerIn("zone-a", 100, 0);
        // zone-b, zone-c, zone-d bleiben leer.

        sweep.ensureScheduledForPopulatedZones();

        assertThat(scheduler.asyncKickoffs).isEqualTo(1);
        assertThat(scheduler.delayedQueue).hasSize(1);
    }

    private void addPlayerIn(String zoneKey, double x, double z) {
        var player = server.addPlayer();
        player.teleport(new org.bukkit.Location(world, x, 70, z));
    }

    private Zone zone(String key, int centreX) {
        SpawnArea area =
                new SpawnArea(key + "-area", Area.of(Cuboid.of(centreX + 50, -50, centreX + 150, 50)));
        return new Zone(
                key,
                world.getUID(),
                Area.of(Cuboid.of(centreX - 500, -500, centreX + 500, 500)),
                new LevelBand(1, 10),
                Optional.empty(),
                List.of(area),
                Optional.empty(),
                false,
                false);
    }

    private MobConfig configWithFourZones() {
        MobKind probe =
                new MobKind(
                        "probe.kind",
                        "ZOMBIE",
                        3,
                        Map.of(Attribute.HEALTH, 40.0),
                        24.0,
                        MessageKey.of("mob.probe.kind.name"),
                        12L,
                        4L,
                        false);
        Map<String, HordeSpec> hordes = new LinkedHashMap<>();
        for (String zoneKey : List.of("zone-a", "zone-b", "zone-c", "zone-d")) {
            hordes.put(
                    zoneKey,
                    new HordeSpec(
                            zoneKey,
                            List.of(new HordeSpec.Entry(zoneKey + "-area", "probe.kind", 1)),
                            null));
        }
        return new MobConfig(
                new Budget(800, 130, 12, 25),
                Duration.ofMillis(2000),
                0.0,
                Duration.ofSeconds(60),
                96.0,
                Duration.ofMillis(500),
                Map.of(probe.key(), probe),
                hordes);
    }
}
