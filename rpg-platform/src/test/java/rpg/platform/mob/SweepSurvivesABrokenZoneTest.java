package rpg.platform.mob;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
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
import rpg.core.scheduler.EntityRef;
import rpg.core.scheduler.Scheduler;
import rpg.core.scheduler.TaskHandle;
import rpg.core.scheduler.WorldPosition;
import rpg.core.stats.Attribute;
import rpg.core.zone.Area;
import rpg.core.zone.CrystalPlacement;
import rpg.core.zone.Cuboid;
import rpg.core.zone.LevelBand;
import rpg.core.zone.SpawnArea;
import rpg.core.zone.Zone;
import rpg.core.zone.Zones;

/**
 * FR-044, Prinzip VI: eine Zone, deren Konfiguration oder Welt Aerger macht, darf die anderen
 * nicht mitreissen und den Durchlauf nicht beenden - sonst plant sie sich nie wieder ein und die
 * Horde bleibt fuer immer stehen.
 */
class SweepSurvivesABrokenZoneTest {

    private ServerMock server;
    private WorldMock world;
    private FakeScheduler scheduler;
    private HordeRegistry registry;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        scheduler = new FakeScheduler();
        registry = new HordeRegistry();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("die kaputte Zone plant trotzdem neu, und die gesunde Zone bleibt unberuehrt")
    void theBrokenZoneStillReschedulesAndTheHealthyZoneIsUnaffected() {
        Zone healthy = zone("healthy", 0, 0);
        Zone broken = zone("broken", 2000, 2000);
        FaultyZones zones = new FaultyZones("broken", healthy, broken);
        MobConfig config = configWithZones("healthy", "broken");

        server.addPlayer().teleport(new org.bukkit.Location(world, 100, 70, 0));
        server.addPlayer().teleport(new org.bukkit.Location(world, 2100, 70, 2000));

        HordeSweep sweep =
                new HordeSweep(
                        server,
                        scheduler,
                        () -> zones,
                        new FakeZonePresence(server, zones),
                        () -> config,
                        registry,
                        new java.util.HashMap<>(),
                        holderId -> false,
                        new PaperMobPlacer(Logger.getLogger("test")),
                        Clock.fixed(Instant.parse("2026-08-24T20:00:00Z"), ZoneOffset.UTC),
                        Logger.getLogger("test"));

        sweep.ensureScheduledForPopulatedZones();

        // Beide Zonen haben sich neu eingeplant - die kaputte trotz ihrer Ausnahme.
        assertThat(scheduler.delayedQueue).hasSize(2);

        // Die gesunde Zone hat gesetzt.
        assertThat(registry.countIn("healthy")).isEqualTo(1);
        // Die kaputte hat nichts gesetzt - sie ist an ihrer eigenen Ausnahme gescheitert, bevor
        // sie etwas verbuchen konnte.
        assertThat(registry.countIn("broken")).isZero();

        // Und ein weiterer Durchlauf der kaputten Zone scheitert wieder, plant aber wieder neu.
        scheduler.runAllDelayed();
        assertThat(scheduler.delayedQueue)
                .as("beide laufen weiter, keine Zone ist stehengeblieben")
                .hasSize(2);
    }

    private Zone zone(String key, int centreX, int centreZ) {
        SpawnArea area =
                new SpawnArea(
                        key + "-area",
                        Area.of(
                                Cuboid.of(
                                        centreX + 100, centreZ - 50, centreX + 300, centreZ + 50)));
        return new Zone(
                key,
                world.getUID(),
                Area.of(
                        Cuboid.of(centreX - 1000, centreZ - 1000, centreX + 1000, centreZ + 1000)),
                new LevelBand(1, 10),
                Optional.empty(),
                List.of(area),
                Optional.empty(),
                false,
                false);
    }

    private static MobConfig configWithZones(String... zoneKeys) {
        MobKind kind =
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
        for (String zoneKey : zoneKeys) {
            hordes.put(
                    zoneKey,
                    new HordeSpec(
                            zoneKey,
                            List.of(new HordeSpec.Entry(zoneKey + "-area", "probe.kind", 1)),
                            null));
        }
        return new MobConfig(
                new Budget(10, 5, 5, 5),
                Duration.ofMillis(100),
                0.0,
                Duration.ofSeconds(60),
                96.0,
                Duration.ofMillis(500),
                Map.of(kind.key(), kind),
                hordes);
    }

    /** Zwei Zonen mit echter Geometrie zum Zaehlen, und eine davon wirft bei {@link #byKey}. */
    private static final class FaultyZones implements Zones {

        private final String trapZoneKey;
        private final Map<String, Zone> geometry = new LinkedHashMap<>();

        FaultyZones(String trapZoneKey, Zone... zones) {
            this.trapZoneKey = trapZoneKey;
            for (Zone zone : zones) {
                geometry.put(zone.key(), zone);
            }
        }

        @Override
        public Optional<Zone> zoneAt(WorldPosition position) {
            for (Zone zone : geometry.values()) {
                if (zone.worldId().equals(position.worldId())
                        && zone.contains(
                                (int) Math.floor(position.x()),
                                (int) Math.floor(position.y()),
                                (int) Math.floor(position.z()))) {
                    return Optional.of(zone);
                }
            }
            return Optional.empty();
        }

        @Override
        public String zoneKeyAt(WorldPosition position) {
            return zoneAt(position).map(Zone::key).orElse(null);
        }

        @Override
        public boolean inSafeCore(WorldPosition position) {
            throw new UnsupportedOperationException("dieser Test braucht das nicht");
        }

        @Override
        public boolean isBoundaryChunk(UUID worldId, int blockX, int blockZ) {
            throw new UnsupportedOperationException("dieser Test braucht das nicht");
        }

        @Override
        public Optional<CrystalPlacement> crystalAt(WorldPosition position) {
            throw new UnsupportedOperationException("dieser Test braucht das nicht");
        }

        @Override
        public Optional<CrystalPlacement> crystalByKey(String crystalKey) {
            throw new UnsupportedOperationException("dieser Test braucht das nicht");
        }

        @Override
        public List<CrystalPlacement> crystals() {
            throw new UnsupportedOperationException("dieser Test braucht das nicht");
        }

        @Override
        public Optional<Zone> byKey(String zoneKey) {
            if (zoneKey.equals(trapZoneKey)) {
                // Simuliert eine Konfiguration oder Welt, die beim Aufloesen Aerger macht -
                // genau die Stelle, an der ein realer Fehler auftreten koennte (FR-044).
                throw new IllegalStateException("simulated trouble resolving zone " + zoneKey);
            }
            return Optional.ofNullable(geometry.get(zoneKey));
        }

        @Override
        public List<Zone> all() {
            return List.copyOf(geometry.values());
        }

        @Override
        public WorldPosition startPoint() {
            throw new UnsupportedOperationException("dieser Test braucht das nicht");
        }

        @Override
        public Optional<WorldPosition> respawnPointOf(String zoneKey) {
            throw new UnsupportedOperationException("dieser Test braucht das nicht");
        }

        @Override
        public WorldPosition fallbackPoint() {
            throw new UnsupportedOperationException("dieser Test braucht das nicht");
        }

        @Override
        public List<SpawnArea> spawnAreasOf(String zoneKey) {
            return byKey(zoneKey).map(Zone::spawnAreas).orElse(List.of());
        }
    }

    private static final class FakeScheduler implements Scheduler {

        final ArrayDeque<Runnable> delayedQueue = new ArrayDeque<>();

        void runAllDelayed() {
            List<Runnable> pending = List.copyOf(delayedQueue);
            delayedQueue.clear();
            pending.forEach(Runnable::run);
        }

        @Override
        public TaskHandle runSyncAtLocation(WorldPosition position, Runnable task) {
            task.run();
            return handle();
        }

        @Override
        public TaskHandle runSyncOnEntity(EntityRef entity, Runnable task) {
            task.run();
            return handle();
        }

        @Override
        public TaskHandle runSyncOnEntityDelayed(EntityRef entity, Duration delay, Runnable task) {
            throw new UnsupportedOperationException("HordeSweep benutzt das nicht");
        }

        @Override
        public TaskHandle runAsync(Runnable task) {
            task.run();
            return handle();
        }

        @Override
        public TaskHandle runAsyncDelayed(Duration delay, Runnable task) {
            delayedQueue.add(task);
            return handle();
        }

        private static TaskHandle handle() {
            return new TaskHandle() {
                @Override
                public void cancel() {}

                @Override
                public boolean isCancelled() {
                    return false;
                }
            };
        }
    }
}
