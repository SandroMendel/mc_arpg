package rpg.platform.mob;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
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
import org.mockbukkit.mockbukkit.entity.PlayerMock;
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
 * Der Einmal-Durchlauf je bevoelkerter Zone: er setzt, haelt das Budget, plant sich neu, und hoert
 * auf, sobald niemand mehr da ist oder das Plugin abschaltet (research.md R4, FR-015).
 */
class HordeSweepTest {

    private ServerMock server;
    private WorldMock world;
    private FakeScheduler scheduler;
    private HordeRegistry registry;
    private FakeZones zones;
    private MobConfig config;
    private HordeSweep sweep;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        scheduler = new FakeScheduler();
        registry = new HordeRegistry();
        Zone zone = zoneFixture();
        zones = new FakeZones(zone);
        config = configFixture();
        PaperMobPlacer placer = new PaperMobPlacer(Logger.getLogger("test"));
        Clock clock = Clock.fixed(Instant.parse("2026-08-24T20:00:00Z"), ZoneOffset.UTC);
        sweep =
                new HordeSweep(
                        server,
                        scheduler,
                        () -> zones,
                        () -> config,
                        registry,
                        placer,
                        clock,
                        Logger.getLogger("test"));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("eine Zone ohne Spieler bekommt gar keinen Durchlauf eingeplant (FR-015)")
    void aZoneWithoutPlayersGetsNoSweepAtAll() {
        sweep.ensureScheduledForPopulatedZones();

        assertThat(scheduler.asyncKickoffs).isZero();
        assertThat(registry.total()).isZero();
    }

    @Test
    @DisplayName("ein Spieler in der Zone loest einen Durchlauf aus, der plant sich selbst neu")
    void aPlayerInTheZoneTriggersASweepThatReschedulesItself() {
        addPlayerInZone();

        sweep.ensureScheduledForPopulatedZones();

        assertThat(scheduler.asyncKickoffs).as("der erste Durchlauf lief sofort").isEqualTo(1);
        assertThat(scheduler.delayedQueue)
                .as("und hat sich fuer den naechsten Durchlauf neu eingeplant")
                .hasSize(1);
        assertThat(scheduler.delays).containsExactly(config.respawnInterval());
    }

    @Test
    @DisplayName("solange Spieler da bleiben, laeuft die Schleife weiter")
    void theLoopKeepsRunningWhilePlayersStay() {
        addPlayerInZone();
        sweep.ensureScheduledForPopulatedZones();

        scheduler.runNextDelayed();
        scheduler.runNextDelayed();
        scheduler.runNextDelayed();

        assertThat(scheduler.delayedQueue).hasSize(1);
    }

    @Test
    @DisplayName("verlaesst der letzte Spieler die Zone, endet die Schleife statt sich neu einzuplanen")
    void whenTheLastPlayerLeavesTheLoopStopsInsteadOfRescheduling() {
        PlayerMock player = addPlayerInZone();
        sweep.ensureScheduledForPopulatedZones();
        assertThat(scheduler.delayedQueue).hasSize(1);

        player.teleport(new org.bukkit.Location(world, -5000, 70, -5000));
        scheduler.runNextDelayed();

        assertThat(scheduler.delayedQueue)
                .as("kein neuer Durchlauf eingeplant - die Zone ist jetzt leer")
                .isEmpty();
    }

    @Test
    @DisplayName("die Budgetgrenze haelt: kein Durchlauf setzt mehr, als die Zone darf")
    void theZoneCeilingHoldsAcrossManySweeps() {
        addPlayerInZone();
        sweep.ensureScheduledForPopulatedZones();

        for (int i = 0; i < 50; i++) {
            scheduler.runNextDelayed();
            assertThat(registry.countIn("greenfields")).isLessThanOrEqualTo(config.budget().perZone());
        }
    }

    @Test
    @DisplayName("EntityRemoveEvent haelt den Bestand ehrlich - eine unbekannte Entitaet ist kein Fehler")
    void entityRemoveEventKeepsTheRegistryHonest() {
        var entity = world.spawnEntity(world.getSpawnLocation(), org.bukkit.entity.EntityType.ZOMBIE);
        var event =
                new org.bukkit.event.entity.EntityRemoveEvent(
                        entity, org.bukkit.event.entity.EntityRemoveEvent.Cause.DEATH);

        org.assertj.core.api.Assertions.assertThatCode(() -> sweep.onEntityRemove(event))
                .doesNotThrowAnyException();
    }

    private PlayerMock addPlayerInZone() {
        PlayerMock player = server.addPlayer();
        player.teleport(new org.bukkit.Location(world, 200, 70, 0));
        return player;
    }

    private Zone zoneFixture() {
        SpawnArea east =
                new SpawnArea("greenfields-east", Area.of(Cuboid.of(100, -50, 300, 50)));
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

    /** Zonen ohne echte Geometrieindex-Kosten - nur was dieser Test braucht, sonst wirft sie. */
    private static final class FakeZones implements Zones {

        private final Map<String, Zone> byKey = new LinkedHashMap<>();

        FakeZones(Zone... zones) {
            for (Zone zone : zones) {
                byKey.put(zone.key(), zone);
            }
        }

        @Override
        public Optional<Zone> zoneAt(WorldPosition position) {
            for (Zone zone : byKey.values()) {
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
            return Optional.ofNullable(byKey.get(zoneKey));
        }

        @Override
        public List<Zone> all() {
            return List.copyOf(byKey.values());
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

    /**
     * Sofortige und synchrone Aufgaben laufen sofort, wie in Wirklichkeit im selben Tick. Verzoegerte
     * asynchrone Aufgaben - die einzige Sorte, mit der diese Klasse sich selbst neu einplant - werden
     * aufgehoben, damit ein Test die Schleife Schritt fuer Schritt antreiben kann, statt in eine
     * Endlosrekursion zu laufen.
     */
    private static final class FakeScheduler implements Scheduler {

        int asyncKickoffs;
        int syncPlacements;
        final Deque<Runnable> delayedQueue = new ArrayDeque<>();
        final List<Duration> delays = new ArrayList<>();

        void runNextDelayed() {
            Runnable next = delayedQueue.poll();
            if (next != null) {
                next.run();
            }
        }

        @Override
        public TaskHandle runSyncAtLocation(WorldPosition position, Runnable task) {
            syncPlacements++;
            task.run();
            return handle();
        }

        @Override
        public TaskHandle runSyncOnEntity(EntityRef entity, Runnable task) {
            throw new UnsupportedOperationException("HordeSweep benutzt das nicht");
        }

        @Override
        public TaskHandle runSyncOnEntityDelayed(EntityRef entity, Duration delay, Runnable task) {
            throw new UnsupportedOperationException("HordeSweep benutzt das nicht");
        }

        @Override
        public TaskHandle runAsync(Runnable task) {
            asyncKickoffs++;
            task.run();
            return handle();
        }

        @Override
        public TaskHandle runAsyncDelayed(Duration delay, Runnable task) {
            delays.add(delay);
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
