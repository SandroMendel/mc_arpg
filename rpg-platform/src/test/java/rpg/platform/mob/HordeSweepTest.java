package rpg.platform.mob;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
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
import rpg.core.stats.Attribute;
import rpg.core.zone.Area;
import rpg.core.zone.Cuboid;
import rpg.core.zone.LevelBand;
import rpg.core.zone.SpawnArea;
import rpg.core.zone.Zone;

/**
 * Der Einmal-Durchlauf je bevoelkerter Zone: er setzt, raeumt auf, haelt das Budget, plant sich
 * neu, und hoert erst auf, wenn eine leere Zone die Kulanzfrist ueberschritten hat (research.md
 * R4, FR-015, FR-019).
 */
class HordeSweepTest {

    private ServerMock server;
    private WorldMock world;
    private FakeMobScheduler scheduler;
    private MutableClock clock;
    private HordeRegistry registry;
    private FakeZones zones;
    private MobConfig config;
    private HordeSweep sweep;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        clock = new MutableClock(Instant.parse("2026-08-24T20:00:00Z"));
        scheduler = new FakeMobScheduler(clock);
        registry = new HordeRegistry();
        Zone zone = zoneFixture();
        zones = new FakeZones(zone);
        config = configFixture();
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
                        holderId -> false, // niemand ist im Kampf, sofern ein Test nichts anderes tut
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
    @DisplayName("verlaesst der letzte Spieler die Zone, laeuft die Schleife innerhalb der Kulanzfrist weiter (FR-019)")
    void whenTheLastPlayerLeavesTheLoopKeepsRunningWithinTheGracePeriod() {
        PlayerMock player = addPlayerInZone();
        sweep.ensureScheduledForPopulatedZones();

        player.teleport(new org.bukkit.Location(world, -5000, 70, -5000));
        scheduler.runNextDelayed(); // 100ms verstrichen, Frist ist 250ms - noch nicht abgelaufen

        assertThat(scheduler.delayedQueue)
                .as("die Frist laeuft noch - kein Grund, die Schleife zu beenden")
                .hasSize(1);
    }

    @Test
    @DisplayName("nach Ablauf der Kulanzfrist wird die Zone geraeumt und die Schleife endet (FR-019)")
    void afterTheGracePeriodTheZoneIsClearedAndTheLoopStops() {
        PlayerMock player = addPlayerInZone();
        sweep.ensureScheduledForPopulatedZones();
        assertThat(registry.countIn("greenfields"))
                .as("der erste Durchlauf hat schon etwas gesetzt")
                .isEqualTo(1);

        player.teleport(new org.bukkit.Location(world, -5000, 70, -5000));
        int rounds = 0;
        while (!scheduler.delayedQueue.isEmpty() && rounds < 20) {
            scheduler.runNextDelayed();
            rounds++;
        }

        assertThat(scheduler.delayedQueue)
                .as("kein neuer Durchlauf eingeplant - die Zone ist jetzt leer")
                .isEmpty();
        assertThat(rounds)
                .as("nicht schon beim ersten leeren Durchlauf - die Frist musste erst ablaufen")
                .isGreaterThan(1);
        assertThat(registry.countIn("greenfields"))
                .as("und alles, was noch dort stand, ist mit ihr geraeumt")
                .isZero();
    }

    @Test
    @DisplayName("kommt jemand innerhalb der Frist zurueck, faengt der Kulanzzaehler wieder von vorn an")
    void ifSomebodyReturnsWithinTheGraceTheCounterResets() {
        PlayerMock player = addPlayerInZone();
        sweep.ensureScheduledForPopulatedZones();

        player.teleport(new org.bukkit.Location(world, -5000, 70, -5000));
        scheduler.runNextDelayed(); // eine leere Runde zaehlt zu laufen an
        player.teleport(new org.bukkit.Location(world, 200, 70, 0)); // zurueck - Zaehler soll loeschen
        scheduler.runNextDelayed(); // wieder bevoelkert

        player.teleport(new org.bukkit.Location(world, -5000, 70, -5000));
        scheduler.runNextDelayed();
        scheduler.runNextDelayed();

        // Ohne Reset waeren es seit dem ERSTEN Weggang schon vier verstrichene Runden a 100ms -
        // ueber der 250ms-Frist, und die Schleife haette bereits geendet. Mit Reset zaehlen nur
        // die zwei Runden seit der Rueckkehr (200ms), und sie laeuft weiter.
        assertThat(scheduler.delayedQueue)
                .as("die Frist zaehlt seit der Rueckkehr, nicht kumulativ ueber beide Abwesenheiten")
                .hasSize(1);
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
    @DisplayName("wer ausserhalb der Reichweite jedes Spielers steht, wird auch bei Betrieb entfernt (FR-020)")
    void whoeverIsOutOfEveryPlayersRangeIsRemovedEvenWhilePopulated() {
        PlayerMock player = addPlayerInZone();
        sweep.ensureScheduledForPopulatedZones();
        assertThat(registry.countIn("greenfields")).isEqualTo(1);
        UUID original = registry.all().iterator().next().entityId();

        // Der Spieler bleibt IN der Zone, wandert aber weit weg von der Kreatur - weit ueber die
        // 96 Bloecke Aufraeumreichweite hinaus, ohne die Zone selbst zu verlassen. Der Nachschub
        // aus FR-018a fuellt die Zone im selben Durchlauf gleich wieder auf - die Zusage dieses
        // Tests ist, dass die URSPRUENGLICHE Kreatur weg ist, nicht dass die Zone leer bleibt.
        player.teleport(new org.bukkit.Location(world, 900, 70, 900));
        scheduler.runNextDelayed();

        assertThat(registry.find(original))
                .as("die eine Kreatur stand weit ausserhalb der Reichweite")
                .isNull();
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
                Duration.ofMillis(250),
                96.0,
                Duration.ofMillis(500),
                Map.of(rotling.key(), rotling),
                Map.of("greenfields", horde));
    }
}
